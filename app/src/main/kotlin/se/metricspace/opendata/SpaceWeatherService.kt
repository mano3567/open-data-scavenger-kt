package se.metricspace.opendata

import kotlin.math.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.math.roundToInt

data class KpIndexStatus(
    val description: String,   // e.g., "Quiet", "Active", "Geomagnetic Storm"
    val exactKp: Double,       // Exakt decimalvärde från NOAA (t.ex. 2.67)
    val kpIndex: Int,          // Avrundat Kp (0 till 9) för enkla gränsvärden
    val timestamp: Instant
)

/**
 * Level 2: Detailed, real-time space weather telemetry from the L1 point.
 */
data class SolarWindTelemetry(
    val bz: Double,            // IMF Z-component in nanoTesla (nT)
    val bt: Double,            // Total magnetic field strength (nT)
    val speedKms: Double,      // Solar wind velocity in km/s
    val density: Double,       // Proton density in p/cm³
    val timestamp: Instant
)

private const val POLE_LAT = 80.5 * PI / 180.0
private const val POLE_LON = -72.5 * PI / 180.0


fun Location.getGeomagneticLatitude(): Double {
    val latRad = this.latitude * PI / 180.0
    val lonRad = this.longitude * PI / 180.0

    val sinGeomagneticLat = sin(latRad) * sin(POLE_LAT) +
            cos(latRad) * cos(POLE_LAT) * cos(lonRad - POLE_LON)

    return asin(sinGeomagneticLat) * 180.0 / PI
}

fun Location.getRequiredKp(): Int {
    val geoMagLat = this.getGeomagneticLatitude()

    // Empirisk formel för att uppskatta nödvändigt Kp-index baserat på geomagnetisk latitud
    return when {
        geoMagLat >= 63.0 -> 1
        geoMagLat >= 60.0 -> 2
        geoMagLat >= 58.0 -> 3
        geoMagLat >= 56.0 -> 4
        geoMagLat >= 54.0 -> 5
        geoMagLat >= 52.0 -> 6
        else -> 7
    }
}

class SpaceWeatherService(private val client: HttpClient, private val userAgent: String) {
    private val noaaScalesUrl = "https://services.swpc.noaa.gov/products/noaa-scales.json"
    private val magUrl = "https://services.swpc.noaa.gov/products/solar-wind/mag-2-hour.json"
    private val plasmaUrl = "https://services.swpc.noaa.gov/products/solar-wind/plasma-2-hour.json"
    private val kpUrl = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"

    fun fetchCurrentKpIndex(): KpIndexStatus? {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(kpUrl))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                // NOAA returnerar en matris där rad 0 är headers: ["time_tag", "kp", "observed", "noaa_scale"]
                println(response.body())
                val rows = Json.parseToJsonElement(response.body()).jsonArray

                // Plocka sista raden (den allra senaste mätningen/prognosen)
                val latestRow = rows.last().jsonArray

                // Exempel på data i latestRow: ["2026-06-28 07:45:00", "2.33", "observed", "0"]
                val timeString = latestRow[0].jsonPrimitive.content
                val exactKp = latestRow[1].jsonPrimitive.doubleOrNull ?: 0.0

                // --- Instant Trick ---
                // NOAA skickar tid som "2026-06-28 07:45:00" (UTC).
                // Instant.parse() kräver ISO-8601 ("2026-06-28T07:45:00Z").
                // Vi byter ut mellanslaget och lägger till ett Z (Zulu/UTC) på slutet.
                val isoTime = timeString.replace(" ", "T") + "Z"
                val timestamp = Instant.parse(isoTime)

                return KpIndexStatus(
                    description = "tihi",
                    exactKp = exactKp,
                    kpIndex = exactKp.roundToInt(),
                    timestamp = timestamp
                )
            } else {
                println("NOAA svarade med ett fel: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Fel vid hämtning av Kp-index: ${e.message}")
        }
        return null
    }
    /**
     * Level 2: Fetches detailed solar wind magnetic and plasma telemetry from L1.
     * Ideal for precise calculations, time-delay forecasting, and aurora hunting.
     */
    fun fetchDetailedTelemetry(): SolarWindTelemetry? {
        try {
            // TODO: Execute parallel or sequential HTTP requests to magUrl and plasmaUrl
            // TODO: Extract the last row of data from both JSON matrices
            // TODO: Correlate them by timestamp and return the combined telemetry
            return SolarWindTelemetry(
                bz = -5.4,
                bt = 6.2,
                speedKms = 480.0,
                density = 8.5,
                timestamp = Instant.now()
            )
        } catch (e: Exception) {
            println("Failed to fetch detailed solar wind telemetry: ${e.message}")
            return null
        }
    }
}