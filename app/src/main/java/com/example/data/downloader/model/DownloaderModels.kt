package com.example.data.downloader.model

enum class MediaType {
    VIDEO,
    AUDIO,
    FILE
}

enum class TransferEngine {
    DOWNLOAD_MANAGER,
    YTDLP
}

data class DownloadFormat(
    val id: String,
    val mediaType: MediaType,
    val resolution: String = "", // e.g. "1080p", "720p", "480p", "360p", "240p"
    val container: String = "", // e.g. "MP4", "WebM", "MP3", "AAC", "M4A"
    val codec: String = "", // e.g. "H.264", "AAC", "VP9"
    val vcodec: String = "",
    val acodec: String = "",
    val bitrate: Long = 0L, // bits per second
    val sizeBytes: Long = 0L, // in bytes (0 if unknown)
    val downloadUrl: String,
    val audioIncluded: Boolean = true,
    val mergeRequired: Boolean = false,
    val separateAudioAvailable: Boolean = false,
    val label: String = "",
    val httpHeaders: Map<String, String>? = null,
    val transferEngine: TransferEngine = TransferEngine.DOWNLOAD_MANAGER
) {
    fun getFormattedSize(): String {
        if (sizeBytes <= 0L) return "Unknown size"
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$sizeBytes B"
        }
    }

    fun getFormattedBitrate(): String {
        if (bitrate <= 0L) return ""
        val kbps = bitrate / 1000
        return "$kbps kbps"
    }

    fun getDisplayName(): String {
        if (label.isNotBlank()) return label
        val parts = mutableListOf<String>()
        if (resolution.isNotBlank()) parts.add(resolution)
        if (container.isNotBlank()) parts.add(container.uppercase())
        if (bitrate > 0L) parts.add(getFormattedBitrate())
        if (sizeBytes > 0L) parts.add(getFormattedSize())
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Default Format"
    }
}

data class MediaAnalysisResult(
    val url: String,
    val title: String,
    val thumbnailUri: String? = null,
    val source: String,
    val formats: List<DownloadFormat>,
    val isDownloadable: Boolean = true,
    val durationSeconds: Double? = null
)

sealed class AnalysisState {
    data object Idle : AnalysisState()
    data object Analyzing : AnalysisState()
    data class Success(val result: MediaAnalysisResult) : AnalysisState()
    data class DirectMedia(val result: MediaAnalysisResult) : AnalysisState()
    data class PublicMediaPage(val message: String, val url: String) : AnalysisState()
    data class ResolverRequired(val message: String) : AnalysisState()
    data class AuthRequired(val message: String) : AnalysisState()
    data class PrivateOrRestricted(val message: String) : AnalysisState()
    data class DrmProtected(val message: String) : AnalysisState()
    data class Unsupported(val message: String) : AnalysisState()
    data class NetworkError(val message: String) : AnalysisState()
    data class ExtractionFailed(val message: String) : AnalysisState()
    data class InvalidUrl(val message: String) : AnalysisState()
    data class DownloadUnavailable(val message: String) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}
