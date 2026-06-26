package se.metricspace.opendata

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings( val selectedLocations: List<Location> = emptyList(), val currentLocationName: String? = null )