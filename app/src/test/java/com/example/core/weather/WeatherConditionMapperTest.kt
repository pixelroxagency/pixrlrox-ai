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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConditionMapperTest {

    @Test
    fun testClearDayCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 0, isDay = true)
        assertEquals("Sunny", condition.conditionName)
        assertEquals(Icons.Default.WbSunny, condition.icon)
        assertTrue(condition.isDay)
    }

    @Test
    fun testClearNightCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 0, isDay = false)
        assertEquals("Clear", condition.conditionName)
        assertEquals(Icons.Default.Nightlight, condition.icon)
        assertFalse(condition.isDay)
    }

    @Test
    fun testPartlyCloudyDayCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 1, isDay = true)
        assertEquals("Partly Cloudy", condition.conditionName)
        assertEquals(Icons.Default.WbCloudy, condition.icon)
        assertTrue(condition.isDay)
    }

    @Test
    fun testPartlyCloudyNightCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 2, isDay = false)
        assertEquals("Partly Cloudy", condition.conditionName)
        assertEquals(Icons.Default.NightsStay, condition.icon)
        assertFalse(condition.isDay)
    }

    @Test
    fun testCloudyCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 3, isDay = true)
        assertEquals("Cloudy", condition.conditionName)
        assertEquals(Icons.Default.Cloud, condition.icon)
    }

    @Test
    fun testFogMistCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 45, isDay = true)
        assertEquals("Fog / Mist", condition.conditionName)
        assertEquals(Icons.Default.Air, condition.icon)
    }

    @Test
    fun testDrizzleCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 53, isDay = true)
        assertEquals("Drizzle", condition.conditionName)
        assertEquals(Icons.Default.Grain, condition.icon)
    }

    @Test
    fun testRainCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 61, isDay = true)
        assertEquals("Rain", condition.conditionName)
        assertEquals(Icons.Default.WaterDrop, condition.icon)
    }

    @Test
    fun testSnowCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 71, isDay = true)
        assertEquals("Snow", condition.conditionName)
        assertEquals(Icons.Default.AcUnit, condition.icon)
    }

    @Test
    fun testThunderstormCondition() {
        val condition = WeatherConditionMapper.mapCondition(weatherCode = 95, isDay = true)
        assertEquals("Thunderstorm", condition.conditionName)
        assertEquals(Icons.Default.Thunderstorm, condition.icon)
    }

    @Test
    fun testTemperatureCelsiusFormatting() {
        assertEquals("28°C", WeatherConditionMapper.formatTemperatureCelsius(28.0))
        assertEquals("28°C", WeatherConditionMapper.formatTemperatureCelsius(28.4))
        assertEquals("29°C", WeatherConditionMapper.formatTemperatureCelsius(28.7))
        assertEquals("0°C", WeatherConditionMapper.formatTemperatureCelsius(0.1))
        assertEquals("-5°C", WeatherConditionMapper.formatTemperatureCelsius(-4.9))
    }

    @Test
    fun testDaytimeCalculationFromIsDayField() {
        assertTrue(WeatherConditionMapper.isDaytime(isDayField = 1))
        assertFalse(WeatherConditionMapper.isDaytime(isDayField = 0))
    }

    @Test
    fun testDaytimeCalculationFromSunriseSunset() {
        // Assume current time is 10:00 AM
        val cal10am = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
        }
        val isDay10am = WeatherConditionMapper.isDaytime(
            sunrise = "06:00",
            sunset = "18:00",
            currentTimeMillis = cal10am.timeInMillis
        )
        assertTrue(isDay10am)

        // Assume current time is 11:00 PM
        val cal11pm = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 0)
        }
        val isDay11pm = WeatherConditionMapper.isDaytime(
            sunrise = "06:00",
            sunset = "18:00",
            currentTimeMillis = cal11pm.timeInMillis
        )
        assertFalse(isDay11pm)
    }
}
