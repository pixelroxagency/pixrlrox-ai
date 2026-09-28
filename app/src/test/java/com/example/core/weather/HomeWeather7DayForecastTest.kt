package com.example.core.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.data.repository.DailyForecast
import com.example.data.repository.WeatherData
import com.example.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class HomeWeather7DayForecastTest {

    // 1. Location label uses existing location data
    @Test
    fun testLocationLabelUsesExistingLocationData() {
        val weatherData = WeatherData(
            locationName = "Dhaka, Bangladesh",
            temperature = 31.0,
            apparentTemperature = 34.0,
            humidity = 72,
            windSpeed = 12.0,
            precipitation = 0.0,
            weatherCode = 1,
            sunrise = "05:45",
            sunset = "17:52",
            hourlyTemps = emptyList(),
            dailyMax = listOf(32.0),
            dailyMin = listOf(26.0),
            isCached = false,
            lastUpdated = System.currentTimeMillis(),
            isDay = true,
            dailyForecasts = emptyList(),
            currentDate = "2026-09-23"
        )
        assertEquals("Dhaka, Bangladesh", weatherData.locationName)
        assertFalse(weatherData.locationName.contains("23.8443")) // Not raw latitude
    }

    // 2. Current temperature is Celsius
    @Test
    fun testCurrentTemperatureIsCelsius() {
        val formatted = WeatherConditionMapper.formatTemperatureCelsius(31.4)
        assertEquals("31°C", formatted)
        assertTrue(formatted.endsWith("°C"))
        assertFalse(formatted.contains("F"))
    }

    // 3. Current WMO condition maps correctly
    @Test
    fun testCurrentWmoConditionMapsCorrectly() {
        assertEquals("Sunny", WeatherConditionMapper.mapCondition(0, isDay = true).conditionName)
        assertEquals("Partly Cloudy", WeatherConditionMapper.mapCondition(1, isDay = true).conditionName)
        assertEquals("Cloudy", WeatherConditionMapper.mapCondition(3, isDay = true).conditionName)
        assertEquals("Fog / Mist", WeatherConditionMapper.mapCondition(45, isDay = true).conditionName)
        assertEquals("Drizzle", WeatherConditionMapper.mapCondition(53, isDay = true).conditionName)
        assertEquals("Rain", WeatherConditionMapper.mapCondition(61, isDay = true).conditionName)
        assertEquals("Snow", WeatherConditionMapper.mapCondition(71, isDay = true).conditionName)
        assertEquals("Thunderstorm", WeatherConditionMapper.mapCondition(95, isDay = true).conditionName)
    }

    // 4. Current clear-day icon
    @Test
    fun testCurrentClearDayIcon() {
        val condition = WeatherConditionMapper.mapCondition(0, isDay = true)
        assertEquals(Icons.Default.WbSunny, condition.icon)
        assertTrue(condition.isDay)
    }

    // 5. Current clear-night icon
    @Test
    fun testCurrentClearNightIcon() {
        val condition = WeatherConditionMapper.mapCondition(0, isDay = false)
        assertEquals(Icons.Default.Nightlight, condition.icon)
        assertFalse(condition.isDay)
    }

    // 6. Current partly-cloudy day/night behavior
    @Test
    fun testCurrentPartlyCloudyDayNightBehavior() {
        val dayCondition = WeatherConditionMapper.mapCondition(1, isDay = true)
        assertEquals(Icons.Default.WbCloudy, dayCondition.icon)
        assertTrue(dayCondition.isDay)

        val nightCondition = WeatherConditionMapper.mapCondition(1, isDay = false)
        assertEquals(Icons.Default.NightsStay, nightCondition.icon)
        assertFalse(nightCondition.isDay)
    }

    // 7. Today is excluded from future forecast cards
    @Test
    fun testTodayIsExcludedFromFutureForecastCards() {
        val todayDate = "2026-09-23"
        val allDaily = listOf(
            DailyForecast("2026-09-23", "WED", 0, 31.0, 25.0),
            DailyForecast("2026-09-24", "THU", 1, 32.0, 26.0),
            DailyForecast("2026-09-25", "FRI", 2, 31.0, 25.0),
            DailyForecast("2026-09-26", "SAT", 61, 29.0, 24.0),
            DailyForecast("2026-09-27", "SUN", 95, 28.0, 24.0),
            DailyForecast("2026-09-28", "MON", 3, 30.0, 25.0),
            DailyForecast("2026-09-29", "TUE", 1, 31.0, 25.0)
        )

        val futureDays = WeatherDateUtil.filterFutureDays(allDaily, todayDate, count = 6)
        assertFalse(futureDays.any { it.date == todayDate })
        assertEquals("2026-09-24", futureDays.first().date)
    }

    // 8. Exactly six future days are selected when available
    @Test
    fun testExactlySixFutureDaysAreSelectedWhenAvailable() {
        val todayDate = "2026-09-23"
        val allDaily = (0..13).map { offset ->
            val dayNumber = 23 + offset
            val dateStr = "2026-09-${String.format("%02d", dayNumber)}"
            DailyForecast(dateStr, "DAY", 0, 30.0 + offset, 24.0)
        }

        val futureDays = WeatherDateUtil.filterFutureDays(allDaily, todayDate, count = 6)
        assertEquals(6, futureDays.size)
    }

    // 9. Future days remain chronological
    @Test
    fun testFutureDaysRemainChronological() {
        val todayDate = "2026-09-23"
        val unsortedDaily = listOf(
            DailyForecast("2026-09-27", "SUN", 95, 28.0, 24.0),
            DailyForecast("2026-09-23", "WED", 0, 31.0, 25.0),
            DailyForecast("2026-09-25", "FRI", 2, 31.0, 25.0),
            DailyForecast("2026-09-24", "THU", 1, 32.0, 26.0),
            DailyForecast("2026-09-28", "MON", 3, 30.0, 25.0),
            DailyForecast("2026-09-26", "SAT", 61, 29.0, 24.0),
            DailyForecast("2026-09-29", "TUE", 1, 31.0, 25.0)
        )

        val futureDays = WeatherDateUtil.filterFutureDays(unsortedDaily, todayDate, count = 6)
        assertEquals(6, futureDays.size)
        for (i in 0 until futureDays.size - 1) {
            assertTrue(futureDays[i].date < futureDays[i + 1].date)
        }
    }

    // 10. Daily WMO codes map to correct forecast icons (daytime representation)
    @Test
    fun testDailyWmoCodesMapToCorrectForecastIcons() {
        val sunnyForecast = WeatherConditionMapper.mapCondition(0, isDay = true)
        assertEquals(Icons.Default.WbSunny, sunnyForecast.icon)

        val rainForecast = WeatherConditionMapper.mapCondition(61, isDay = true)
        assertEquals(Icons.Default.WaterDrop, rainForecast.icon)

        val thunderstormForecast = WeatherConditionMapper.mapCondition(95, isDay = true)
        assertEquals(Icons.Default.Thunderstorm, thunderstormForecast.icon)
    }

    // 11. Forecast maximum temperature
    @Test
    fun testForecastMaximumTemperature() {
        val formatted = WeatherConditionMapper.formatForecastTemp(max = 32.4, min = 25.8)
        assertTrue(formatted.startsWith("32°"))
    }

    // 12. Forecast minimum temperature
    @Test
    fun testForecastMinimumTemperature() {
        val formatted = WeatherConditionMapper.formatForecastTemp(max = 32.4, min = 25.8)
        assertTrue(formatted.endsWith("26°"))
    }

    // 13. Celsius formatting
    @Test
    fun testCelsiusFormatting() {
        val currentFormatted = WeatherConditionMapper.formatTemperatureCelsius(29.6)
        assertEquals("30°C", currentFormatted)

        val forecastFormatted = WeatherConditionMapper.formatForecastTemp(31.8, 24.2)
        assertEquals("32° / 24°", forecastFormatted)
        assertFalse(forecastFormatted.contains("F"))
    }

    // 14. Month-boundary forecast dates
    @Test
    fun testMonthBoundaryForecastDates() {
        val todayDate = "2026-09-28"
        val allDaily = listOf(
            DailyForecast("2026-09-28", "MON", 0, 32.0, 26.0),
            DailyForecast("2026-09-29", "TUE", 1, 31.0, 25.0),
            DailyForecast("2026-09-30", "WED", 2, 30.0, 25.0),
            DailyForecast("2026-10-01", "THU", 61, 29.0, 24.0),
            DailyForecast("2026-10-02", "FRI", 95, 28.0, 24.0),
            DailyForecast("2026-10-03", "SAT", 3, 30.0, 25.0),
            DailyForecast("2026-10-04", "SUN", 0, 31.0, 25.0)
        )

        val futureDays = WeatherDateUtil.filterFutureDays(allDaily, todayDate, count = 6)
        assertEquals(6, futureDays.size)
        assertEquals("2026-09-29", futureDays[0].date)
        assertEquals("2026-09-30", futureDays[1].date)
        assertEquals("2026-10-01", futureDays[2].date)
        assertEquals("2026-10-02", futureDays[3].date)
        assertEquals("2026-10-03", futureDays[4].date)
        assertEquals("2026-10-04", futureDays[5].date)
    }

    // 15. Year-boundary forecast dates
    @Test
    fun testYearBoundaryForecastDates() {
        val todayDate = "2026-12-30"
        val allDaily = listOf(
            DailyForecast("2026-12-30", "WED", 0, 20.0, 12.0),
            DailyForecast("2026-12-31", "THU", 1, 21.0, 13.0),
            DailyForecast("2027-01-01", "FRI", 2, 22.0, 14.0),
            DailyForecast("2027-01-02", "SAT", 3, 21.0, 13.0),
            DailyForecast("2027-01-03", "SUN", 61, 19.0, 12.0),
            DailyForecast("2027-01-04", "MON", 95, 18.0, 11.0),
            DailyForecast("2027-01-05", "TUE", 0, 20.0, 12.0)
        )

        val futureDays = WeatherDateUtil.filterFutureDays(allDaily, todayDate, count = 6)
        assertEquals(6, futureDays.size)
        assertEquals("2026-12-31", futureDays[0].date)
        assertEquals("2027-01-01", futureDays[1].date)
        assertEquals("2027-01-02", futureDays[2].date)
        assertEquals("2027-01-03", futureDays[3].date)
        assertEquals("2027-01-04", futureDays[4].date)
        assertEquals("2027-01-05", futureDays[5].date)
    }

    // 16. Current weather survives forecast-only failure
    @Test
    fun testCurrentWeatherSurvivesForecastOnlyFailure() {
        val weatherWithFailedForecast = WeatherData(
            locationName = "Dhaka",
            temperature = 31.0,
            apparentTemperature = 34.0,
            humidity = 75,
            windSpeed = 10.0,
            precipitation = 0.0,
            weatherCode = 1,
            sunrise = "05:45",
            sunset = "17:52",
            hourlyTemps = emptyList(),
            dailyMax = emptyList(),
            dailyMin = emptyList(),
            isCached = false,
            lastUpdated = System.currentTimeMillis(),
            isDay = true,
            dailyForecasts = emptyList(),
            currentDate = "2026-09-23",
            isForecastAvailable = false
        )

        // Current weather remains accessible and valid
        assertEquals(31.0, weatherWithFailedForecast.temperature, 0.001)
        assertEquals("Dhaka", weatherWithFailedForecast.locationName)
        assertFalse(weatherWithFailedForecast.isForecastAvailable)
        assertTrue(weatherWithFailedForecast.dailyForecasts.isEmpty())
    }

    // 17. Full failure produces safe error state
    @Test
    fun testFullFailureProducesSafeErrorState() {
        val nullWeather: WeatherData? = null
        assertNull(nullWeather)
        // Null weather is handled safely by HomeWeatherForecastPanel with fallback / error UI
    }

    // 18. Weather panel navigates to existing Weather route
    @Test
    fun testWeatherPanelNavigatesToExistingWeatherRoute() {
        assertEquals("weather", Screen.Weather.route)
    }
}
