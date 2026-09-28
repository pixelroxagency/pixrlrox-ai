package com.example.core.database.entity.voice

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val audioPath: String,
    val transcript: String = "",
    val isTranscribed: Boolean = false
)
