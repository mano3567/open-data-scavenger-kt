package se.metricspace.opendata

import java.net.http.HttpClient

fun main() {
    val commonHttpClient = HttpClient.newHttpClient()
    val deepSpaceNetworkService = DeepSpaceNetworkService(commonHttpClient)
    val flightService = FlightService(commonHttpClient)
    val geocodingService = GeocodingService(commonHttpClient)
    val weatherService = WeatherService(commonHttpClient)

    val appFlow = AppFlow(deepSpaceNetworkService, flightService, geocodingService, weatherService)

    appFlow.start()
}
