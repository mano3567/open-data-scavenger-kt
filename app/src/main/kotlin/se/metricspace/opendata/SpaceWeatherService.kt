package se.metricspace.opendata


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlin.math.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

data class SolarWindMagneticField( val bxGsm: Double?, val byGsm: Double?, val bzGsm: Double?, val latitudeGsm: Double?, val longitudeGsm: Double?, val timeTag: Instant, val totalFieldBt: Double? )

data class SolarWindPlasma(val density: Double?, val speed: Double?, val temperature: Double?, val timeTag: Instant)

data class CombinedSolarWind(
    val timeTag: Instant,
    val plasma: SolarWindPlasma?,
    val mag: SolarWindMagneticField?
)

private const val POLE_LAT = 80.5 * PI / 180.0
private const val POLE_LON = -72.5 * PI / 180.0

fun mergeSolarWindData( plasmaList: List<SolarWindPlasma>, magList: List<SolarWindMagneticField> ): List<CombinedSolarWind> {

    // 1. Skapa Maps där nyckeln är tidsstämpeln avrundad till jämn minut
    val plasmaByMinute = plasmaList.associateBy {
        it.timeTag.truncatedTo(ChronoUnit.MINUTES)
    }
    val magByMinute = magList.associateBy {
        it.timeTag.truncatedTo(ChronoUnit.MINUTES)
    }

    val allUniqueMinutes = (plasmaByMinute.keys + magByMinute.keys).sorted()

    return allUniqueMinutes.map { minute ->
        CombinedSolarWind(
            timeTag = minute,
            plasma = plasmaByMinute[minute], // Kan bli null om plasma saknas denna minut
            mag = magByMinute[minute]        // Kan bli null om mag saknas denna minut
        )
    }
}
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
//    private val noaaScalesUrl = "https://services.swpc.noaa.gov/products/noaa-scales.json"
    private val magneticFieldUrl = "https://services.swpc.noaa.gov/products/solar-wind/mag-2-hour.json"
    private val plasmaUrl = "https://services.swpc.noaa.gov/products/solar-wind/plasma-2-hour.json"
    private val kpUrl = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private val noaaFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun parseNoaaDateTime(timeStr: String): Instant {
        val localDateTime = LocalDateTime.parse(timeStr, noaaFormatter)
        return localDateTime.toInstant(ZoneOffset.UTC)
    }

    fun fetchSolarWindMagneticField(): List<SolarWindMagneticField> {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(magneticFieldUrl))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val jsonString = response.body()
                val rawMatrix = Json.decodeFromString<List<List<String>>>(jsonString)
                val magneticData = rawMatrix.drop(1).map { row ->
                    SolarWindMagneticField(
                        timeTag = parseNoaaDateTime(row[0]),
                        bxGsm = row[1].toDoubleOrNull(),
                        byGsm = row[2].toDoubleOrNull(),
                        bzGsm = row[3].toDoubleOrNull(),
                        longitudeGsm = row[4].toDoubleOrNull(),
                        latitudeGsm = row[5].toDoubleOrNull(),
                        totalFieldBt = row[6].toDoubleOrNull()
                    )
                }
                return magneticData.takeLast(30)
            } else {
                println("NOAA responded with an error: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Failed to fetch Kp trend: ${e.message}")
        }
        return emptyList()
    }

    fun fetchSolarWindPlasmas(): List<SolarWindPlasma> {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(plasmaUrl))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val jsonString = response.body()
                val rawMatrix = Json.decodeFromString<List<List<String>>>(jsonString)
                val plasmaData = rawMatrix.drop(1).map { row ->
                    SolarWindPlasma(
                        timeTag = parseNoaaDateTime(row[0]), // Här sker konverteringen
                        density = row[1].toDoubleOrNull(),
                        speed = row[2].toDoubleOrNull(),
                        temperature = row[3].toDoubleOrNull()
                    )
                }
                return plasmaData.takeLast(30)
            } else {
                println("NOAA responded with an error: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Failed to fetch Kp trend: ${e.message}")
        }
        return emptyList()
    }

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

    fun fetchDetailedTelemetry(): List<CombinedSolarWind> {
        try {
            val plasmaData = fetchSolarWindPlasmas()
            val magData = fetchSolarWindMagneticField()

            return mergeSolarWindData(plasmaData, magData)
        } catch (e: Exception) {
            println("Failed to fetch detailed solar wind telemetry: ${e.message}")
            return emptyList<CombinedSolarWind>()
        }
    }
}