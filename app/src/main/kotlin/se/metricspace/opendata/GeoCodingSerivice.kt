package se.metricspace.opendata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GeocodingService(private val client: HttpClient) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OsmResult(val display_name: String, val lat: String, val lon: String)

    fun findLocation(someLocation: String): Location? {
        try {
            val encodedString = URLEncoder.encode(someLocation, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedString&format=json&limit=1"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "se.metricspace.Locator/1.0 (mange27@hotmail.com)")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val osmLista = jsonParser.decodeFromString<List<OsmResult>>(response.body())
                val rawResult = osmLista.firstOrNull() ?: return null

                return Location( name = rawResult.display_name, latitude = rawResult.lat.toDouble(), longitude = rawResult.lon.toDouble() )
            }
        } catch (e: Exception) {
            println("Some problem in findLocation: ${e.message}")
        }
        return null
    }
}