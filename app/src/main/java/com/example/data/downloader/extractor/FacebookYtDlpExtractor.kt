package com.example.data.downloader.extractor

import android.content.Context

class FacebookYtDlpExtractor(
    context: Context? = null
) : BaseYtDlpExtractor(context) {

    override val platformName: String = "Facebook"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        return host == "facebook.com" || host.endsWith(".facebook.com") ||
                host == "fb.watch" || host == "m.facebook.com"
    }
}
