package com.example.data.downloader.extractor

import android.content.Context
import com.example.data.downloader.analyzer.AuthRequiredException
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import okhttp3.OkHttpClient
import java.util.UUID

class FacebookPublicExtractor(
    context: Context? = null,
    private val ytDlpExtractor: FacebookYtDlpExtractor = FacebookYtDlpExtractor(context)
) : PlatformExtractor {

    override val platformName: String = "Facebook"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        val isFbHost = host == "facebook.com" || host.endsWith(".facebook.com") ||
                host == "fb.watch" || host == "m.facebook.com" ||
                host == "fb.com" || host.endsWith(".fb.com")
        if (!isFbHost) return false

        val lower = url.lowercase().substringBefore('?').substringBefore('#')
        if (host == "fb.watch") return true
        if (url.contains("/watch/?v=") || url.contains("/watch?v=")) return true

        return lower.contains("/reel/") || lower.contains("/reels/") ||
                lower.contains("/videos/") || lower.contains("/watch") ||
                lower.contains("/posts/") || lower.contains("/share/")
    }

    override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                extractorSelected = "FacebookPublicExtractor",
                extractorReached = "FacebookPublicExtractor"
            )
        }
        DownloaderDiagnosticsRegistry.recordAttempt("FacebookPublicExtractor", "STARTED")

        // 1. Attempt lightweight extraction first
        var lightweightError: PublicMediaPageException? = null
        try {
            val result = extractInternal(url, httpClient)
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookPublicExtractor", "SUCCESS")
            return result
        } catch (e: AuthRequiredException) {
            throw e
        } catch (e: PrivateOrRestrictedException) {
            throw e
        } catch (e: PublicMediaPageException) {
            lightweightError = e
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookPublicExtractor", "TEMPORARY_EXTRACTION_FAILURE")
            YtDlpDiagnostics.log("FACEBOOK_LIGHTWEIGHT_FAILED: ${e.message}")
        } catch (e: Exception) {
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookPublicExtractor", "TEMPORARY_EXTRACTION_FAILURE")
            YtDlpDiagnostics.log("FACEBOOK_LIGHTWEIGHT_FAILED: ${e.message}")
        }

        // 2. Fallback to yt-dlp
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                ytDlpFallbackInvoked = "YES",
                extractorSelected = "FacebookYtDlpExtractor",
                extractorReached = "FacebookYtDlpExtractor"
            )
        }

        return try {
            val ytResult = ytDlpExtractor.extract(url, httpClient)
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookYtDlpExtractor", "SUCCESS")
            ytResult
        } catch (e: com.example.data.downloader.analyzer.ExtractionFailedException) {
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookYtDlpExtractor", "EXTRACTOR_INIT_FAILED")
            if (lightweightError != null && !YtDlpInitializer.isReady()) {
                throw lightweightError
            }
            throw e
        } catch (e: Exception) {
            DownloaderDiagnosticsRegistry.recordAttempt("FacebookYtDlpExtractor", "FAILED")
            throw e
        }
    }

    private suspend fun extractInternal(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        val response = SafeHttpHelper.executeWithSafeRedirects(
            client = httpClient,
            initialUrl = url
        )

        val html = response.body
        val finalUrl = response.finalUrl

        // 1. Detect login requirement
        if (finalUrl.contains("/login.php") || finalUrl.contains("checkpoint") ||
            html.contains("You must log in to continue") || html.contains("<title>Log in to Facebook</title>")
        ) {
            throw AuthRequiredException(platformName, "This Facebook video requires login or isn't publicly accessible.")
        }

        // 2. Detect restricted or deleted content
        if (html.contains("This content isn't available right now") || html.contains("This video may have been deleted")) {
            throw PrivateOrRestrictedException(platformName, "This Facebook video is private or restricted by the owner.")
        }

        var title: String? = null
        var thumbnail: String? = null
        var hdVideoUrl: String? = null
        var sdVideoUrl: String? = null

        // Strategy A: OpenGraph meta tags
        val ogVideo = extractMetaTag(html, "og:video") ?: extractMetaTag(html, "og:video:secure_url") ?: extractMetaTag(html, "og:video:url")
        if (!ogVideo.isNullOrBlank()) {
            hdVideoUrl = ogVideo
        }

        title = extractMetaTag(html, "og:title")
        thumbnail = extractMetaTag(html, "og:image")

        // Strategy B: Embedded script JSON search
        val hdRegex = Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)"""")
        val sdRegex = Regex(""""playable_url"\s*:\s*"([^"]+)"""")
        val nativeHdRegex = Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)"""")
        val nativeSdRegex = Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)"""")

        val foundHd = hdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
            ?: nativeHdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
        val foundSd = sdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
            ?: nativeSdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }

        if (!foundHd.isNullOrBlank()) {
            hdVideoUrl = foundHd
        }
        if (!foundSd.isNullOrBlank()) {
            sdVideoUrl = foundSd
        }

        if (hdVideoUrl.isNullOrBlank() && sdVideoUrl.isNullOrBlank()) {
            throw PublicMediaPageException(platformName, url, "This Facebook video couldn't be resolved from its public page.")
        }

        val formats = mutableListOf<DownloadFormat>()

        if (!hdVideoUrl.isNullOrBlank()) {
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.VIDEO,
                    resolution = "HD",
                    container = "MP4",
                    codec = "H.264",
                    sizeBytes = 0L,
                    downloadUrl = hdVideoUrl,
                    audioIncluded = true,
                    label = "HD • MP4"
                )
            )
        }

        if (!sdVideoUrl.isNullOrBlank() && sdVideoUrl != hdVideoUrl) {
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.VIDEO,
                    resolution = "SD",
                    container = "MP4",
                    codec = "H.264",
                    sizeBytes = 0L,
                    downloadUrl = sdVideoUrl,
                    audioIncluded = true,
                    label = "SD • MP4"
                )
            )
        }

        val sanitizedTitle = UrlSecurityValidator.sanitizeFilename(
            title ?: "Facebook Video",
            fallback = "Facebook_Video"
        )

        return MediaAnalysisResult(
            url = finalUrl,
            title = sanitizedTitle,
            thumbnailUri = thumbnail?.let { SafeHttpHelper.unescapeHtmlAndJson(it) },
            source = "Facebook",
            formats = formats,
            isDownloadable = true
        )
    }

    private fun extractMetaTag(html: String, property: String): String? {
        val pattern = Regex("""<meta\s+[^>]*property=["']$property["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = pattern.find(html) ?: run {
            val altPattern = Regex("""<meta\s+[^>]*content=["']([^"']+)["'][^>]*property=["']$property["']""", RegexOption.IGNORE_CASE)
            altPattern.find(html)
        }
        return match?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
    }
}
