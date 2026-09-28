package com.example.core.database.entity.vault
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    val encryptedPassword: ByteArray,
    val notes: String
)
