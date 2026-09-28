package com.example.core.database.entity.notes
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val pinned: Boolean,
    val archived: Boolean
)
