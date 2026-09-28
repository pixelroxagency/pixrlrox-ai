package com.example.core.database.entity.news

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_articles")
data class SavedArticleEntity(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val publishedAt: Long,
    val savedAt: Long,
    val description: String,
    val imageUrl: String
)
