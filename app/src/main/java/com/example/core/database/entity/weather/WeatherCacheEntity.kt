package com.example.core.database.entity.weather

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val locationName: String,
    val jsonPayload: String,
    val timestamp: Long
)
