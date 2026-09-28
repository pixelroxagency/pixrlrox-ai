package com.example.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

object RemotePlaylistFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchAndParse(url: String, customName: String? = null): M3uParseResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val lower = trimmedUrl.lowercase()

        // 1. Strict protocol security check
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw IllegalArgumentException("Only http:// and https:// playlist URLs are supported.")
        }

        // Prevent loopback / localhost abuse
        if (lower.contains("://localhost") || lower.contains("://127.0.0.1") || lower.contains("://::1")) {
            throw IllegalArgumentException("Localhost and loopback URLs are not permitted.")
        }

        val request = Request.Builder()
            .url(trimmedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) PixelRox/1.0 IPTV")
            .header("Accept", "*/*")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw IllegalStateException("Unable to connect to playlist URL: ${e.localizedMessage ?: "Connection failed"}")
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${resp.code} (${resp.message})")
            }

            // Check Content-Length header if provided
            val contentLength = resp.body?.contentLength() ?: -1L
            if (contentLength > M3uPlaylistParser.MAX_STREAM_BYTES) {
                throw IllegalStateException("Playlist is too large (${contentLength / (1024 * 1024)}MB). Maximum supported size is 10MB.")
            }

            val bodyStream: InputStream = resp.body?.byteStream()
                ?: throw IllegalStateException("Received empty response from server.")

            // Read safely up to MAX_STREAM_BYTES
            val boundedStream = object : InputStream() {
                private var bytesRead = 0L
                override fun read(): Int {
                    if (bytesRead >= M3uPlaylistParser.MAX_STREAM_BYTES) {
                        throw IllegalStateException("Playlist exceeds 10MB limit during download.")
                    }
                    val b = bodyStream.read()
                    if (b != -1) bytesRead++
                    return b
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (bytesRead >= M3uPlaylistParser.MAX_STREAM_BYTES) {
                        throw IllegalStateException("Playlist exceeds 10MB limit during download.")
                    }
                    val readLen = minOf(len.toLong(), M3uPlaylistParser.MAX_STREAM_BYTES - bytesRead).toInt()
                    val count = bodyStream.read(b, off, readLen)
                    if (count != -1) bytesRead += count
                    return count
                }

                override fun close() {
                    bodyStream.close()
                }
            }

            val derivedDefaultName = customName?.takeIf { it.isNotBlank() }
                ?: derivePlaylistNameFromUrl(trimmedUrl)

            val parseResult = M3uPlaylistParser.parse(boundedStream, derivedDefaultName)

            if (parseResult.isSingleHlsManifest) {
                throw IllegalStateException("The URL points to a single video/HLS stream rather than an IPTV channel playlist. Please add it as a Single Stream instead.")
            }

            if (parseResult.channels.isEmpty()) {
                throw IllegalStateException("No playable channels found in this playlist.")
            }

            parseResult
        }
    }

    private fun derivePlaylistNameFromUrl(url: String): String {
        return try {
            val segment = url.substringBefore("?").substringAfterLast("/")
            val clean = segment.substringBeforeLast(".").replace("_", " ").replace("-", " ").trim()
            if (clean.isNotBlank() && clean.length > 2) clean else "Web Playlist"
        } catch (_: Exception) {
            "Web Playlist"
        }
    }
}
