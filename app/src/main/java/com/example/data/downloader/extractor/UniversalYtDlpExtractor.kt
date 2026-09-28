package com.example.data.downloader.extractor

import android.content.Context
import android.net.Uri

open class UniversalYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override var platformName: String = "Universal"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        if (host.isBlank()) return false

        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "http" && scheme != "https") return false

        // Exclude direct media files handled by DirectMediaAnalyzer / HlsMediaAnalyzer
        val path = try { Uri.parse(url).path?.lowercase() ?: "" } catch (_: Exception) { "" }
        val isDirectFile = path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") ||
                path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".wav") ||
                path.endsWith(".ogg") || path.endsWith(".mov") || path.endsWith(".mkv") ||
                path.endsWith(".pdf") || path.endsWith(".zip") || path.endsWith(".apk")
        if (isDirectFile) return false

        return true
    }
}
