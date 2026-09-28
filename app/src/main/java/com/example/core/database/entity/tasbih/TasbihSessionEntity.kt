package com.example.core.database.entity.tasbih

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasbih_sessions")
data class TasbihSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val count: Int,
    val target: Int,
    val lastUpdated: Long
)
