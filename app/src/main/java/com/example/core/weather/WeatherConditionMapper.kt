package com.example.core.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Calendar

data class WeatherConditionInfo(
    val conditionName: String,
    val icon: ImageVector,
    val isDay: Boolean
)

object WeatherConditionMapper {

    fun formatTemperatureCelsius(temp: Double): String {
        val rounded = Math.round(temp)
        return "${rounded}°C"
    }

    fun formatForecastTemp(max: Double, min: Double): String {
        return "${Math.round(max)}° / ${Math.round(min)}°"
    }

    fun isDaytime(
        isDayField: Int? = null,
        sunrise: String? = null,
        sunset: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. API isDayField (1 = Day, 0 = Night)
        if (isDayField != null && isDayField >= 0) {
            return isDayField == 1
        }

        // 2. Sunrise / Sunset timestamps / time strings
        if (!sunrise.isNullOrBlankOrDash() && !sunset.isNullOrBlankOrDash()) {
            val sunriseMinutes = parseMinutesFromTimeStr(sunrise!!)
            val sunsetMinutes = parseMinutesFromTimeStr(sunset!!)

            if (sunriseMinutes != null && sunsetMinutes != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
                val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                return currentMinutes in sunriseMinutes..sunsetMinutes
            }
        }

        // 3. Fallback to current hour
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 6..17
    }

    private fun String?.isNullOrBlankOrDash(): Boolean {
        return this.isNullOrBlank() || this == "--"
    }

    fun parseMinutesFromTimeStr(timeStr: String): Int? {
        val clean = timeStr.trim().split("T").last()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        return null
    }

    fun mapCondition(
        weatherCode: Int,
        isDay: Boolean
    ): WeatherConditionInfo {
        return when (weatherCode) {
            0 -> {
                WeatherConditionInfo(
                    conditionName = if (isDay) "Sunny" else "Clear",
                    icon = if (isDay) Icons.Default.WbSunny else Icons.Default.Nightlight,
                    isDay = isDay
                )
            }
            1, 2 -> {
                WeatherConditionInfo(
                    conditionName = "Partly Cloudy",
                    icon = if (isDay) Icons.Default.WbCloudy else Icons.Default.NightsStay,
                    isDay = isDay
                )
            }
            3 -> {
                WeatherConditionInfo(
                    conditionName = "Cloudy",
                    icon = Icons.Default.Cloud,
                    isDay = isDay
                )
            }
            45, 48 -> {
                WeatherConditionInfo(
                    conditionName = "Fog / Mist",
                    icon = Icons.Default.Air,
                    isDay = isDay
                )
            }
            51, 53, 55, 56, 57 -> {
                WeatherConditionInfo(
                    conditionName = "Drizzle",
                    icon = Icons.Default.Grain,
                    isDay = isDay
                )
            }
            61, 63, 65, 66, 67, 80, 81, 82 -> {
                WeatherConditionInfo(
                    conditionName = "Rain",
                    icon = Icons.Default.WaterDrop,
                    isDay = isDay
                )
            }
            71, 73, 75, 77, 85, 86 -> {
                WeatherConditionInfo(
                    conditionName = "Snow",
                    icon = Icons.Default.AcUnit,
                    isDay = isDay
                )
            }
            95, 96, 99 -> {
                WeatherConditionInfo(
                    conditionName = "Thunderstorm",
                    icon = Icons.Default.Thunderstorm,
                    isDay = isDay
                )
            }
            else -> {
                WeatherConditionInfo(
                    conditionName = if (isDay) "Sunny" else "Clear",
                    icon = if (isDay) Icons.Default.WbSunny else Icons.Default.Nightlight,
                    isDay = isDay
                )
            }
        }
    }
}
