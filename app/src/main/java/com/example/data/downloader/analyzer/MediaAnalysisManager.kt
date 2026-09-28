package com.example.data.downloader.analyzer

import android.content.Context
import android.net.Uri
import com.example.data.downloader.extractor.FacebookPublicExtractor
import com.example.data.downloader.extractor.InstagramPublicExtractor
import com.example.data.downloader.extractor.TikTokPublicExtractor
import com.example.data.downloader.extractor.TikTokYtDlpExtractor
import com.example.data.downloader.extractor.UniversalYtDlpExtractor
import com.example.data.downloader.extractor.YtDlpInitializer
import com.example.data.downloader.extractor.YouTubeYtDlpExtractor
import com.example.data.downloader.model.MediaAnalysisResult
import java.net.MalformedURLException

class MediaAnalysisManager(
    context: Context? = null,
    private val analyzers: List<MediaSourceAnalyzer> = listOf(
        DirectMediaAnalyzer(),
        HlsMediaAnalyzer(),
        OnDeviceSocialMediaAnalyzer(
            extractors = listOf(
                YouTubeYtDlpExtractor(context),
                TikTokPublicExtractor(context),
                InstagramPublicExtractor(context),
                FacebookPublicExtractor(context),
                UniversalYtDlpExtractor(context)
            )
        ),
        SocialProtectionDetectorAnalyzer()
    )
) {
    init {
        context?.let { YtDlpInitializer.registerContext(it) }
    }

    suspend fun analyze(rawUrl: String): MediaAnalysisResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Please enter a valid media link.")
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            throw MalformedURLException("Invalid URL format.")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Only HTTP and HTTPS URLs are supported.")
        }

        if (uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("The link does not have a valid web domain.")
        }

        // Check analyzers ordered by priority
        val sortedAnalyzers = analyzers.sortedByDescending { it.priority }
        for (analyzer in sortedAnalyzers) {
            if (analyzer.canHandle(trimmed)) {
                val analyzerName = analyzer.javaClass.simpleName
                com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        extractorSelected = analyzerName,
                        extractorReached = analyzerName
                    )
                }
                return analyzer.analyze(trimmed)
            }
        }

        throw Exception("Unsupported source: No compatible analyzer found for this link.")
    }
}
