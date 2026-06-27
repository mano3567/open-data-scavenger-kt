package se.metricspace.opendata

import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale
import kotlin.math.roundToInt

class FlightService(private val client: HttpClient, private val userAgent: String) {
    private fun Double.toCompassDirection(): String {
        val directions = arrayOf("N", "NO", "O", "SO", "S", "SV", "V", "NV")
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
        val compassDirection: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    fun getFlightsOver(lat: Double, lon: Double): List<Flight> {
        try {
            val lamin = String.format(Locale.US, "%.4f", lat - 0.51)
            val lamax = String.format(Locale.US, "%.4f", lat + 0.51)
            val lomin = String.format(Locale.US, "%.4f", lon - 1.02)
            val lomax = String.format(Locale.US, "%.4f", lon + 1.02)

            val url = "https://opensky-network.org/api/states/all?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val root = Json.parseToJsonElement(response.body()).jsonObject

                val statesArray = root["states"]?.jsonArray ?: return emptyList()

                val flights = mutableListOf<Flight>()

                for (state in statesArray) {
                    val data = state.jsonArray

                    val icao24 = data[0].jsonPrimitive.contentOrNull?.trim() ?: "Okänd"
                    val callsign = data[1].jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Okänd"
                    val country = data[2].jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "Okänt"
                    val longitude = data[5].jsonPrimitive.doubleOrNull ?: 0.0
                    val latitude = data[6].jsonPrimitive.doubleOrNull ?: 0.0
                    val altitude = data[7].jsonPrimitive.doubleOrNull ?: 0.0
                    val velocityMs = data[9].jsonPrimitive.doubleOrNull ?: 0.0

                    val velocityKmh = velocityMs * 3.6
                    val trueTrack = data[10].jsonPrimitive.doubleOrNull ?: 0.0

                    if (altitude > 0) {
                        flights.add(Flight(icao24, callsign, country, altitude, velocityKmh, trueTrack, trueTrack.toCompassDirection(), latitude, longitude))
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