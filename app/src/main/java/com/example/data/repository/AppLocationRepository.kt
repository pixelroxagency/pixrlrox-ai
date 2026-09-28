package com.example.data.repository

import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLocationRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("pixelrox_shared_location_prefs", Context.MODE_PRIVATE)

    private val _savedLocation = MutableStateFlow(
        SavedLocation(
            latitude = java.lang.Double.longBitsToDouble(prefs.getLong("lat", java.lang.Double.doubleToLongBits(23.8443))),
            longitude = java.lang.Double.longBitsToDouble(prefs.getLong("lng", java.lang.Double.doubleToLongBits(89.6384))),
            displayName = prefs.getString("display_name", "Sujanagar, Pabna, Bangladesh") ?: "Sujanagar, Pabna, Bangladesh",
            source = try {
                LocationSource.valueOf(prefs.getString("source", LocationSource.DEFAULT.name) ?: LocationSource.DEFAULT.name)
            } catch (_: Exception) {
                LocationSource.DEFAULT
            }
        )
    )
    val savedLocation: StateFlow<SavedLocation> = _savedLocation.asStateFlow()

    fun setLocation(latitude: Double, longitude: Double, displayName: String, source: LocationSource) {
        prefs.edit()
            .putLong("lat", java.lang.Double.doubleToLongBits(latitude))
            .putLong("lng", java.lang.Double.doubleToLongBits(longitude))
            .putString("display_name", displayName)
            .putString("source", source.name)
            .apply()

        _savedLocation.value = SavedLocation(
            latitude = latitude,
            longitude = longitude,
            displayName = displayName,
            source = source
        )
    }

    fun requestDeviceLocation(
        onSuccess: (SavedLocation) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val newLoc = SavedLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        displayName = "Current Location",
                        source = LocationSource.GPS
                    )
                    setLocation(newLoc.latitude, newLoc.longitude, newLoc.displayName, newLoc.source)
                    onSuccess(newLoc)
                } else {
                    try {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                        val provider = lm?.getProviders(true)?.firstOrNull {
                            it == android.location.LocationManager.GPS_PROVIDER ||
                            it == android.location.LocationManager.NETWORK_PROVIDER
                        }
                        val fallbackLoc = provider?.let { lm.getLastKnownLocation(it) }
                        if (fallbackLoc != null) {
                            val newLoc = SavedLocation(
                                latitude = fallbackLoc.latitude,
                                longitude = fallbackLoc.longitude,
                                displayName = "Current Location",
                                source = LocationSource.GPS
                            )
                            setLocation(newLoc.latitude, newLoc.longitude, newLoc.displayName, newLoc.source)
                            onSuccess(newLoc)
                        } else {
                            onFailure("No GPS location available. Try manual city selection.")
                        }
                    } catch (e: Exception) {
                        onFailure("Failed to obtain device location: ${e.message}")
                    }
                }
            }.addOnFailureListener { e ->
                onFailure("Location provider error: ${e.message}")
            }
        } else {
            onFailure("Location permission not granted.")
        }
    }
}
