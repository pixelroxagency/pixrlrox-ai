package com.example.data.downloader.extractor

import android.content.Context
import android.net.Uri

class YouTubeYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override val platformName: String = "YouTube"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        val isYoutubeHost = host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
        if (!isYoutubeHost) return false

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }

        val path = uri.path?.lowercase() ?: ""
        if (host == "youtu.be") {
            return path.isNotBlank() && path != "/"
        }

        return path.contains("/watch") || path.contains("/shorts") ||
                path.contains("/live") || path.contains("/embed/") ||
                path.contains("/v/") || path.contains("/attribution_link")
    }
}
