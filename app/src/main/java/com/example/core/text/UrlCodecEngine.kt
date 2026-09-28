package com.example.core.text

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class UrlEncodingMode(val displayName: String, val isRfc3986: Boolean) {
    RFC3986_PERCENT("RFC 3986 (%20 for spaces)", true),
    FORM_URL_ENCODED("Form Urlencoded (+ for spaces)", false)
}

object UrlCodecEngine {

    fun encode(input: String, mode: UrlEncodingMode = UrlEncodingMode.RFC3986_PERCENT): String {
        if (input.isEmpty()) return ""
        val standard = URLEncoder.encode(input, StandardCharsets.UTF_8.name())
        return if (mode == UrlEncodingMode.RFC3986_PERCENT) {
            standard.replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~")
        } else {
            standard
        }
    }

    fun decode(input: String): String {
        if (input.isEmpty()) return ""

        // Validate percent encoding syntax (every '%' must be followed by 2 hex digits)
        val percentRegex = Regex("%(?![0-9a-fA-F]{2})")
        if (percentRegex.containsMatchIn(input)) {
            val match = percentRegex.find(input)
            val index = match?.range?.first ?: 0
            throw IllegalArgumentException("Malformed percent-encoding sequence at position $index ('${input.substring(index, (index + 3).coerceAtMost(input.length))}')")
        }

        return try {
            URLDecoder.decode(input, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            throw IllegalArgumentException("URL decoding failed: ${e.message}")
        }
    }
}
