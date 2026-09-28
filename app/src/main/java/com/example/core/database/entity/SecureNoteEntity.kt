package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secure_notes")
data class SecureNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val encryptedBody: String,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)
