package se.metricspace.opendata

import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale
import kotlin.math.roundToInt

class FlightService(private val client: HttpClient) {
    private fun Double.toCompassDirection(): String {
        val directions = arrayOf("N", "NO", "O", "SO", "S", "SV", "V", "NV")
        // Delar 360 grader på våra 8 väderstreck (45 grader per snitt)
        val index = ((this % 360) / 45.0).roundToInt()
        return directions[index % 8]
    }

    data class Flight(
        val icao24: String,
        val callsign: String,
        val country: String,
        val altitudeMeters: Double,
        val velocityKmh: Double,
        val trueTrackDegrees: Double,
        val compassDirection: String
    )

    fun getFlightsOver(lat: Double, lon: Double): List<Flight> {
        try {
            // 1. Skapa en "Bounding Box" runt platsen.
            // 0.5 grader latitud är ca 55 km norr/söder.
            // 1.0 grad longitud är ca 55 km öster/väster på den här breddgraden.
            val lamin = String.format(Locale.US, "%.4f", lat - 0.5)
            val lamax = String.format(Locale.US, "%.4f", lat + 0.5)
            val lomin = String.format(Locale.US, "%.4f", lon - 1.0)
            val lomax = String.format(Locale.US, "%.4f", lon + 1.0)

            val url = "https://opensky-network.org/api/states/all?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                // 2. Läs in JSON-trädet utan förutbestämda klasser
                val root = Json.parseToJsonElement(response.body()).jsonObject

                // Om "states" är null betyder det att inga plan är i luften precis där just nu
                val statesArray = root["states"]?.jsonArray ?: return emptyList()

                val flights = mutableListOf<Flight>()

                // 3. Loopa igenom varje flygplan (som är en array av värden)
                for (state in statesArray) {
                    val data = state.jsonArray

                    // Plocka ut värden baserat på index (enligt OpenSkys dokumentation)
                    // contentOrNull/doubleOrNull gör att vi inte kraschar om värdet saknas
                    val icao24 = data[0].jsonPrimitive.contentOrNull?.trim() ?: "Okänd"
                    val callsign = data[1].jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Okänd"
                    val country = data[2].jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Okänt"
                    val altitude = data[7].jsonPrimitive.doubleOrNull ?: 0.0
                    val velocityMs = data[9].jsonPrimitive.doubleOrNull ?: 0.0

                    // Gör om m/s till km/h för att det är mer logiskt för oss människor
                    val velocityKmh = velocityMs * 3.6
                    val trueTrack = data[10].jsonPrimitive.doubleOrNull ?: 0.0

                    // Filtrera bort plan som står på marken (höjd = 0)
                    if (altitude > 0) {
                        flights.add(Flight(icao24, callsign, country, altitude, velocityKmh, trueTrack, trueTrack.toCompassDirection()))
                    }
                }
                return flights
            } else {
                println("OpenSky svarade med felkod: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Fel vid hämtning av flygplan: ${e.message}")
        }
        return emptyList()
    }
}