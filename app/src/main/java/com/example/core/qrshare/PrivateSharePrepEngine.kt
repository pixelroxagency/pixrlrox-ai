package com.example.core.qrshare

object PrivateSharePrepEngine {
    fun sanitizeText(text: String, trimWhitespace: Boolean = true): String {
        return if (trimWhitespace) text.trim().replace(Regex("\\s+"), " ") else text
    }
}
