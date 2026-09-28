package com.example.data.downloader.analyzer

import android.net.Uri
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class DirectMediaAnalyzer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceAnalyzer {

    override val priority: Int = 80

    private val supportedVideoExtensions = setOf("mp4", "webm", "mkv", "mov", "3gp", "avi", "ts", "m4v", "flv", "wmv")
    private val supportedAudioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "wma", "m4b")
    private val supportedFileExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "rtf",
        "zip", "rar", "7z", "tar", "gz", "apk",
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp"
    )

    private val platformSocialDomains = setOf(
        "youtube.com", "youtu.be", "instagram.com", "facebook.com", "fb.watch",
        "tiktok.com", "twitter.com", "x.com", "threads.net", "threads.com", "twitch.tv",
        "vimeo.com", "dailymotion.com", "snapchat.com", "reddit.com"
    )

    override suspend fun canHandle(url: String): Boolean {
        val lower = url.lowercase().substringBefore("?").substringBefore("#")
        val extension = getExtension(lower)
        return supportedVideoExtensions.contains(extension) ||
            supportedAudioExtensions.contains(extension) ||
            supportedFileExtensions.contains(extension)
    }

    override suspend fun analyze(url: String): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Unsupported protocol '$scheme'. Only HTTP and HTTPS URLs are supported.")
        }

        var resolvedUrl = url
        var contentType: String? = null
        var contentLength: Long = -1L
        var contentDisposition: String? = null
        var httpStatusCode = 200

        // 1. Probe using HTTP HEAD request
        try {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()

            httpClient.newCall(headReq).execute().use { response ->
                httpStatusCode = response.code
                resolvedUrl = response.request.url.toString()
                com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                    current.copy(
                        httpStatus = response.code.toString(),
                        responseContentType = response.header("Content-Type") ?: "N/A"
                    )
                }
                if (response.isSuccessful) {
                    contentType = response.header("Content-Type")
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    contentDisposition = response.header("Content-Disposition")
                } else if (response.code in listOf(401, 403, 404, 410, 500, 502, 503)) {
                    throwHttpException(response.code)
                }
            }
        } catch (e: Exception) {
            if (e.message?.startsWith("Server returned") == true || e.message?.startsWith("Remote") == true || e.message?.startsWith("Access") == true) {
                throw e
            }
            // HEAD might be rejected (e.g. 405 Method Not Allowed), continue to Range GET
        }

        // 2. If HEAD gave no useful metadata or returned HTML, perform a small Range GET probe
        val isHtmlHeader = contentType?.startsWith("text/html", ignoreCase = true) == true ||
                contentType?.startsWith("application/xhtml+xml", ignoreCase = true) == true

        if (contentType == null || isHtmlHeader || contentLength <= 0) {
            try {
                val getReq = Request.Builder()
                    .url(resolvedUrl)
                    .header("Range", "bytes=0-2048")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()

                httpClient.newCall(getReq).execute().use { response ->
                    httpStatusCode = response.code
                    resolvedUrl = response.request.url.toString()
                    com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry.updateDiagnostics { current ->
                        current.copy(
                            httpStatus = response.code.toString(),
                            responseContentType = response.header("Content-Type") ?: current.responseContentType
                        )
                    }
                    if (response.isSuccessful || response.code == 206) {
                        contentType = response.header("Content-Type") ?: contentType
                        if (contentLength <= 0) {
                            val cr = response.header("Content-Range")
                            if (cr != null && cr.contains("/")) {
                                contentLength = cr.substringAfter("/").toLongOrNull() ?: -1L
                            } else {
                                contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                            }
                        }
                        if (contentDisposition == null) {
                            contentDisposition = response.header("Content-Disposition")
                        }

                        // Verify chunk content to detect HTML pages disguised with generic headers
                        val peekBytes = response.body?.byteStream()?.readNBytes(512) ?: ByteArray(0)
                        val peekStr = String(peekBytes, Charsets.UTF_8).lowercase().trim()
                        if (peekStr.contains("<!doctype html") || peekStr.contains("<html") || peekStr.contains("<head>")) {
                            throw Exception("The provided link points to an HTML web page, not a direct downloadable file. Please enter a direct media link.")
                        }
                    } else if (response.code in listOf(401, 403, 404, 410, 500, 502, 503)) {
                        throwHttpException(response.code)
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("HTML web page") == true || e.message?.startsWith("Server returned") == true || e.message?.startsWith("Remote") == true || e.message?.startsWith("Access") == true) {
                    throw e
                }
            }
        }

        // 3. Reject confirmed HTML web pages
        val lowerType = contentType?.lowercase() ?: ""
        if (lowerType.startsWith("text/html") || lowerType.startsWith("application/xhtml+xml")) {
            throw Exception("The provided link points to an HTML web page, not a direct downloadable file. Please copy and paste a direct media URL.")
        }

        // 4. File name and extension resolution
        val filename = extractFilename(resolvedUrl, contentDisposition)
        val ext = getExtension(filename).ifBlank { getExtension(resolvedUrl) }

        val isVideo = isVideoMedia(lowerType, ext)
        val isAudio = isAudioMedia(lowerType, ext)
        val isFile = isFileMedia(lowerType, ext)

        if (!isVideo && !isAudio && !isFile && (lowerType.isBlank() || lowerType.contains("text/plain"))) {
            throw Exception("No direct downloadable media or file format found at this link.")
        }

        val mediaType = when {
            isVideo -> MediaType.VIDEO
            isAudio -> MediaType.AUDIO
            else -> MediaType.FILE
        }

        val container = detectContainer(lowerType, ext, mediaType)
        val detectedResolution = if (mediaType == MediaType.VIDEO) detectResolutionHint(filename + " " + resolvedUrl) else ""

        val labelStr = buildString {
            if (detectedResolution.isNotBlank()) append("$detectedResolution • ")
            append(container.uppercase())
            if (contentLength > 0) {
                append(" • ")
                append(formatBytes(contentLength))
            }
        }

        val format = DownloadFormat(
            id = UUID.randomUUID().toString(),
            mediaType = mediaType,
            resolution = if (mediaType == MediaType.VIDEO) detectedResolution.ifBlank { "Original" } else "",
            container = container,
            codec = if (mediaType == MediaType.VIDEO) "H.264" else container,
            sizeBytes = if (contentLength > 0) contentLength else 0L,
            downloadUrl = resolvedUrl,
            audioIncluded = true,
            label = labelStr
        )

        val title = filename.substringBeforeLast(".").ifBlank { "Download" }

        val sourceDescription = when (mediaType) {
            MediaType.VIDEO -> "Direct Video File"
            MediaType.AUDIO -> "Direct Audio File"
            MediaType.FILE -> if (ext.equals("pdf", ignoreCase = true)) "Direct PDF Document" else "Direct File Download"
        }

        MediaAnalysisResult(
            url = resolvedUrl,
            title = title,
            source = sourceDescription,
            formats = listOf(format),
            isDownloadable = true
        )
    }

    private fun throwHttpException(code: Int): Nothing {
        when (code) {
            401 -> throw Exception("Access requires host server authentication (HTTP 401).")
            403 -> throw Exception("Access forbidden by the remote server (HTTP 403).")
            404 -> throw Exception("Remote file not found (HTTP 404). Please verify the link.")
            410 -> throw Exception("The requested file is no longer available at this address (HTTP 410 Gone).")
            else -> throw Exception("Server returned HTTP error code $code.")
        }
    }

    private fun isVideoMedia(contentType: String?, extension: String): Boolean {
        if (contentType != null && contentType.startsWith("video/")) return true
        return supportedVideoExtensions.contains(extension.lowercase())
    }

    private fun isAudioMedia(contentType: String?, extension: String): Boolean {
        if (contentType != null && contentType.startsWith("audio/")) return true
        return supportedAudioExtensions.contains(extension.lowercase())
    }

    private fun isFileMedia(contentType: String?, extension: String): Boolean {
        if (contentType != null && (
                    contentType.startsWith("image/") ||
                    contentType.contains("pdf") ||
                    contentType.contains("octet-stream") ||
                    contentType.contains("zip") ||
                    contentType.contains("tar") ||
                    contentType.contains("application/")
                )
        ) {
            return true
        }
        return supportedFileExtensions.contains(extension.lowercase())
    }

    private fun detectContainer(contentType: String?, extension: String, mediaType: MediaType): String {
        val extLower = extension.lowercase()
        if (extLower.isNotBlank()) {
            return extLower.uppercase()
        }
        val mime = contentType?.substringBefore(";")?.trim()?.lowercase() ?: ""
        return when {
            mime.contains("mp4") -> "MP4"
            mime.contains("webm") -> "WEBM"
            mime.contains("matroska") || mime.contains("mkv") -> "MKV"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("aac") -> "AAC"
            mime.contains("ogg") -> "OGG"
            mime.contains("wav") -> "WAV"
            mime.contains("flac") -> "FLAC"
            mime.contains("pdf") -> "PDF"
            mime.contains("png") -> "PNG"
            mime.contains("jpeg") || mime.contains("jpg") -> "JPG"
            mime.contains("zip") -> "ZIP"
            mediaType == MediaType.AUDIO -> "MP3"
            mediaType == MediaType.VIDEO -> "MP4"
            else -> "FILE"
        }
    }

    private fun detectResolutionHint(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p (4K)"
            lower.contains("1440p") || lower.contains("2k") -> "1440p (2K)"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("480p") || lower.contains("sd") -> "480p"
            lower.contains("360p") -> "360p"
            lower.contains("240p") -> "240p"
            else -> ""
        }
    }

    private fun getExtension(urlOrPath: String): String {
        val path = urlOrPath.substringBefore("?").substringBefore("#")
        val lastSlash = path.lastIndexOf('/')
        val filename = if (lastSlash != -1) path.substring(lastSlash + 1) else path
        val dot = filename.lastIndexOf('.')
        return if (dot != -1 && dot < filename.length - 1) filename.substring(dot + 1) else ""
    }

    private fun extractFilename(url: String, contentDisposition: String?): String {
        if (contentDisposition != null) {
            val fnMatch = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";]+)['"]?""", RegexOption.IGNORE_CASE).find(contentDisposition)
            if (fnMatch != null) {
                val cleaned = fnMatch.groupValues[1].trim()
                if (cleaned.isNotBlank()) return cleaned
            }
        }
        return try {
            val uri = Uri.parse(url)
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank() && segment.contains(".")) segment else "download_file"
        } catch (_: Exception) {
            "download_file"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
