package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_entries")
data class ClipboardEntryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val type: String, // Text, URL, Email, Phone
    val timestamp: Long
)
