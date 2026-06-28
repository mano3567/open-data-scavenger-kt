🌌 Open Data Scavenger

A Kotlin-based Command Line Interface (CLI) application that aggregates fascinating open data from various scientific and public APIs. From tracking flights in your local airspace to forecasting auroras using real-time solar wind telemetry from the L1 Lagrange point.

✨ Features

The application features an interactive terminal menu with the following capabilities:

🌍 Location Management: Search and save geographic locations using the OpenStreetMap (Nominatim) geocoding API.

☁️ Weather & Stargazing: Fetches detailed meteorological data from the Swedish Meteorological and Hydrological Institute (SMHI). It specifically filters for clear skies and optimal stargazing conditions.

✈️ Live Flight Radar: Tracks live commercial and private aircraft over your selected coordinates using the OpenSky Network API.

📡 Deep Space Network (DSN): Parses real-time XML data directly from NASA's Deep Space Network to see which antennas on Earth are currently communicating with spacecraft like Voyager, James Webb, and Perseverance.

🪐 Space Weather & Aurora Forecast: (Work in progress / Latest addition)

Fetches global Planetary Kp-index trends from NOAA SWPC.

Calculates the Geomagnetic (Dipole) Latitude of your saved locations to accurately estimate the required Kp-index for visual auroras.

Analyzes real-time Solar Wind telemetry (IMF $B_z$, speed, and density) from the DSCOVR satellite at the L1 point to forecast aurora chances 30-60 minutes in advance.

🛠️ Tech Stack

This project is built with modern, idiomatic Kotlin and avoids heavy frameworks in favor of standard libraries where possible:

Language: Kotlin

HTTP Client: Native Java 11+ java.net.http.HttpClient

JSON Parsing: kotlinx.serialization (Type-safe, reflection-free unmarshalling)

XML Parsing: Standard javax.xml.parsers and XPath for legacy NASA feeds.

Time Handling: java.time.Instant for robust UTC time mapping.

🚀 Architecture Highlights

Separation of Concerns: Distinct service classes (WeatherService, FlightService, DeepSpaceNetworkService, SpaceWeatherService) handle their respective domains.

Extension Functions: Uses Kotlin's powerful extension functions (e.g., Location.getGeomagneticLatitude()) to keep data classes clean while enriching them with complex domain logic.

Immutable State: Employs Kotlin's data class copy mechanics for state management.

⚙️ Getting Started

Prerequisites

JDK 11 or higher installed on your machine.

Kotlin compiler (kotlinc) or IntelliJ IDEA.

Running the App

Clone the repository.

Compile and run Main.kt.

The app will automatically generate a settings.json file in the root directory to persist your location preferences between sessions.

📜 License

This project is for educational purposes. Please respect the rate limits and terms of service of the respective open data providers:

SMHI Open Data

OpenSky Network

NASA Deep Space Network

NOAA Space Weather Prediction Center
