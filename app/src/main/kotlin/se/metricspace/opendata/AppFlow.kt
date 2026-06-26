package se.metricspace.opendata

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.Locale

class AppFlow(
    private val geocodingService: GeocodingService,
    private val weatherService: WeatherService,
    private val flightService: FlightService
) {
    private val settingsFile = File("settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun start() {
        println("=== Välkommen till Testskottet ===")

        var settings = laddaInstallningar()

        if (settings.selectedLocations.isEmpty()) {
            println("\nInga sparade platser hittades. Låt oss lägga till din första!")
            settings = laggTillNyPlatsFlode(settings)
        }

        // Välj plats initialt
        var valdPlats = settings.selectedLocations.find { it.name == settings.currentLocationName }
            ?: visaPlatsMeny(settings)

        // Den stora evighetsloopen (Huvudmenyn)
        while (true) {
            println("\n=======================================")
            println("📍 AKTIV PLATS: ${valdPlats.name}")
            println("=======================================")
            println("[1] 🌟 Kolla Väder & Stjärnskådning")
            println("[2] ✈️  Starta Flygradar")
            println("[3] 🌍 Byt Plats")
            println("[4] 💤 Avsluta programmet")
            print("Ditt val: ")

            when (readlnOrNull()?.trim()) {
                "1" -> korVaderFlode(valdPlats)
                "2" -> korRadarFlode(valdPlats)
                "3" -> {
                    valdPlats = visaPlatsMeny(settings)
                    // Uppdatera settings med det nya aktiva valet
                    settings = settings.copy(currentLocationName = valdPlats.name)
                    settingsFile.writeText(json.encodeToString(settings))
                }
                "4" -> {
                    println("Avslutar processer och stänger ner. Snyggt kodat ikväll! God natt!")
                    break // Bryter while-loopen och stänger programmet
                }
                else -> println("Ogiltigt val, försök igen.")
            }
        }
    }

    private fun korVaderFlode(plats: Location) {
        println("\nSöker väderprognos för ${plats.name}...")
        val forecasts = weatherService.getWeatherForecast(plats.latitude, plats.longitude)

        if (forecasts != null) {
            val perfektaTimmar = forecasts.filter { f ->
                f.isClearSky && f.precipitationMm == 0.0
            }

            if (perfektaTimmar.isEmpty()) {
                println("Tyvärr, inget klockrent stjärnskådarväder i sikte. ☁️")
            } else {
                println("🌟 BÄSTA TILLFÄLLEN FÖR OBSERVATION:")
                perfektaTimmar.take(5).forEach { f ->
                    println(" - ${f.time} | Sikt: ${f.visibilityKm} km | Vind: ${f.windSpeed} m/s")
                }
            }
        } else {
            println("Kunde inte hämta vädret just nu.")
        }
    }

    private fun korRadarFlode(plats: Location) {
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

                val radarUrl = "https://globe.adsbexchange.com/?icao=${f.icao24}"

                println(
                    "   - ${f.callsign.padEnd(8)} | " +
                            "Riktning: ${f.compassDirection.padEnd(2)} ($trackStr°) | " +
                            "Höjd: $altStr m | " +
                            "Fart: $spdStr km/h | " +
                            "Land: ${f.country.padEnd(10)}$mysteryTag\n" +
                            "     Karta: $radarUrl\n" +
                            "     " + "-".repeat(80)
                )
            }
        }
    }

    private fun laddaInstallningar(): AppSettings {
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

    private fun visaPlatsMeny(settings: AppSettings): Location {
        while (true) {
            println("\nVälj en plats att använda:")
            settings.selectedLocations.forEachIndexed { index, plats ->
                val markering = if (plats.name == settings.currentLocationName) " (aktiv)" else ""
                println("[${index + 1}] ${plats.name}$markering")
            }
            println("[N] Lägg till en ny plats")
            print("Ditt val: ")

            val input = readln().trim().lowercase()

            if (input == "n") {
                val uppdateradeSettings = laggTillNyPlatsFlode(settings)
                return visaPlatsMeny(uppdateradeSettings) // Starta om menyn med nya datan
            }

            val valdtIndex = input.toIntOrNull()?.minus(1)
            if (valdtIndex != null && valdtIndex in settings.selectedLocations.indices) {
                val valPlats = settings.selectedLocations[valdtIndex]

                // Spara valet som aktivt till nästa gång appen startar
                val nyaSettings = settings.copy(currentLocationName = valPlats.name)
                settingsFile.writeText(json.encodeToString(nyaSettings))

                return valPlats
            }

            println("Ogiltigt val, försök igen.")
        }
    }

    private fun laggTillNyPlatsFlode(nuvarandeSettings: AppSettings): AppSettings {
        print("Skriv namnet på platsen (t.ex. 'Ljusterö' eller 'Sergels Torg'): ")
        val sokning = readln().trim()

        println("Söker efter koordinater...")
        val hittadPlats = geocodingService.findLocation(sokning)

        if (hittadPlats != null) {
            println("Hittade: ${hittadPlats.name}")

            // Lägg till i listan och spara till filen
            val nyLista = nuvarandeSettings.selectedLocations + hittadPlats
            val nyaSettings = nuvarandeSettings.copy(
                selectedLocations = nyLista,
                currentLocationName = hittadPlats.name
            )

            settingsFile.writeText(json.encodeToString(nyaSettings))
            println("Platsen har sparats i settings.json!")
            return nyaSettings
        } else {
            println("Kunde tyvärr inte geokoda den platsen. Försök igen.")
            return laggTillNyPlatsFlode(nuvarandeSettings) // Försök igen
        }
    }
}
