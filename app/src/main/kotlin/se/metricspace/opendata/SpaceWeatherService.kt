package se.metricspace.opendata


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlin.math.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

@Serializable
data class KpIndexStatus(
    @SerialName("Kp")
    val kpIndex: Double,

    @SerialName("a_running")
    val runningAmplitude: Int,

    @SerialName("station_count")
    val stationCount: Int,

    @SerialName("time_tag")
    val timeTagString: String
) {
    /**
     * Lazily parses the NOAA time_tag into a thread-safe Instant.
     * Appends 'Z' to explicitly denote Coordinated Universal Time (UTC).
     */
    val timestamp: Instant
        get() = Instant.parse(timeTagString + "Z")
}
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

    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun fetchLatestKpIndex(): List<KpIndexStatus> {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(kpUrl))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                // One single line handles the entire unmarshalling into our domain model
                return jsonParser.decodeFromString<List<KpIndexStatus>>(response.body()).takeLast(8)
            } else {
                println("NOAA responded with an error: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Failed to fetch Kp trend: ${e.message}")
        }
        return emptyList()
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