package com.example.core.database.entity.news

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_cache")
data class NewsCacheEntity(
    @PrimaryKey val category: String,
    val jsonPayload: String,
    val timestamp: Long
)
