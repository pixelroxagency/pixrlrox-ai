package com.example.core.database.entity.website

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "managed_websites")
data class WebsiteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val domain: String,
    val url: String,
    val environment: String = "Production", // "Production", "Staging", "Internal Service", "Client Website"
    val notes: String = "",
    val isMonitoringEnabled: Boolean = true,
    val clientId: String? = null, // Links to ClientEntity (#114)
    val lastCheckTime: Long = 0L,
    val isOnline: Boolean = true,
    val httpStatus: Int = 200,
    val responseTimeMs: Long = 0L,
    val sslValid: Boolean = true,
    val sslExpiryDate: Long = 0L,
    val sslIssuer: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
