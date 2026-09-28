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

class HlsMediaAnalyzer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceAnalyzer {

    override val priority: Int = 70

    override suspend fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("hls")
    }

    override suspend fun analyze(url: String): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PixelRox/1.0 (Android; Downloader)")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch HLS manifest (HTTP ${response.code})")
        }

        val body = response.body?.string() ?: throw Exception("Empty HLS manifest response")
        if (!body.contains("#EXTM3U")) {
            throw Exception("Invalid HLS stream: #EXTM3U header missing")
        }

        val baseUri = Uri.parse(url)
        val formats = mutableListOf<DownloadFormat>()
        val lines = body.lines().map { it.trim() }

        var currentStreamInf: String? = null

        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                currentStreamInf = line.substringAfter("#EXT-X-STREAM-INF:")
            } else if (currentStreamInf != null && line.isNotBlank() && !line.startsWith("#")) {
                val variantUrl = resolveUrl(baseUri, line)
                val format = parseStreamInf(currentStreamInf, variantUrl)
                formats.add(format)
                currentStreamInf = null
            }

            // Also check for audio tracks
            if (line.startsWith("#EXT-X-MEDIA:") && line.contains("TYPE=AUDIO")) {
                val uriMatch = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
                val nameMatch = Regex("""NAME="([^"]+)"""").find(line)?.groupValues?.get(1) ?: "Audio Track"
                if (uriMatch != null) {
                    val audioUrl = resolveUrl(baseUri, uriMatch)
                    formats.add(
                        DownloadFormat(
                            id = UUID.randomUUID().toString(),
                            mediaType = MediaType.AUDIO,
                            container = "AAC",
                            codec = "AAC",
                            bitrate = 128000L,
                            downloadUrl = audioUrl,
                            audioIncluded = true,
                            label = "$nameMatch • AAC • 128 kbps"
                        )
                    )
                }
            }
        }

        // If no variants found but valid single playlist
        if (formats.isEmpty()) {
            formats.add(
                DownloadFormat(
                    id = UUID.randomUUID().toString(),
                    mediaType = MediaType.VIDEO,
                    resolution = "Source",
                    container = "HLS",
                    codec = "Auto",
                    downloadUrl = url,
                    audioIncluded = true,
                    label = "Original Stream • HLS"
                )
            )
        }

        // Sort video formats by resolution/bandwidth descending
        val sortedFormats = formats.sortedWith(
            compareByDescending<DownloadFormat> { it.mediaType == MediaType.VIDEO }
                .thenByDescending { parseResolutionHeight(it.resolution) }
                .thenByDescending { it.bitrate }
        )

        val title = guessTitleFromUrl(url)

        MediaAnalysisResult(
            url = url,
            title = title,
            source = "HLS Stream Playlist",
            formats = sortedFormats,
            isDownloadable = true
        )
    }

    private fun parseStreamInf(streamInf: String, variantUrl: String): DownloadFormat {
        var bandwidth = 0L
        var resolution = ""
        var codecs = ""

        val bandwidthMatch = Regex("""BANDWIDTH=(\d+)""").find(streamInf)
        if (bandwidthMatch != null) {
            bandwidth = bandwidthMatch.groupValues[1].toLongOrNull() ?: 0L
        }

        val resMatch = Regex("""RESOLUTION=(\d+x\d+)""").find(streamInf)
        if (resMatch != null) {
            val resStr = resMatch.groupValues[1]
            val parts = resStr.split("x")
            if (parts.size == 2) {
                val height = parts[1].toIntOrNull() ?: 0
                resolution = when {
                    height >= 2160 -> "2160p (4K)"
                    height >= 1440 -> "1440p (2K)"
                    height >= 1080 -> "1080p"
                    height >= 720 -> "720p"
                    height >= 480 -> "480p"
                    height >= 360 -> "360p"
                    height >= 240 -> "240p"
                    height >= 144 -> "144p"
                    else -> "${height}p"
                }
            }
        }

        val codecsMatch = Regex("""CODECS="([^"]+)"""").find(streamInf)
        if (codecsMatch != null) {
            codecs = codecsMatch.groupValues[1]
        }

        val id = UUID.randomUUID().toString()
        val kbps = if (bandwidth > 0) "${bandwidth / 1000} kbps" else ""
        val codecLabel = if (codecs.isNotBlank()) codecs.split(",").firstOrNull()?.trim() ?: "H.264" else "H.264"
        val label = listOfNotNull(
            resolution.ifBlank { null },
            "MP4",
            kbps.ifBlank { null }
        ).joinToString(" • ")

        return DownloadFormat(
            id = id,
            mediaType = MediaType.VIDEO,
            resolution = resolution.ifBlank { "Auto" },
            container = "MP4",
            codec = codecLabel,
            bitrate = bandwidth,
            downloadUrl = variantUrl,
            audioIncluded = true,
            label = label
        )
    }

    private fun parseResolutionHeight(res: String): Int {
        val digits = res.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun resolveUrl(baseUri: Uri, relativeOrAbsolute: String): String {
        return if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            relativeOrAbsolute
        } else {
            val scheme = baseUri.scheme ?: "https"
            val host = baseUri.host ?: ""
            val port = if (baseUri.port != -1) ":${baseUri.port}" else ""
            val path = baseUri.path ?: ""
            val lastSlash = path.lastIndexOf('/')
            val basePath = if (lastSlash != -1) path.substring(0, lastSlash + 1) else "/"

            if (relativeOrAbsolute.startsWith("/")) {
                "$scheme://$host$port$relativeOrAbsolute"
            } else {
                "$scheme://$host$port$basePath$relativeOrAbsolute"
            }
        }
    }

    private fun guessTitleFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val segment = uri.lastPathSegment?.substringBefore(".m3u8")?.replace("_", " ")?.replace("-", " ")
            if (!segment.isNullOrBlank()) segment else "HLS Media Stream"
        } catch (_: Exception) {
            "HLS Media Stream"
        }
    }
}
