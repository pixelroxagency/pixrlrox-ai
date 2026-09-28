package com.example.data.downloader.extractor

import android.content.Context
import android.net.Uri

open class ThreadsYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override val platformName: String = "Threads"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        val isThreadsHost = host == "threads.net" || host == "www.threads.net" ||
                host == "threads.com" || host == "www.threads.com" ||
                host.endsWith(".threads.net") || host.endsWith(".threads.com")
        if (!isThreadsHost) return false

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }

        val path = uri.path?.lowercase() ?: ""
        return path.contains("/post/") || path.contains("/t/") ||
                path.startsWith("/post/") || path.startsWith("/t/")
    }
}
