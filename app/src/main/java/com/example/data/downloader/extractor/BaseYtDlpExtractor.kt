package com.example.data.downloader.extractor

import android.content.Context
import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.analyzer.ExtractionTimeoutException
import com.example.data.downloader.analyzer.NetworkErrorException
import com.example.data.downloader.analyzer.PrivateOrRestrictedException
import com.example.data.downloader.analyzer.PublicMediaPageException
import com.example.data.downloader.analyzer.UnsupportedPlatformException
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

abstract class BaseYtDlpExtractor(
    protected val context: Context? = null
) : PlatformExtractor {

    abstract override val platformName: String

    open override suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult = withContext(Dispatchers.IO) {
        UrlSecurityValidator.validateUrl(url)

        val host = UrlSecurityValidator.parseHost(url) ?: platformName.lowercase()
        YtDlpDiagnostics.log("YTDLP_EXECUTION_STARTED: platform=$platformName, host=$host")

        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                sourcePlatform = platformName,
                extractorSelected = this::class.java.simpleName,
                ytDlpExecutionReached = "YES",
                ytDlpInitialized = if (YtDlpInitializer.isReady()) "YES" else "NO"
            )
        }

        // Ensure yt-dlp runtime is fully initialized on-device
        YtDlpInitializer.ensureInitialized(context)

        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                ytDlpInitialized = if (YtDlpInitializer.isReady()) "YES" else "NO"
            )
        }

        var attemptCount = 0
        var lastException: Throwable? = null
        var info: VideoInfo? = null

        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                ytDlpSocketTimeoutSeconds = "20"
            )
        }

        while (attemptCount < 2) {
            attemptCount++

            DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                current.copy(
                    ytDlpAttemptCount = attemptCount.toString(),
                    ytDlpRetryPerformed = if (attemptCount > 1) "YES" else "NO"
                )
            }

            val trimmedUrl = url.trim()
            val request = YoutubeDLRequest(trimmedUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "20")

            val startTime = System.currentTimeMillis()
            try {
                val res = YoutubeDL.getInstance().getInfo(request)
                val durationMs = System.currentTimeMillis() - startTime
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=SUCCESS")
                
                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = "none",
                        ytDlpExceptionMessage = "none",
                        ytDlpCauseClass = "none",
                        ytDlpCauseMessage = "none",
                        ytDlpExitCode = "0",
                        ytDlpStdoutSummary = "N/A",
                        ytDlpStderrSummary = "N/A",
                        ytDlpResultSuccess = "YES",
                        ytDlpInfoExtractionSucceeded = "YES",
                        ytDlpFormatsCount = (res.formats?.size ?: 0).toString()
                    )
                }
                info = res
                break
            } catch (e: YoutubeDLException) {
                val durationMs = System.currentTimeMillis() - startTime
                val category = YtDlpDiagnostics.classifyError(e)
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=FAILED, YTDLP_FAILURE_CATEGORY=$category, YTDLP_EXCEPTION_CLASS=${e.javaClass.simpleName}")

                val exitCode = getExitCode(e)
                val stdoutSum = getStdoutSummary(e)
                val stderrSum = getStderrSummary(e)
                val causeClass = e.cause?.javaClass?.name ?: "N/A"
                val causeMsg = sanitizeAndTruncate(e.cause?.message)

                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = e.javaClass.name,
                        ytDlpExceptionMessage = sanitizeAndTruncate(e.message),
                        ytDlpCauseClass = causeClass,
                        ytDlpCauseMessage = causeMsg,
                        ytDlpExitCode = exitCode,
                        ytDlpStdoutSummary = stdoutSum,
                        ytDlpStderrSummary = stderrSum,
                        ytDlpResultSuccess = "NO",
                        ytDlpInfoExtractionSucceeded = "NO",
                        ytDlpFormatsCount = "0"
                    )
                }

                if (category == YtDlpFailureCategory.NETWORK_TIMEOUT && attemptCount < 2) {
                    kotlinx.coroutines.delay(1500)
                    lastException = e
                    continue
                }

                handleYtDlpException(url, e)
            } catch (e: ExtractionFailedException) {
                val durationMs = System.currentTimeMillis() - startTime
                val category = YtDlpDiagnostics.classifyError(e)
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=FAILED, YTDLP_FAILURE_CATEGORY=$category, YTDLP_EXCEPTION_CLASS=${e.javaClass.simpleName}")
                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = e.javaClass.name,
                        ytDlpExceptionMessage = sanitizeAndTruncate(e.message),
                        ytDlpCauseClass = e.cause?.javaClass?.name ?: "N/A",
                        ytDlpCauseMessage = sanitizeAndTruncate(e.cause?.message),
                        ytDlpExitCode = "N/A",
                        ytDlpStdoutSummary = "N/A",
                        ytDlpStderrSummary = "N/A",
                        ytDlpResultSuccess = "NO",
                        ytDlpInfoExtractionSucceeded = "NO",
                        ytDlpFormatsCount = "0"
                    )
                }

                if (category == YtDlpFailureCategory.NETWORK_TIMEOUT && attemptCount < 2) {
                    kotlinx.coroutines.delay(1500)
                    lastException = e
                    continue
                }
                throw e
            } catch (e: SocketTimeoutException) {
                val durationMs = System.currentTimeMillis() - startTime
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=FAILED, YTDLP_FAILURE_CATEGORY=NETWORK_TIMEOUT, YTDLP_EXCEPTION_CLASS=SocketTimeoutException")
                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = e.javaClass.name,
                        ytDlpExceptionMessage = sanitizeAndTruncate(e.message),
                        ytDlpCauseClass = e.cause?.javaClass?.name ?: "N/A",
                        ytDlpCauseMessage = sanitizeAndTruncate(e.cause?.message),
                        ytDlpExitCode = "N/A",
                        ytDlpStdoutSummary = "N/A",
                        ytDlpStderrSummary = "N/A",
                        ytDlpResultSuccess = "NO",
                        ytDlpInfoExtractionSucceeded = "NO",
                        ytDlpFormatsCount = "0"
                    )
                }

                if (attemptCount < 2) {
                    kotlinx.coroutines.delay(1500)
                    lastException = e
                    continue
                }
                throw ExtractionTimeoutException("Connection to the media source timed out. Please try again.")
            } catch (e: UnknownHostException) {
                val durationMs = System.currentTimeMillis() - startTime
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=FAILED, YTDLP_FAILURE_CATEGORY=NETWORK_ERROR, YTDLP_EXCEPTION_CLASS=UnknownHostException")
                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = e.javaClass.name,
                        ytDlpExceptionMessage = sanitizeAndTruncate(e.message),
                        ytDlpCauseClass = e.cause?.javaClass?.name ?: "N/A",
                        ytDlpCauseMessage = sanitizeAndTruncate(e.cause?.message),
                        ytDlpExitCode = "N/A",
                        ytDlpStdoutSummary = "N/A",
                        ytDlpStderrSummary = "N/A",
                        ytDlpResultSuccess = "NO",
                        ytDlpInfoExtractionSucceeded = "NO",
                        ytDlpFormatsCount = "0"
                    )
                }
                throw NetworkErrorException("Network error: Unable to connect to $platformName.")
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTime
                val category = YtDlpDiagnostics.classifyError(e)
                YtDlpDiagnostics.log("YTDLP_EXECUTION_COMPLETED: platform=$platformName, attempt=$attemptCount, durationMs=$durationMs, YTDLP_EXIT_STATUS=FAILED, YTDLP_FAILURE_CATEGORY=$category, YTDLP_EXCEPTION_CLASS=${e.javaClass.simpleName}")
                DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        ytDlpExceptionClass = e.javaClass.name,
                        ytDlpExceptionMessage = sanitizeAndTruncate(e.message),
                        ytDlpCauseClass = e.cause?.javaClass?.name ?: "N/A",
                        ytDlpCauseMessage = sanitizeAndTruncate(e.cause?.message),
                        ytDlpExitCode = "N/A",
                        ytDlpStdoutSummary = "N/A",
                        ytDlpStderrSummary = "N/A",
                        ytDlpResultSuccess = "NO",
                        ytDlpInfoExtractionSucceeded = "NO",
                        ytDlpFormatsCount = "0"
                    )
                }

                if (category == YtDlpFailureCategory.NETWORK_TIMEOUT && attemptCount < 2) {
                    kotlinx.coroutines.delay(1500)
                    lastException = e
                    continue
                }
                val sanitized = YtDlpErrorSanitizer.sanitize(e.message ?: "Unknown extraction error.")
                throw ExtractionFailedException(sanitized)
            }
        }

        val finalInfo = info ?: run {
            val ex = lastException ?: Exception("$platformName extraction timeout.")
            val cat = YtDlpDiagnostics.classifyError(ex)
            if (cat == YtDlpFailureCategory.NETWORK_TIMEOUT) {
                throw ExtractionTimeoutException("Connection to the media source timed out. Please try again.")
            } else {
                throw ex
            }
        }

        val rawFormats = finalInfo.formats ?: emptyList()
        val formats = mutableListOf<DownloadFormat>()

        // Check if any separate audio or overall audio source exists
        val sourceAudioAvailable = rawFormats.any { fmt ->
            val ac = fmt.acodec?.trim()
            val vc = fmt.vcodec?.trim()
            val hasAc = !ac.isNullOrBlank() && !ac.equals("none", ignoreCase = true)
            val hasVc = !vc.isNullOrBlank() && !vc.equals("none", ignoreCase = true)
            hasAc || (fmt.ext?.lowercase() in listOf("mp3", "m4a", "aac", "wav", "flac", "opus"))
        }

        DownloaderDiagnosticsRegistry.updateDiagnostics { it.copy(
            separateAudioAvailable = if (sourceAudioAvailable) "YES" else "NO"
        ) }

        for (fmt in rawFormats) {
            val downloadUrl = fmt.url?.trim() ?: continue
            if (downloadUrl.isBlank()) continue
            if (!downloadUrl.startsWith("http://", ignoreCase = true) && !downloadUrl.startsWith("https://", ignoreCase = true)) {
                continue
            }

            val ext = fmt.ext?.lowercase() ?: "mp4"
            val vcodecClean = fmt.vcodec?.trim()
            val acodecClean = fmt.acodec?.trim()

            val hasVideoCodec = !vcodecClean.isNullOrBlank() && !vcodecClean.equals("none", ignoreCase = true)
            val hasAudioCodec = !acodecClean.isNullOrBlank() && !acodecClean.equals("none", ignoreCase = true)

            // Better MediaType detection: check for "audio" in format notes/ids if codecs are ambiguous
            val isAudioId = fmt.formatId?.contains("audio", ignoreCase = true) == true || 
                            fmt.format?.contains("audio", ignoreCase = true) == true ||
                            fmt.formatNote?.contains("audio", ignoreCase = true) == true

            val mediaType = if (hasVideoCodec && !isAudioId) {
                MediaType.VIDEO
            } else if (hasAudioCodec || isAudioId) {
                MediaType.AUDIO
            } else if (ext in listOf("mp4", "webm", "mkv", "mov")) {
                MediaType.VIDEO
            } else if (ext in listOf("mp3", "m4a", "aac", "wav", "flac", "opus")) {
                MediaType.AUDIO
            } else {
                MediaType.VIDEO
            }

            val width = fmt.width
            val height = fmt.height
            val resStr: String = when {
                height > 0 -> "${height}p"
                width > 0 -> "${width}w"
                !fmt.formatNote.isNullOrBlank() -> fmt.formatNote ?: "HD"
                !fmt.formatId.isNullOrBlank() -> fmt.formatId ?: "HD"
                else -> if (mediaType == MediaType.AUDIO) "Audio" else "HD"
            }

            // Correct audio detection: only true if acodec is present and not "none"
            val audioIncluded = hasAudioCodec
            // Merging is required if it's a video stream WITHOUT audio, and we have separate audio available
            val mergeRequired = hasVideoCodec && !hasAudioCodec && sourceAudioAvailable
            val separateAudioAvailable = hasVideoCodec && !hasAudioCodec && sourceAudioAvailable

            val bitrate = (fmt.tbr * 1000).toLong()

            val baseLabel = if (fmt.formatNote.isNullOrBlank()) "$resStr • ${ext.uppercase()}" else "${fmt.formatNote} • ${ext.uppercase()}"
            val finalLabel = baseLabel

            formats.add(
                DownloadFormat(
                    id = fmt.formatId ?: UUID.randomUUID().toString(),
                    mediaType = mediaType,
                    resolution = resStr,
                    container = ext.uppercase(),
                    codec = listOfNotNull(vcodecClean, acodecClean).filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) }.joinToString("/"),
                    vcodec = vcodecClean ?: "",
                    acodec = acodecClean ?: "",
                    bitrate = bitrate,
                    sizeBytes = fmt.fileSize,
                    downloadUrl = downloadUrl,
                    audioIncluded = audioIncluded,
                    mergeRequired = mergeRequired,
                    separateAudioAvailable = separateAudioAvailable,
                    label = finalLabel,
                    httpHeaders = fmt.httpHeaders,
                    transferEngine = com.example.data.downloader.model.TransferEngine.YTDLP
                )
            )
        }

        // If no formats list was parsed, but finalInfo.url is present:
        if (formats.isEmpty() && !finalInfo.url.isNullOrBlank()) {
            val directUrl = finalInfo.url!!
            val ext = finalInfo.ext?.lowercase() ?: "mp4"
            // VideoInfo might not have top-level vcodec/acodec in all versions/cases
            val vcodec = "H.264"
            val acodec = "AAC"
            val audioIncluded = true 
            
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.VIDEO,
                    resolution = "HD",
                    container = ext.uppercase(),
                    codec = "$vcodec/$acodec",
                    vcodec = vcodec,
                    acodec = acodec,
                    bitrate = 0L,
                    sizeBytes = 0L,
                    downloadUrl = directUrl,
                    audioIncluded = audioIncluded,
                    mergeRequired = false,
                    label = "HD • ${ext.uppercase()}",
                    httpHeaders = finalInfo.httpHeaders,
                    transferEngine = com.example.data.downloader.model.TransferEngine.YTDLP
                )
            )
        }

        if (formats.isEmpty()) {
            val effPlatform = derivePlatformName(finalInfo, url)
            throw PublicMediaPageException(effPlatform, url, "No downloadable formats could be extracted from this $effPlatform link.")
        }

        val effectivePlatform = derivePlatformName(finalInfo, url)

        DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
            current.copy(
                sourcePlatform = effectivePlatform,
                extractorSelected = this::class.java.simpleName
            )
        }

        val cleanTitle = UrlSecurityValidator.sanitizeFilename(
            finalInfo.title ?: "$effectivePlatform Video",
            fallback = "${effectivePlatform}_Video"
        )

        // Second-pass enrichment: Propagate audio capability to video-only formats if usable audio exists
        val hasUsableSeparateAudio = formats.any { it.mediaType == com.example.data.downloader.model.MediaType.AUDIO }
        
        val enrichedFormats = formats.map { format ->
            if (format.mediaType == com.example.data.downloader.model.MediaType.VIDEO && !format.audioIncluded && hasUsableSeparateAudio) {
                format.copy(separateAudioAvailable = true, mergeRequired = true)
            } else {
                format
            }
        }

        // De-duplicate video qualities: prioritize enriched/muxed formats over raw video-only formats
        // if they have the same resolution/container.
        // Actually, map logic already handles the enrichment for all video-only formats.
        
        MediaAnalysisResult(
            url = url,
            title = cleanTitle,
            thumbnailUri = finalInfo.thumbnail,
            source = effectivePlatform,
            formats = enrichedFormats,
            isDownloadable = true,
            durationSeconds = if (finalInfo.duration > 0) finalInfo.duration.toDouble() else null
        )
    }

    protected fun derivePlatformName(info: VideoInfo?, url: String): String {
        val key = info?.extractorKey ?: info?.extractor
        if (!key.isNullOrBlank() && !key.equals("generic", ignoreCase = true)) {
            return when (key.lowercase()) {
                "youtube" -> "YouTube"
                "facebook" -> "Facebook"
                "instagram" -> "Instagram"
                "tiktok" -> "TikTok"
                "threads" -> "Threads"
                "twitter" -> "X / Twitter"
                "reddit" -> "Reddit"
                "vimeo" -> "Vimeo"
                "soundcloud" -> "SoundCloud"
                "pinterest" -> "Pinterest"
                else -> key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
        val host = UrlSecurityValidator.parseHost(url) ?: return if (platformName != "Universal") platformName else "Web"
        val cleanHost = host.removePrefix("www.").substringBefore(".")
        return if (cleanHost.isNotBlank()) cleanHost.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } else platformName
    }

    private fun handleYtDlpException(url: String, e: YoutubeDLException): Nothing {
        val rawMessage = e.message ?: ""
        val lower = rawMessage.lowercase()
        val effectivePlatform = derivePlatformName(null, url)

        if (lower.contains("unsupported url") || lower.contains("is not a valid url") || lower.contains("no suitable extractor")) {
            throw UnsupportedPlatformException(effectivePlatform, "This $effectivePlatform link is not supported by the video extractor.")
        }

        if (lower.contains("timed out") || lower.contains("transporterror('timed out')") || lower.contains("timeout")) {
            throw ExtractionTimeoutException("Connection to the media source timed out. Please try again.")
        }

        if (lower.contains("not initialized") || lower.contains("instance not initialized")) {
            throw ExtractionFailedException("Media extractor couldn't start. Please try again.")
        }

        if (lower.contains("login required") ||
            lower.contains("sign in") ||
            lower.contains("authentication required") ||
            lower.contains("account is private") ||
            lower.contains("private video") ||
            lower.contains("this video is private") ||
            lower.contains("followers-only")
        ) {
            throw PrivateOrRestrictedException(effectivePlatform, "This $effectivePlatform video is private or restricted by the creator.")
        }

        if (lower.contains("captcha") ||
            lower.contains("bot") ||
            lower.contains("verification") ||
            lower.contains("verify")
        ) {
            throw PublicMediaPageException(effectivePlatform, url, "$effectivePlatform requires bot/CAPTCHA verification to view this video.")
        }

        if (lower.contains("video unavailable") ||
            lower.contains("removed") ||
            lower.contains("not found") ||
            lower.contains("404") ||
            lower.contains("deleted")
        ) {
            throw PublicMediaPageException(effectivePlatform, url, "This $effectivePlatform video is unavailable or has been removed.")
        }

        val sanitized = YtDlpErrorSanitizer.sanitize(rawMessage)
        throw PublicMediaPageException(effectivePlatform, url, sanitized)
    }

    private fun sanitizeAndTruncate(input: String?): String {
        if (input == null) return "N/A"
        if (input.isBlank()) return "N/A"

        var sanitized = input
        sanitized = sanitized.replace(Regex("(?i)(cookie|token|key|auth|signature|sig)=[^\\s&\"';]+"), "$1=[redacted]")
        
        sanitized = sanitized.replace(Regex("""https?://[^\s"'<>]+""")) { match ->
            val urlStr = match.value
            try {
                val uri = android.net.Uri.parse(urlStr)
                val host = uri.host ?: "host"
                "https://$host/[redacted_url_path]"
            } catch (_: Exception) {
                "https://[redacted_url]"
            }
        }

        sanitized = sanitized.replace(Regex("(?i)(Authorization|Cookie):\\s*[^\\s]+"), "$1: [redacted]")

        if (sanitized.length > 500) {
            sanitized = sanitized.substring(0, 497) + "..."
        }
        return sanitized
    }

    private fun getExitCode(e: Throwable): String {
        return try {
            val field = e.javaClass.getDeclaredField("exitCode")
            field.isAccessible = true
            field.get(e)?.toString() ?: "N/A"
        } catch (_: Exception) {
            val msg = e.message ?: ""
            val match = Regex("""exit code (\d+)""", RegexOption.IGNORE_CASE).find(msg)
            match?.groupValues?.get(1) ?: "N/A"
        }
    }

    private fun getStdoutSummary(e: Throwable): String {
        return try {
            val field = e.javaClass.getDeclaredField("stdout")
            field.isAccessible = true
            val raw = field.get(e)?.toString() ?: "N/A"
            sanitizeAndTruncate(raw)
        } catch (_: Exception) {
            "N/A"
        }
    }

    private fun getStderrSummary(e: Throwable): String {
        return try {
            val field = e.javaClass.getDeclaredField("stderr")
            field.isAccessible = true
            val raw = field.get(e)?.toString() ?: "N/A"
            sanitizeAndTruncate(raw)
        } catch (_: Exception) {
            "N/A"
        }
    }
}
