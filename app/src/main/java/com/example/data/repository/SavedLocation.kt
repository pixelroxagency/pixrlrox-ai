package com.example.data.repository

enum class LocationSource {
    DEFAULT,
    GPS,
    MANUAL
}

data class SavedLocation(
    val latitude: Double = 23.8443,
    val longitude: Double = 89.6384,
    val displayName: String = "Sujanagar, Pabna, Bangladesh",
    val source: LocationSource = LocationSource.DEFAULT
)
