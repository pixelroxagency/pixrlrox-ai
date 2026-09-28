package com.example.data.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader

data class ParsedChannel(
    val title: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null
)

data class M3uParseResult(
    val playlistName: String?,
    val channels: List<ParsedChannel>,
    val isSingleHlsManifest: Boolean = false,
    val totalLinesParsed: Int = 0
)

object M3uPlaylistParser {

    const val MAX_CHANNELS = 5000
    const val MAX_LINE_LENGTH = 4096
    const val MAX_STREAM_BYTES = 10 * 1024 * 1024 // 10 MB limit

    private val EXTINF_TAG = Regex("^#EXTINF:", RegexOption.IGNORE_CASE)
    private val TVG_ID_REGEX = Regex("""tvg-id=["']([^"']*)["']|tvg-id=([^ ,]+)""", RegexOption.IGNORE_CASE)
    private val TVG_NAME_REGEX = Regex("""tvg-name=["']([^"']*)["']|tvg-name=([^ ,]+)""", RegexOption.IGNORE_CASE)
    private val TVG_LOGO_REGEX = Regex("""tvg-logo=["']([^"']*)["']|tvg-logo=([^ ,]+)""", RegexOption.IGNORE_CASE)
    private val GROUP_TITLE_REGEX = Regex("""group-title=["']([^"']*)["']|group-title=([^ ,]+)""", RegexOption.IGNORE_CASE)

    // HLS media segment / manifest tags that indicate a single video manifest rather than an IPTV channel playlist
    private val HLS_SINGLE_STREAM_TAGS = listOf(
        "#EXT-X-TARGETDURATION",
        "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-STREAM-INF",
        "#EXT-X-PLAYLIST-TYPE",
        "#EXT-X-MAP",
        "#EXT-X-KEY",
        "#EXT-X-DISCONTINUITY",
        "#EXT-X-BYTERANGE"
    )

    fun parse(text: String, defaultName: String? = null): M3uParseResult {
        return parse(StringReader(text), defaultName)
    }

    fun parse(inputStream: InputStream, defaultName: String? = null): M3uParseResult {
        return parse(InputStreamReader(inputStream, Charsets.UTF_8), defaultName)
    }

    fun parse(reader: Reader, defaultName: String? = null): M3uParseResult {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val channels = mutableListOf<ParsedChannel>()
        val seenUrls = mutableSetOf<String>()

        var playlistName: String? = defaultName
        var hasHlsSingleStreamTag = false
        var hasExtInfTag = false
        var lineCount = 0

        var pendingTitle: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var pendingTvgLogo: String? = null
        var pendingGroupTitle: String? = null

        bufferedReader.useLines { lines ->
            for (rawLine in lines) {
                lineCount++
                val line = if (rawLine.length > MAX_LINE_LENGTH) rawLine.substring(0, MAX_LINE_LENGTH).trim() else rawLine.trim()

                if (line.isEmpty()) {
                    continue
                }

                // Check for HLS single stream manifest tags
                for (hlsTag in HLS_SINGLE_STREAM_TAGS) {
                    if (line.startsWith(hlsTag, ignoreCase = true)) {
                        hasHlsSingleStreamTag = true
                        break
                    }
                }

                if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                    val name = line.substringAfter(":").trim()
                    if (name.isNotBlank() && playlistName.isNullOrBlank()) {
                        playlistName = name
                    }
                    continue
                }

                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    // Check if EXTM3U line has name attributes
                    val nameMatch = GROUP_TITLE_REGEX.find(line) ?: TVG_NAME_REGEX.find(line)
                    if (nameMatch != null && playlistName.isNullOrBlank()) {
                        val foundName = nameMatch.groupValues[1].ifEmpty { nameMatch.groupValues[2] }
                        if (foundName.isNotBlank()) {
                            playlistName = foundName
                        }
                    }
                    continue
                }

                if (EXTINF_TAG.containsMatchIn(line)) {
                    hasExtInfTag = true
                    pendingTvgId = extractAttribute(TVG_ID_REGEX, line)
                    pendingTvgName = extractAttribute(TVG_NAME_REGEX, line)
                    pendingTvgLogo = extractAttribute(TVG_LOGO_REGEX, line)
                    pendingGroupTitle = extractAttribute(GROUP_TITLE_REGEX, line)

                    // Extract title: string following the last comma or first comma after attributes
                    val commaIdx = line.indexOf(',')
                    if (commaIdx != -1 && commaIdx < line.length - 1) {
                        val rawTitle = line.substring(commaIdx + 1).trim()
                        pendingTitle = rawTitle.ifBlank { pendingTvgName }
                    } else {
                        pendingTitle = pendingTvgName
                    }
                    continue
                }

                // Ignore other comment lines
                if (line.startsWith("#")) {
                    continue
                }

                // If this is a media URL line
                val isValidScheme = line.startsWith("http://", ignoreCase = true) ||
                        line.startsWith("https://", ignoreCase = true) ||
                        line.startsWith("rtmp://", ignoreCase = true) ||
                        line.startsWith("rtsp://", ignoreCase = true) ||
                        line.startsWith("content://", ignoreCase = true)

                if (isValidScheme) {
                    val normalizedUrl = line.trim()
                    if (!seenUrls.contains(normalizedUrl)) {
                        seenUrls.add(normalizedUrl)

                        val channelTitle = pendingTitle?.takeIf { it.isNotBlank() }
                            ?: pendingTvgName?.takeIf { it.isNotBlank() }
                            ?: deriveTitleFromUrl(normalizedUrl, channels.size + 1)

                        channels.add(
                            ParsedChannel(
                                title = channelTitle,
                                url = normalizedUrl,
                                tvgId = pendingTvgId,
                                tvgName = pendingTvgName,
                                tvgLogo = pendingTvgLogo,
                                groupTitle = pendingGroupTitle
                            )
                        )

                        if (channels.size >= MAX_CHANNELS) {
                            break
                        }
                    }

                    // Reset pending attributes after consuming URL
                    pendingTitle = null
                    pendingTvgId = null
                    pendingTvgName = null
                    pendingTvgLogo = null
                    pendingGroupTitle = null
                }
            }
        }

        // If file contains HLS single stream manifest tags (e.g. TARGETDURATION, STREAM-INF, MEDIA-SEQUENCE), it's a single HLS stream
        val isSingleHls = hasHlsSingleStreamTag

        return M3uParseResult(
            playlistName = playlistName,
            channels = if (isSingleHls) emptyList() else channels,
            isSingleHlsManifest = isSingleHls,
            totalLinesParsed = lineCount
        )
    }

    private fun extractAttribute(regex: Regex, line: String): String? {
        val match = regex.find(line) ?: return null
        val value = match.groupValues[1].ifEmpty { match.groupValues.getOrNull(2) ?: "" }
        return value.trim().takeIf { it.isNotBlank() }
    }

    private fun deriveTitleFromUrl(url: String, index: Int): String {
        return try {
            val segment = url.substringBefore("?").substringAfterLast("/")
            val clean = segment.substringBeforeLast(".").replace("_", " ").replace("-", " ").trim()
            if (clean.isNotBlank()) clean else "Channel $index"
        } catch (_: Exception) {
            "Channel $index"
        }
    }
}
