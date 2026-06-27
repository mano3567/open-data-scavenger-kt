package se.metricspace.opendata

import java.net.http.HttpClient

fun main() {
    val userAgent = "Open Data Scavenger/0.1 (https://metricspace.se)" // should be a setting
    val commonHttpClient = HttpClient.newHttpClient()
    val deepSpaceNetworkService = DeepSpaceNetworkService(commonHttpClient, userAgent)
    val flightService = FlightService(commonHttpClient, userAgent)
    val geocodingService = GeocodingService(commonHttpClient, userAgent)
    val weatherService = WeatherService(commonHttpClient, userAgent)

    val appFlow = AppFlow(deepSpaceNetworkService, flightService, geocodingService, weatherService)

    appFlow.start()
}
