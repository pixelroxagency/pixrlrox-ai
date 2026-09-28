package com.example.data.downloader.extractor

import android.content.Context
import android.net.Uri
import com.example.data.downloader.analyzer.AuthRequiredException
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import okhttp3.OkHttpClient
import java.util.UUID

class InstagramPublicExtractor(
    context: Context? = null,
    private val ytDlpExtractor: InstagramYtDlpExtractor = InstagramYtDlpExtractor(context)
) : PlatformExtractor {

    constructor(ytDlpExtractor: InstagramYtDlpExtractor) : this(null, ytDlpExtractor)

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

    override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                extractorSelected = "InstagramPublicExtractor",
                extractorReached = "InstagramPublicExtractor"
            )
        }
        DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "STARTED")

        var lightweightError: Exception? = null

        // 1. Attempt lightweight extraction first
        try {
            val lightweightResult = extractLightweight(url, httpClient)
            if (lightweightResult.formats.isNotEmpty()) {
                DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "SUCCESS")
                return lightweightResult
            } else {
                DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "NO_USABLE_MEDIA")
            }
        } catch (e: AuthRequiredException) {
            lightweightError = e
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "LOGIN_OR_VERIFICATION_PAGE")
            YtDlpDiagnostics.log("INSTAGRAM_LIGHTWEIGHT_LOGIN_PAGE: ${e.message}")
        } catch (e: PrivateOrRestrictedException) {
            lightweightError = e
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "LOGIN_OR_VERIFICATION_PAGE")
            YtDlpDiagnostics.log("INSTAGRAM_LIGHTWEIGHT_RESTRICTED: ${e.message}")
        } catch (e: PublicMediaPageException) {
            lightweightError = e
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "TEMPORARY_EXTRACTION_FAILURE")
            YtDlpDiagnostics.log("INSTAGRAM_LIGHTWEIGHT_FAILED: ${e.message}")
        } catch (e: Exception) {
            lightweightError = e
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramPublicExtractor", "FAILED")
            YtDlpDiagnostics.log("INSTAGRAM_LIGHTWEIGHT_FAILED: ${e.message}")
        }

        // 2. Fallback to yt-dlp extractor
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                ytDlpFallbackInvoked = "YES",
                extractorSelected = "InstagramYtDlpExtractor",
                extractorReached = "InstagramYtDlpExtractor"
            )
        }
        DownloaderDiagnosticsRegistry.recordAttempt("InstagramYtDlpExtractor", "STARTED")

        return try {
            val ytResult = ytDlpExtractor.extract(url, httpClient)
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramYtDlpExtractor", "SUCCESS")
            ytResult
        } catch (e: ExtractionFailedException) {
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramYtDlpExtractor", "EXTRACTOR_INIT_FAILED")
            if (lightweightError != null && !YtDlpInitializer.isReady()) {
                throw lightweightError
            }
            throw e
        } catch (e: Exception) {
            DownloaderDiagnosticsRegistry.recordAttempt("InstagramYtDlpExtractor", "FAILED")
            throw e
        }
    }

    private suspend fun extractLightweight(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        val canonicalUrl = canonicalizeInstagramUrl(url)

        val response = SafeHttpHelper.executeWithSafeRedirects(
            client = httpClient,
            initialUrl = canonicalUrl
        )

        val html = response.body
        val finalUrl = response.finalUrl

        // 1. Detect login requirement
        if (finalUrl.contains("/accounts/login/") || html.contains("<title>Login • Instagram</title>") || html.contains("login_required")) {
            throw AuthRequiredException(platformName, "This Instagram post requires login or isn't publicly accessible.")
        }

        // 2. Detect restricted or deleted post
        if (html.contains("Sorry, this page isn't available") || html.contains("The link you followed may be broken") || html.contains("Restricted profile")) {
            throw PrivateOrRestrictedException(platformName, "This Instagram post is private or restricted and cannot be downloaded without authentication.")
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
        val hdRegex = Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)"""")
        val sdRegex = Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)"""")
        val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
        val playableUrlRegex = Regex(""""playable_url"\s*:\s*"([^"]+)"""")

        val foundHd = hdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
        val foundSd = sdRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
        val foundVideoUrl = videoUrlRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
        val foundPlayable = playableUrlRegex.find(html)?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }

        if (!foundHd.isNullOrBlank()) {
            hdVideoUrl = foundHd
        }
        if (!foundSd.isNullOrBlank()) {
            sdVideoUrl = foundSd
        }
        if (hdVideoUrl.isNullOrBlank() && !foundVideoUrl.isNullOrBlank()) {
            hdVideoUrl = foundVideoUrl
        }
        if (hdVideoUrl.isNullOrBlank() && !foundPlayable.isNullOrBlank()) {
            hdVideoUrl = foundPlayable
        }

        // If no video URL was found anywhere in public HTML, it's either an image carousel or requires auth
        if (hdVideoUrl.isNullOrBlank() && sdVideoUrl.isNullOrBlank()) {
            throw AuthRequiredException(platformName, "This Instagram post requires login or isn't publicly accessible.")
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

        // Clean user title
        val cleanedTitle = title
            ?.replace(Regex("""^Instagram\s+(video|photo|reel)\s+by\s+""", RegexOption.IGNORE_CASE), "")
            ?.takeIf { it.isNotBlank() } ?: "Instagram_Reel"

        val sanitizedTitle = UrlSecurityValidator.sanitizeFilename(cleanedTitle, fallback = "Instagram_Reel")

        return MediaAnalysisResult(
            url = canonicalUrl,
            title = sanitizedTitle,
            thumbnailUri = thumbnail?.let { SafeHttpHelper.unescapeHtmlAndJson(it) },
            source = "Instagram",
            formats = formats,
            isDownloadable = true
        )
    }

    private fun canonicalizeInstagramUrl(rawUrl: String): String {
        val clean = rawUrl.substringBefore('?').substringBefore('#').trim()
        val host = UrlSecurityValidator.parseHost(clean) ?: "www.instagram.com"
        val path = clean.substringAfter("://$host", "").trim('/')
        val segments = path.split('/')
        val typeIdx = segments.indexOfFirst { it == "reel" || it == "reels" || it == "p" || it == "tv" }
        return if (typeIdx != -1 && typeIdx + 1 < segments.size) {
            val type = if (segments[typeIdx] == "reels") "reel" else segments[typeIdx]
            val shortcode = segments[typeIdx + 1]
            "https://$host/$type/$shortcode/"
        } else {
            clean
        }
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
