package com.example.data.downloader.extractor

import android.content.Context
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID

class TikTokPublicExtractor(
    context: Context? = null,
    private val ytDlpExtractor: TikTokYtDlpExtractor = TikTokYtDlpExtractor(context)
) : PlatformExtractor {

    constructor(ytDlpExtractor: TikTokYtDlpExtractor) : this(null, ytDlpExtractor)

    override val platformName: String = "TikTok"

    override fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com") ||
                host == "vt.tiktok.com" || host == "vm.tiktok.com"
    }

    override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                extractorSelected = "TikTokPublicExtractor",
                extractorReached = "TikTokPublicExtractor"
            )
        }
        // Fast path: Attempt lightweight HTML / JSON parsing on-device
        var lightweightError: PublicMediaPageException? = null
        try {
            val lightweightResult = extractLightweight(url, httpClient)
            if (lightweightResult.formats.isNotEmpty()) {
                YtDlpDiagnostics.log("LIGHTWEIGHT_EXTRACTOR_RESULT=SUCCESS, FORMATS_COUNT=${lightweightResult.formats.size}")
                DownloaderDiagnosticsRegistry.recordAttempt("TikTokPublicExtractor", "SUCCESS")
                return lightweightResult
            }
        } catch (e: PublicMediaPageException) {
            lightweightError = e
            YtDlpDiagnostics.log("LIGHTWEIGHT_EXTRACTOR_RESULT=FAILED, EXCEPTION=${e.javaClass.simpleName}")
            DownloaderDiagnosticsRegistry.recordAttempt("TikTokPublicExtractor", "TEMPORARY_EXTRACTION_FAILURE")
        } catch (e: Exception) {
            YtDlpDiagnostics.log("LIGHTWEIGHT_EXTRACTOR_RESULT=FAILED, EXCEPTION=${e.javaClass.simpleName}")
            DownloaderDiagnosticsRegistry.recordAttempt("TikTokPublicExtractor", "TEMPORARY_EXTRACTION_FAILURE")
        }

        // Robust path: On-device yt-dlp extractor
        YtDlpDiagnostics.log("YTDLP_FALLBACK_INVOKED=true")
        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                ytDlpFallbackInvoked = "YES"
            )
        }
        return try {
            DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                current.copy(
                    extractorSelected = "TikTokYtDlpExtractor",
                    extractorReached = "TikTokYtDlpExtractor"
                )
            }
            val ytResult = ytDlpExtractor.extract(url, httpClient)
            DownloaderDiagnosticsRegistry.recordAttempt("TikTokYtDlpExtractor", "SUCCESS")
            ytResult
        } catch (e: ExtractionFailedException) {
            DownloaderDiagnosticsRegistry.recordAttempt("TikTokYtDlpExtractor", "EXTRACTOR_INIT_FAILED")
            if (lightweightError != null && !YtDlpInitializer.isReady()) {
                throw lightweightError
            }
            throw e
        } catch (e: Exception) {
            DownloaderDiagnosticsRegistry.recordAttempt("TikTokYtDlpExtractor", "FAILED")
            throw e
        }
    }

    private suspend fun extractLightweight(url: String, httpClient: OkHttpClient): MediaAnalysisResult {
        val response = SafeHttpHelper.executeWithSafeRedirects(
            client = httpClient,
            initialUrl = url
        )

        val html = response.body
        val finalUrl = response.finalUrl

        if (html.isBlank()) {
            throw PublicMediaPageException(platformName, url, "This TikTok video isn't available for direct public download.")
        }

        // Check for captcha or restricted responses
        if (html.contains("verify_account") || html.contains("login_required") || html.contains("security-check")) {
            throw PublicMediaPageException(platformName, url, "This TikTok video requires verification or login and cannot be downloaded publicly.")
        }

        var title: String? = null
        var thumbnail: String? = null
        var durationSecs: Double? = null
        val videoVariants = mutableListOf<VideoVariant>()
        var audioUrl: String? = null

        // Strategy A: Parse __UNIVERSAL_DATA_FOR_REHYDRATION__
        val universalScriptMatch = Regex("""<script[^>]*id=["']__UNIVERSAL_DATA_FOR_REHYDRATION__["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).find(html)
        if (universalScriptMatch != null) {
            val jsonStr = universalScriptMatch.groupValues[1]
            try {
                val root = JSONObject(jsonStr)
                val defaultScope = root.optJSONObject("__DEFAULT_SCOPE__")
                val videoDetail = defaultScope?.optJSONObject("webapp.video-detail")
                val itemInfo = videoDetail?.optJSONObject("itemInfo")
                val itemStruct = itemInfo?.optJSONObject("itemStruct")

                if (itemStruct != null) {
                    title = itemStruct.optString("desc").takeIf { it.isNotBlank() }

                    val videoObj = itemStruct.optJSONObject("video")
                    if (videoObj != null) {
                        val dur = videoObj.optDouble("duration")
                        if (!dur.isNaN() && dur > 0) {
                            durationSecs = dur
                        }
                        thumbnail = videoObj.optString("cover").takeIf { it.isNotBlank() }
                            ?: videoObj.optString("originCover").takeIf { it.isNotBlank() }
                            ?: videoObj.optString("dynamicCover").takeIf { it.isNotBlank() }

                        val playAddr = videoObj.optString("playAddr").takeIf { it.isNotBlank() }
                        val downloadAddr = videoObj.optString("downloadAddr").takeIf { it.isNotBlank() }

                        if (!downloadAddr.isNullOrBlank()) {
                            videoVariants.add(VideoVariant(quality = "HD", url = downloadAddr, bitrate = 0))
                        }
                        if (!playAddr.isNullOrBlank() && playAddr != downloadAddr) {
                            videoVariants.add(VideoVariant(quality = "Standard", url = playAddr, bitrate = 0))
                        }

                        val bitrateInfo = videoObj.optJSONArray("bitrateInfo")
                        if (bitrateInfo != null) {
                            for (i in 0 until bitrateInfo.length()) {
                                val bObj = bitrateInfo.optJSONObject(i) ?: continue
                                val gearName = bObj.optString("GearName", "")
                                val qualityLabel = when {
                                    gearName.contains("1080") -> "1080p"
                                    gearName.contains("720") -> "720p"
                                    gearName.contains("540") -> "540p"
                                    else -> gearName.replace("normal_", "").ifBlank { "HD" }
                                }
                                val pObj = bObj.optJSONObject("PlayAddr")
                                val urlList = pObj?.optJSONArray("UrlList")
                                val bUrl = urlList?.optString(0)
                                if (!bUrl.isNullOrBlank()) {
                                    val bitrate = bObj.optLong("Bitrate", 0L)
                                    videoVariants.add(VideoVariant(quality = qualityLabel, url = bUrl, bitrate = bitrate))
                                }
                            }
                        }
                    }

                    val musicObj = itemStruct.optJSONObject("music")
                    if (musicObj != null) {
                        audioUrl = musicObj.optString("playUrl").takeIf { it.isNotBlank() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strategy B: Parse SIGI_STATE
        if (videoVariants.isEmpty()) {
            val sigiMatch = Regex("""<script[^>]*id=["']SIGI_STATE__["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).find(html)
            if (sigiMatch != null) {
                val jsonStr = sigiMatch.groupValues[1]
                try {
                    val root = JSONObject(jsonStr)
                    val itemModule = root.optJSONObject("ItemModule")
                    if (itemModule != null) {
                        val keys = itemModule.keys()
                        if (keys.hasNext()) {
                            val firstKey = keys.next()
                            val item = itemModule.optJSONObject(firstKey)
                            if (item != null) {
                                if (title.isNullOrBlank()) {
                                    title = item.optString("desc").takeIf { it.isNotBlank() }
                                }
                                val videoObj = item.optJSONObject("video")
                                if (videoObj != null) {
                                    if (thumbnail.isNullOrBlank()) {
                                        thumbnail = videoObj.optString("cover").takeIf { it.isNotBlank() }
                                    }
                                    val playAddr = videoObj.optString("playAddr").takeIf { it.isNotBlank() }
                                    val downloadAddr = videoObj.optString("downloadAddr").takeIf { it.isNotBlank() }
                                    if (!downloadAddr.isNullOrBlank()) {
                                        videoVariants.add(VideoVariant("HD", downloadAddr, 0))
                                    }
                                    if (!playAddr.isNullOrBlank() && playAddr != downloadAddr) {
                                        videoVariants.add(VideoVariant("Standard", playAddr, 0))
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Strategy C: OpenGraph metadata fallback
        val ogVideo = extractMetaTag(html, "og:video") ?: extractMetaTag(html, "og:video:url") ?: extractMetaTag(html, "og:video:secure_url")
        if (title.isNullOrBlank()) {
            title = extractMetaTag(html, "og:title")
        }
        if (thumbnail.isNullOrBlank()) {
            thumbnail = extractMetaTag(html, "og:image")
        }
        if (!ogVideo.isNullOrBlank()) {
            videoVariants.add(VideoVariant("HD", ogVideo, 0))
        }

        // Strategy D: Direct JSON regex search
        if (videoVariants.isEmpty()) {
            val playAddrMatch = Regex(""""playAddr"\s*:\s*"([^"]+)"""").find(html)
            val downloadAddrMatch = Regex(""""downloadAddr"\s*:\s*"([^"]+)"""").find(html)

            val dlUrl = downloadAddrMatch?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }
            val plUrl = playAddrMatch?.groupValues?.get(1)?.let { SafeHttpHelper.unescapeHtmlAndJson(it) }

            if (!dlUrl.isNullOrBlank()) videoVariants.add(VideoVariant("HD", dlUrl, 0))
            if (!plUrl.isNullOrBlank() && plUrl != dlUrl) videoVariants.add(VideoVariant("Standard", plUrl, 0))
        }

        if (videoVariants.isEmpty()) {
            throw PublicMediaPageException(platformName, url, "This TikTok video isn't available for direct public download.")
        }

        // Deduplicate variants by clean URL
        val distinctVariants = videoVariants.distinctBy { it.cleanUrl }
        val formats = mutableListOf<DownloadFormat>()

        for (variant in distinctVariants) {
            val unescapedUrl = SafeHttpHelper.unescapeHtmlAndJson(variant.url)
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.VIDEO,
                    resolution = variant.quality,
                    container = "MP4",
                    codec = "H.264",
                    bitrate = variant.bitrate,
                    sizeBytes = 0L,
                    downloadUrl = unescapedUrl,
                    audioIncluded = true,
                    label = "${variant.quality} • MP4"
                )
            )
        }

        // Add audio format if found
        if (!audioUrl.isNullOrBlank()) {
            val unescapedAudio = SafeHttpHelper.unescapeHtmlAndJson(audioUrl)
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.AUDIO,
                    resolution = "Audio",
                    container = "MP3",
                    codec = "MP3",
                    sizeBytes = 0L,
                    downloadUrl = unescapedAudio,
                    audioIncluded = true,
                    label = "Original Audio • MP3"
                )
            )
        }

        val cleanTitle = UrlSecurityValidator.sanitizeFilename(
            title ?: "TikTok Video",
            fallback = "TikTok_Video"
        )

        return MediaAnalysisResult(
            url = finalUrl,
            title = cleanTitle,
            thumbnailUri = thumbnail?.let { SafeHttpHelper.unescapeHtmlAndJson(it) },
            source = "TikTok",
            formats = formats,
            isDownloadable = true,
            durationSeconds = durationSecs
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

    private data class VideoVariant(
        val quality: String,
        val url: String,
        val bitrate: Long
    ) {
        val cleanUrl: String get() = url.substringBefore("?")
    }
}
