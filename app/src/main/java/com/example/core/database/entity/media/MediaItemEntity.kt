package com.example.core.database.entity.media
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val duration: Long,
    val lastPlayed: Long,
    val lastPosition: Long,
    val completed: Boolean,
    val mediaType: String
)
