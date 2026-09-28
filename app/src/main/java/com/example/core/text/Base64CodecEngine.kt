package com.example.core.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

enum class Base64Mode(val displayName: String) {
    STANDARD("Standard (RFC 4648)"),
    URL_SAFE("URL Safe (-_)")
}

object Base64CodecEngine {

    fun encodeText(text: String, mode: Base64Mode = Base64Mode.STANDARD): String {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return encodeBytes(bytes, mode)
    }

    fun decodeText(base64Str: String, mode: Base64Mode = Base64Mode.STANDARD): String {
        val cleaned = base64Str.trim()
        if (cleaned.isEmpty()) return ""
        val bytes = decodeBytes(cleaned, mode)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun encodeBytes(bytes: ByteArray, mode: Base64Mode = Base64Mode.STANDARD): String {
        return try {
            val flags = if (mode == Base64Mode.URL_SAFE) {
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            } else {
                android.util.Base64.NO_WRAP
            }
            android.util.Base64.encodeToString(bytes, flags).trim()
        } catch (_: Throwable) {
            // Fallback for JVM unit tests
            if (mode == Base64Mode.URL_SAFE) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)
            }
        }
    }

    fun decodeBytes(base64Str: String, mode: Base64Mode = Base64Mode.STANDARD): ByteArray {
        val cleaned = base64Str.trim()
        return try {
            val flags = if (mode == Base64Mode.URL_SAFE) {
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            } else {
                android.util.Base64.NO_WRAP
            }
            android.util.Base64.decode(cleaned, flags)
        } catch (_: Throwable) {
            if (mode == Base64Mode.URL_SAFE) {
                java.util.Base64.getUrlDecoder().decode(cleaned)
            } else {
                java.util.Base64.getDecoder().decode(cleaned)
            }
        }
    }

    suspend fun encodeStream(
        inputStream: InputStream,
        maxBytes: Long = 10 * 1024 * 1024, // 10MB safety cap
        mode: Base64Mode = Base64Mode.STANDARD
    ): String = withContext(Dispatchers.IO) {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var totalRead = 0L
        var read: Int

        inputStream.use { stream ->
            while (stream.read(chunk).also { read = it } != -1) {
                totalRead += read
                if (totalRead > maxBytes) {
                    throw IllegalArgumentException("File exceeds maximum allowed size of ${maxBytes / (1024 * 1024)} MB for Base64 conversion.")
                }
                buffer.write(chunk, 0, read)
            }
        }

        encodeBytes(buffer.toByteArray(), mode)
    }
}

