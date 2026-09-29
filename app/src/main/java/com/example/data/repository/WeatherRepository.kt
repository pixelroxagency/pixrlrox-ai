package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.core.database.dao.weather.WeatherDao
import com.example.core.database.entity.weather.WeatherCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import com.example.core.weather.WeatherConditionMapper
import com.example.core.weather.WeatherDateUtil

data class DailyForecast(
    val date: String,
    val dayOfWeek: String,
    val weatherCode: Int,
    val maxTemp: Double,
    val minTemp: Double,
    val precipitationProbability: Int? = null
)

data class WeatherData(
    val locationName: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val weatherCode: Int,
    val sunrise: String,
    val sunset: String,
    val hourlyTemps: List<Double>,
    val dailyMax: List<Double>,
    val dailyMin: List<Double>,
    val isCached: Boolean,
    val lastUpdated: Long,
    val isDay: Boolean = true,
    val dailyForecasts: List<DailyForecast> = emptyList(),
    val currentDate: String = "",
    val isForecastAvailable: Boolean = true
)

class WeatherRepository(
    private val context: Context,
    private val weatherDao: WeatherDao
) {
    private val client = OkHttpClient()

    suspend fun fetchWeather(locationName: String, lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,weather_code,precipitation_probability_max&timezone=auto"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonString = response.body!!.string()
                weatherDao.insertWeatherCache(
                    WeatherCacheEntity(
                        locationName = locationName,
                        jsonPayload = jsonString,
                        timestamp = System.currentTimeMillis()
                    )
                )
                parseWeatherJson(locationName, jsonString, false, System.currentTimeMillis())
            } else {
                getCachedWeather(locationName) ?: throw Exception("Network error and no cache available")
            }
        } catch (e: Exception) {
            getCachedWeather(locationName) ?: throw e
        }
    }

    suspend fun searchLocation(query: String): List<Pair<String, Pair<Double, Double>>> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${android.net.Uri.encode(query)}&count=5"
        val results = mutableListOf<Pair<String, Pair<Double, Double>>>()
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val json = JSONObject(response.body!!.string())
                val arr = json.optJSONArray("results")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name", "Unknown")
                        val country = obj.optString("country", "")
                        val admin1 = obj.optString("admin1", "")
                        val fullName = listOf(name, admin1, country).filter { it.isNotBlank() }.joinToString(", ")
                        val lat = obj.optDouble("latitude", 0.0)
                        val lon = obj.optDouble("longitude", 0.0)
                        results.add(fullName to (lat to lon))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Failed to search location: $query", e)
        }
        if (results.isEmpty()) {
            results.add("Dhaka, Bangladesh" to (23.8103 to 90.4125))
            results.add("London, United Kingdom" to (51.5074 to -0.1278))
            results.add("New York, United States" to (40.7128 to -74.0060))
        }
        results
    }

    suspend fun getCachedWeather(locationName: String): WeatherData? {
        val cache = weatherDao.getWeatherCache(locationName) ?: return null
        return try {
            parseWeatherJson(locationName, cache.jsonPayload, true, cache.timestamp)
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Failed to parse cached weather data for $locationName", e)
            null
        }
    }

    private fun parseWeatherJson(locationName: String, jsonString: String, isCached: Boolean, timestamp: Long): WeatherData {
        val json = JSONObject(jsonString)
        val current = json.getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val apparentTemp = current.getDouble("apparent_temperature")
        val humidity = current.getInt("relative_humidity_2m")
        val windSpeed = current.getDouble("wind_speed_10m")
        val precipitation = current.getDouble("precipitation")
        val weatherCode = current.getInt("weather_code")
        val isDayInt = current.optInt("is_day", -1)
        val currentTimeStr = current.optString("time", "")
        val currentDate = if (currentTimeStr.contains("T")) currentTimeStr.substringBefore("T") else currentTimeStr

        val daily = json.optJSONObject("daily")
        val sunrises = daily?.optJSONArray("sunrise")
        val sunsets = daily?.optJSONArray("sunset")
        val sunrise = if (sunrises != null && sunrises.length() > 0) sunrises.getString(0).split("T").getOrElse(1) { sunrises.getString(0) } else "--"
        val sunset = if (sunsets != null && sunsets.length() > 0) sunsets.getString(0).split("T").getOrElse(1) { sunsets.getString(0) } else "--"

        val isDay = WeatherConditionMapper.isDaytime(
            isDayField = if (isDayInt != -1) isDayInt else null,
            sunrise = sunrise,
            sunset = sunset,
            currentTimeMillis = System.currentTimeMillis()
        )

        val hourly = json.optJSONObject("hourly")
        val hourlyTempsArr = hourly?.optJSONArray("temperature_2m")
        val hourlyTemps = mutableListOf<Double>()
        if (hourlyTempsArr != null) {
            for (i in 0 until minOf(24, hourlyTempsArr.length())) {
                hourlyTemps.add(hourlyTempsArr.getDouble(i))
            }
        }

        val dailyMax = mutableListOf<Double>()
        val dailyMin = mutableListOf<Double>()
        val dailyForecasts = mutableListOf<DailyForecast>()
        var isForecastAvailable = false

        if (daily != null) {
            val maxArr = daily.optJSONArray("temperature_2m_max")
            val minArr = daily.optJSONArray("temperature_2m_min")
            if (maxArr != null && minArr != null) {
                for (i in 0 until minOf(7, maxArr.length())) {
                    dailyMax.add(maxArr.getDouble(i))
                    dailyMin.add(minArr.getDouble(i))
                }
            }

            try {
                val timesArr = daily.optJSONArray("time")
                val codesArr = daily.optJSONArray("weather_code")
                val precipProbArr = daily.optJSONArray("precipitation_probability_max")

                if (timesArr != null && maxArr != null && minArr != null) {
                    val count = minOf(timesArr.length(), maxArr.length(), minArr.length())
                    val allDaily = mutableListOf<DailyForecast>()
                    for (i in 0 until count) {
                        val dateStr = timesArr.getString(i)
                        val max = maxArr.getDouble(i)
                        val min = minArr.getDouble(i)
                        val code = codesArr?.optInt(i, 0) ?: 0
                        val precipProb = if (precipProbArr != null && !precipProbArr.isNull(i)) precipProbArr.optInt(i) else null
                        val dayOfWeek = WeatherDateUtil.getDayOfWeekAbbreviation(dateStr)

                        allDaily.add(
                            DailyForecast(
                                date = dateStr,
                                dayOfWeek = dayOfWeek,
                                weatherCode = code,
                                maxTemp = max,
                                minTemp = min,
                                precipitationProbability = precipProb
                            )
                        )
                    }

                    val futureDays = WeatherDateUtil.filterFutureDays(allDaily, currentDate, 6)
                    dailyForecasts.addAll(futureDays)
                    isForecastAvailable = dailyForecasts.isNotEmpty()
                }
            } catch (e: Exception) {
                Log.w("WeatherRepository", "Failed to parse daily forecast, preserving current weather: ${e.message}")
                isForecastAvailable = false
            }
        }

        return WeatherData(
            locationName = locationName,
            temperature = temp,
            apparentTemperature = apparentTemp,
            humidity = humidity,
            windSpeed = windSpeed,
            precipitation = precipitation,
            weatherCode = weatherCode,
            sunrise = sunrise,
            sunset = sunset,
            hourlyTemps = hourlyTemps,
            dailyMax = dailyMax,
            dailyMin = dailyMin,
            isCached = isCached,
            lastUpdated = timestamp,
            isDay = isDay,
            dailyForecasts = dailyForecasts,
            currentDate = currentDate,
            isForecastAvailable = isForecastAvailable
        )
    }
}
