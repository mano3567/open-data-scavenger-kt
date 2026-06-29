package se.metricspace.opendata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class Location(val addressType: String, val boundingBox: List<String>, val displayName: String, val importance: Double, val latitude: Double, val licence: String, val longitude: Double, val name: String, val osmId: Long, val osmType: String, val placeId: Long, val placeRank: Int, val type: String)

class GeoLocationService(private val client: HttpClient, private val userAgent: String) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OsmResult(
        val addresstype: String,
        val boundingbox: List<String>,
        val display_name: String,
        val importance: Double,
        val lat: String,
        val licence: String,
        val lon: String,
        val name: String,
        val osm_id: Long,
        val osm_type: String,
        val place_id: Long,
        val place_rank: Int,
        val type: String
    )

    fun findLocation(someLocation: String): Location? {
        try {
            val encodedString = URLEncoder.encode(someLocation, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedString&format=json&limit=1"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val osmLista = jsonParser.decodeFromString<List<OsmResult>>(response.body())
                val rawResult = osmLista.firstOrNull() ?: return null

                return Location( addressType = rawResult.addresstype, boundingBox = rawResult.boundingbox, displayName = rawResult.display_name, importance = rawResult.importance, latitude = rawResult.lat.toDouble(), licence = rawResult.licence, longitude = rawResult.lon.toDouble(), osmId = rawResult.osm_id, osmType = rawResult.osm_type, placeId = rawResult.place_id, placeRank = rawResult.place_rank, type = rawResult.type, name = rawResult.name )
            }
        } catch (e: Exception) {
            println("Some problem in findLocation: ${e.message}")
        }
        return null
    }
}