package com.example.data.downloader.extractor

import android.content.Context
import android.net.Uri

open class InstagramYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override val platformName: String = "Instagram"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        val isInstaHost = host == "instagram.com" || host.endsWith(".instagram.com")
        if (!isInstaHost) return false

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }

        val path = uri.path?.lowercase() ?: ""
        return path.contains("/reel/") || path.contains("/reels/") ||
                path.contains("/p/") || path.contains("/tv/") ||
                path.startsWith("/reel") || path.startsWith("/reels") ||
                path.startsWith("/p") || path.startsWith("/tv")
    }
}
