package se.metricspace.opendata

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherService(private val client: HttpClient) {
    // Ett snyggt sätt att städa upp SMHI:s "9999.0"-problem
    private fun Double?.validSmhiValue(fallback: Double = 0.0): Double {
        return if (this == null || this == 9999.0) fallback else this
    }
        // ignoreUnknownKeys är guld värt här, eftersom SMHI skickar massor av data vi inte bryr oss om
    private val jsonParser = Json { ignoreUnknownKeys = true }

    data class WeatherForecast(
        val time: String,
        val cloudCoverOctas: Int,
        val isClearSky: Boolean,
        val temperature: Double,
        val windSpeed: Double,
        val humidity: Double,
        val visibilityKm: Double,
        val precipitationMm: Double
    )
    // --- Nya, mycket renare Data Classes för SMHI SNOW1gv1 ---
    @Serializable private data class SmhiResponse(val timeSeries: List<TimeSeries>)
    @Serializable private data class TimeSeries(val time: String, val data: WeatherData)
    @Serializable
    private data class WeatherData(
        val cloud_area_fraction: Double? = null,
        val symbol_code: Double? = null,

        // Nya tydliga namn för kläder & komfort:
        val air_temperature: Double? = null,      // Temperatur i Celsius
        val wind_speed: Double? = null,           // Vind i m/s
        val relative_humidity: Double? = null,    // Luftfuktighet i %

        // Nya tydliga namn för nederbörd:
        val precipitation_amount_mean: Double? = null, // Regn i mm/h
        val predominant_precipitation_type_at_surface: Double? = null, // Typ av regn (0-6)

        // Nya namnet för sikt:
        val visibility_in_air: Double? = null     // Sikt i km
    )
    fun getWeatherForecast(lat: Double, lon: Double): List<WeatherForecast>? {
        try {
            val latStr = String.format(Locale.US, "%.5f", lat)
            val lonStr = String.format(Locale.US, "%.5f", lon)

            // Den helt nya SNOW1g-URL:en!
            val url = "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/$lonStr/lat/$latStr/data.json"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val rawJson = Json.parseToJsonElement(response.body())
                val firstHour = rawJson.jsonObject["timeSeries"]?.jsonArray?.firstOrNull()

                val smhiData = jsonParser.decodeFromString<SmhiResponse>(response.body())
                val forecasts = mutableListOf<WeatherForecast>()

                for (timeStep in smhiData.timeSeries.take(120)) { // Din take(48)!
                    val data = timeStep.data

                    // Moln är lite speciellt eftersom vi vill ha en Int (0-8)
                    val rawCloud = data.cloud_area_fraction.validSmhiValue(8.0)
                    val cloudCover = rawCloud.toInt()

                    // 1. Tolka SMHI:s UTC-tid
                    val parsedTime = ZonedDateTime.parse(timeStep.time)

                    // 2. Konvertera till datorns lokala tidszon (Sverige)
                    val localTime = parsedTime.withZoneSameInstant(ZoneId.systemDefault())
                    if (localTime.isBefore(ZonedDateTime.now())) {
                        continue
                    }
                    // Alternativt kan du hårdkoda det om du vill vara bombsäker:
                    // val localTime = parsedTime.withZoneSameInstant(ZoneId.of("Europe/Stockholm"))

                    // 3. Formatera den LOKALA tiden
                    val displayTime = localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    forecasts.add(
                        WeatherForecast(
                            time = displayTime,
                            cloudCoverOctas = cloudCover,
                            isClearSky = cloudCover == 0,

                            // Plocka in resten superlätt!
                            temperature = data.air_temperature.validSmhiValue(),
                            windSpeed = data.wind_speed.validSmhiValue(),
                            humidity = data.relative_humidity.validSmhiValue(),
                            visibilityKm = data.visibility_in_air.validSmhiValue(),
                            precipitationMm = data.precipitation_amount_mean.validSmhiValue()
                        )
                    )
                }
                return forecasts
            } else {
                println("SMHI svarade med ett fel: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            println("Kunde inte tolka vädret: ${e.message}")
        }
        return null
    }
}