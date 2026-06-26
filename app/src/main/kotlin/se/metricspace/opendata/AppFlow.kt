package se.metricspace.opendata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.Locale

@Serializable
data class AppSettings( val selectedLocations: List<Location> = emptyList(), val currentLocationName: String? = null )

class AppFlow(
    private val deepSpaceNetworkService: DeepSpaceNetworkService,
    private val flightService: FlightService,
    private val geocodingService: GeocodingService,
    private val weatherService: WeatherService ) {
    private val settingsFile = File("settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun start() {
        println("=== Välkommen till Testskottet ===")

        var settings = loadSettings()

        if (settings.selectedLocations.isEmpty()) {
            println("\nInga sparade platser hittades. Låt oss lägga till din första!")
            settings = addNewLocation(settings)
        }

        // Välj plats initialt
        var selectedLocation = settings.selectedLocations.find { it.name == settings.currentLocationName }
            ?: showLocationMenu(settings)

        // Den stora evighetsloopen (Huvudmenyn)
        while (true) {
            println("\n=======================================")
            println("📍 AKTIV PLATS: ${selectedLocation.name} Lon "+String.format(Locale.US, "%.2f", selectedLocation.longitude)+" Lat "+String.format(Locale.US, "%.2f", selectedLocation.latitude))
            println("=======================================")
            println("[1] 🌟 Kolla Väder & Stjärnskådning")
            println("[2] ✈️  Starta Flygradar")
            println("[3] 🌍 Byt Plats")
            println("[4] 💤 Snapshot för kommunikation från Deep Space Network")
            println("[5] 💤 Avsluta programmet")
            print("Ditt val: ")

            when (readlnOrNull()?.trim()) {
                "1" -> goWeatherFlow(selectedLocation)
                "2" -> goRadarFLow(selectedLocation)
                "3" -> {
                    selectedLocation = showLocationMenu(settings)
                    // Uppdatera settings med det nya aktiva valet
                    settings = settings.copy(currentLocationName = selectedLocation.name)
                    settingsFile.writeText(json.encodeToString(settings))
                }
                "4" -> goDeepSpaceNetworkFlow()
                "5" -> {
                    println("Avslutar processer och stänger ner. Snyggt kodat ikväll! God natt!")
                    break // Bryter while-loopen och stänger programmet
                }
                else -> println("Ogiltigt val, försök igen.")
            }
        }
    }

    private fun goDeepSpaceNetworkFlow() {
        println("\nVisar ögonblicksbild från Deep Space Network...")
        val stations = deepSpaceNetworkService.getDataFromDsnAtNasa()
        for(station in stations) {
            println(station.name+": "+station.friendlyName+" -> "+station.timeUTC)
            for(dish in station.dishes) {
                println(dish)
            }
        }
    }

    private fun goWeatherFlow(plats: Location) {
        println("\nSöker väderprognos för ${plats.name}...")
        val forecasts = weatherService.getWeatherForecast(plats.latitude, plats.longitude)

        if (forecasts != null) {
            forecasts.take(5).forEach { f ->
                println("${f.time} | Sikt: ${f.visibilityKm} km | Vind: ${f.windSpeed} m/s | Precip: ${f.precipitationMm} mm | Clear Sky: ${f.isClearSky} | Humidity: ${f.humidity} | Octas: ${f.cloudCoverOctas}/8 | Temperature: ${f.temperature}")
            }

            val goodObservingTimes = forecasts.filter { f ->
                f.isClearSky && f.precipitationMm == 0.0
            }

            if (goodObservingTimes.isEmpty()) {
                println("Tyvärr, inget klockrent stjärnskådarväder i sikte. ☁️")
            } else {
                println("🌟 BÄSTA TILLFÄLLEN FÖR OBSERVATION:")
                goodObservingTimes.take(5).forEach { f ->
                    println("${f.time} | Sikt: ${f.visibilityKm} km | Vind: ${f.windSpeed} m/s | Precip: ${f.precipitationMm} mm | Clear Sky: ${f.isClearSky} | Humidity: ${f.humidity} | Octas: ${f.cloudCoverOctas}/8 | Temperature: ${f.temperature}")
                }
            }
        } else {
            println("Kunde inte hämta vädret just nu.")
        }
    }

    private fun goRadarFLow(plats: Location) {
        println("\nSöker på radarn över ${plats.name}...")
        val flights = flightService.getFlightsOver(plats.latitude, plats.longitude)

        if (flights.isEmpty()) {
            println("Luftrummet är helt tomt just nu.")
        } else {
            println("✈️  Hittade ${flights.size} flygplan i luften!")
            flights.sortedBy { it.altitudeMeters }.forEach { f ->
                val altStr = String.format(Locale.US, "%.0f", f.altitudeMeters).padStart(5)
                val spdStr = String.format(Locale.US, "%.0f", f.velocityKmh).padStart(4)
                val trackStr = String.format(Locale.US, "%3.0f", f.trueTrackDegrees)

                val isMystery = f.country == "Okänt" || f.callsign.startsWith("VJT")
                val mysteryTag = if (isMystery) " 🕵️‍♂️" else ""
                val longitude = String.format(Locale.US, "%.2f", f.longitude).padStart(4)
                val latitude = String.format(Locale.US, "%.2f", f.latitude).padStart(4)
                val radarUrl = "https://globe.adsbexchange.com/?icao=${f.icao24}"

                println(
                    "   - ${f.callsign.padEnd(8)} | " +
                            "Riktning: ${f.compassDirection.padEnd(2)} ($trackStr°) | " +
                            "Long: $longitude | " +
                            "Lati: $latitude | " +
                            "Höjd: $altStr m | " +
                            "Fart: $spdStr km/h | " +
                            "Land: ${f.country.padEnd(10)}$mysteryTag\n" +
                            "     Karta: $radarUrl\n" +
                            "     " + "-".repeat(80)
                )
            }
        }
    }

    private fun loadSettings(): AppSettings {
        return if (settingsFile.exists()) {
            try {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } catch (e: Exception) {
                println("Kunde inte läsa settings.json, skapar nya standardinställningar.")
                AppSettings()
            }
        } else {
            AppSettings()
        }
    }

    private fun showLocationMenu(settings: AppSettings): Location {
        while (true) {
            println("\nVälj en plats att använda:")
            settings.selectedLocations.forEachIndexed { index, plats ->
                val markering = if (plats.name == settings.currentLocationName) " (aktiv)" else ""
                println("[${index + 1}] ${plats.name}$markering Lon "+String.format(Locale.US, "%.2f", plats.longitude)+" Lat "+String.format(Locale.US, "%.2f", plats.latitude))
            }
            println("[N] Lägg till en ny plats")
            print("Ditt val: ")

            val input = readln().trim().lowercase()

            if (input == "n") {
                val uppdateradeSettings = addNewLocation(settings)
                return showLocationMenu(uppdateradeSettings)
            }

            val valdtIndex = input.toIntOrNull()?.minus(1)
            if (valdtIndex != null && valdtIndex in settings.selectedLocations.indices) {
                val valPlats = settings.selectedLocations[valdtIndex]

                val nyaSettings = settings.copy(currentLocationName = valPlats.name)
                settingsFile.writeText(json.encodeToString(nyaSettings))

                return valPlats
            }

            println("Ogiltigt val, försök igen.")
        }
    }

    private fun addNewLocation(appSettings: AppSettings): AppSettings {
        print("Skriv namnet på platsen (t.ex. 'Ljusterö' eller 'Sergels Torg'): ")
        val searchTerm = readln().trim()

        println("Söker efter koordinater...")
        val locationFound = geocodingService.findLocation(searchTerm)

        if (locationFound != null) {
            println("Hittade: ${locationFound.name}")

            val newList = appSettings.selectedLocations + locationFound
            val newSettings = appSettings.copy(
                selectedLocations = newList,
                currentLocationName = locationFound.name
            )

            settingsFile.writeText(json.encodeToString(newSettings))
            println("Platsen har sparats i settings.json!")
            return newSettings
        } else {
            println("Kunde tyvärr inte geokoda den platsen. Försök igen.")
            return addNewLocation(appSettings) // Försök igen
        }
    }
}
