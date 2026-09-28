package com.example.core.database.entity.download

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val filename: String,
    val url: String,
    val destinationUri: String,
    val status: String, // "QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED"
    val progress: Int,
    val fileSize: Long,
    val timestamp: Long,
    val downloadManagerId: Long = -1L,
    val mediaType: String = "VIDEO", // "VIDEO", "AUDIO", "STREAM", "FILE"
    val quality: String = "", // e.g. "1080p", "720p", "320 kbps"
    val format: String = "", // e.g. "MP4", "MP3", "AAC", "WebM"
    val thumbnailUri: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val sourceUrl: String = "",
    val completedTimestamp: Long? = null,
    val transferEngine: String = "DOWNLOAD_MANAGER" // "DOWNLOAD_MANAGER", "YTDLP"
)
