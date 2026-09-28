package com.example.core.database.entity.checklist
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long
)
