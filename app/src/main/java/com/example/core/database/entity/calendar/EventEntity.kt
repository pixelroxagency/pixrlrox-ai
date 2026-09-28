package com.example.core.database.entity.calendar
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val isAllDay: Boolean
)
