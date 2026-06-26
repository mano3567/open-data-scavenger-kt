package se.metricspace.opendata

import java.net.http.HttpClient

fun main() {
    val commonHttpClient = HttpClient.newHttpClient()
    val geocodingService = GeocodingService(commonHttpClient)
    val flightService = FlightService(commonHttpClient)
    val weatherService = WeatherService(commonHttpClient)
    val appFlow = AppFlow(geocodingService, weatherService, flightService)
    appFlow.start()
}
