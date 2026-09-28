package com.example.data.downloader.analyzer

import com.example.data.downloader.extractor.FacebookPublicExtractor
import com.example.data.downloader.extractor.InstagramPublicExtractor
import com.example.data.downloader.extractor.PlatformExtractor
import com.example.data.downloader.extractor.TikTokPublicExtractor
import com.example.data.downloader.extractor.UniversalYtDlpExtractor
import com.example.data.downloader.extractor.UrlSecurityValidator
import com.example.data.downloader.extractor.YouTubeYtDlpExtractor
import com.example.data.downloader.model.MediaAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OnDeviceSocialMediaAnalyzer(
    private val httpClient: OkHttpClient = createDefaultHttpClient(),
    private val extractors: List<PlatformExtractor> = listOf(
        YouTubeYtDlpExtractor(),
        TikTokPublicExtractor(),
        InstagramPublicExtractor(),
        FacebookPublicExtractor(),
        UniversalYtDlpExtractor()
    )
) : MediaSourceAnalyzer {

    override val priority: Int = 60

    override suspend fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        if (host.isBlank()) return false

        val lower = url.lowercase().substringBefore('?').substringBefore('#')
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") ||
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".mov") ||
            lower.endsWith(".mkv") || lower.endsWith(".pdf") || lower.endsWith(".zip")
        ) {
            return false
        }

        return extractors.any { it.canHandle(url) }
    }

    override suspend fun analyze(url: String): MediaAnalysisResult = withContext(Dispatchers.IO) {
        UrlSecurityValidator.validateUrl(url)
        val extractor = extractors.firstOrNull { it.canHandle(url) }
            ?: throw UnsupportedPlatformException("Social", "No on-device extractor supported for this platform.")

        extractor.extract(url.trim(), httpClient)
    }

    companion object {
        fun createDefaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
