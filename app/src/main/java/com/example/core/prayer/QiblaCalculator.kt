package com.example.core.prayer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object QiblaCalculator {
    private const val KAABA_LATITUDE = 21.422518
    private const val KAABA_LONGITUDE = 39.826182

    /**
     * Calculates the Great Circle bearing toward the Kaaba in degrees (0..360).
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLambda = Math.toRadians(KAABA_LONGITUDE - longitude)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)

        val initialBearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(initialBearingRad)

        return (bearingDeg + 360.0) % 360.0
    }
}
