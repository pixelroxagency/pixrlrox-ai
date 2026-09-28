package com.example.data.downloader.extractor

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

data class SafeHttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val body: String,
    val headers: Headers
)

object SafeHttpHelper {

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    suspend fun executeWithSafeRedirects(
        client: OkHttpClient,
        initialUrl: String,
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = 5
    ): SafeHttpResponse = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl.trim()
        var redirectsRemaining = maxRedirects

        while (true) {
            UrlSecurityValidator.validateUrl(currentUrl)

            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)
                .header("Accept", headers["Accept"] ?: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", headers["Accept-Language"] ?: "en-US,en;q=0.9")

            headers.forEach { (k, v) ->
                if (k != "User-Agent" && k != "Accept" && k != "Accept-Language") {
                    reqBuilder.header(k, v)
                }
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val code = response.code

            DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                current.copy(
                    httpStatus = code.toString(),
                    redirectCount = (maxRedirects - redirectsRemaining).toString(),
                    responseContentType = response.header("Content-Type") ?: "N/A"
                )
            }

            if (code in 300..399) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank() || redirectsRemaining <= 0) {
                    throw Exception("Too many redirects or missing Location header (HTTP $code).")
                }
                redirectsRemaining--

                val resolvedUrl = resolveRedirectTarget(currentUrl, location)
                UrlSecurityValidator.validateUrl(resolvedUrl)
                currentUrl = resolvedUrl
            } else {
                val body = response.body?.string() ?: ""
                return@withContext SafeHttpResponse(
                    statusCode = code,
                    finalUrl = currentUrl,
                    body = body,
                    headers = response.headers
                )
            }
        }
        throw Exception("Maximum redirect limit reached.")
    }

    fun resolveRedirectTarget(baseUrl: String, location: String): String {
        val trimmedLoc = location.trim()
        if (trimmedLoc.startsWith("http://", ignoreCase = true) || trimmedLoc.startsWith("https://", ignoreCase = true)) {
            return trimmedLoc
        }
        val baseUri = Uri.parse(baseUrl)
        val scheme = baseUri.scheme ?: "https"
        val host = baseUri.host ?: ""
        val portStr = if (baseUri.port != -1 && baseUri.port != 80 && baseUri.port != 443) ":${baseUri.port}" else ""

        return if (trimmedLoc.startsWith("/")) {
            "$scheme://$host$portStr$trimmedLoc"
        } else {
            val basePath = baseUri.path?.substringBeforeLast("/", "") ?: ""
            "$scheme://$host$portStr$basePath/$trimmedLoc"
        }
    }

    fun unescapeHtmlAndJson(str: String): String {
        return str
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0022", "\"")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
