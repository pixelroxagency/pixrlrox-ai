package com.example.core.text

import java.util.Locale

data class TextStatistics(
    val charCount: Int,
    val charCountNoSpaces: Int,
    val wordCount: Int,
    val lineCount: Int,
    val paragraphCount: Int
)

object TextTransformEngine {

    fun toUpperCase(text: String): String = text.uppercase(Locale.getDefault())

    fun toLowerCase(text: String): String = text.lowercase(Locale.getDefault())

    fun toTitleCase(text: String): String {
        return text.split(Regex("(?<=\\s)|(?=\\s)"))
            .joinToString("") { word ->
                if (word.isBlank()) word
                else word.substring(0, 1).uppercase(Locale.getDefault()) + word.substring(1).lowercase(Locale.getDefault())
            }
    }

    fun toSentenceCase(text: String): String {
        var capitalizeNext = true
        val sb = java.lang.StringBuilder()
        for (ch in text) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                capitalizeNext = true
            }
        }
        return sb.toString()
    }

    fun trimWhitespace(text: String): String {
        return text.lines().map { it.trim() }.joinToString("\n").trim()
    }

    fun removeExtraSpaces(text: String): String {
        return text.replace(Regex("[ \\t]+"), " ").trim()
    }

    fun removeBlankLines(text: String): String {
        return text.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    fun sortLinesAZ(text: String): String {
        return text.lines().sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("\n")
    }

    fun sortLinesZA(text: String): String {
        return text.lines().sortedWith(String.CASE_INSENSITIVE_ORDER.reversed()).joinToString("\n")
    }

    fun removeDuplicateLines(text: String): String {
        return text.lines().distinct().joinToString("\n")
    }

    fun reverseLines(text: String): String {
        return text.lines().reversed().joinToString("\n")
    }

    fun reverseText(text: String): String {
        return text.reversed()
    }

    fun generateSlug(text: String): String {
        val trimmed = text.trim().lowercase(Locale.getDefault())
        val replaced = Regex("[^a-z0-9]+").replace(trimmed, "-")
        return replaced.trim('-')
    }

    fun computeStatistics(text: String): TextStatistics {
        val chars = text.length
        val charsNoSpaces = text.count { !it.isWhitespace() }
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val lines = if (text.isEmpty()) 0 else text.lines().size
        val paragraphs = if (text.isBlank()) 0 else text.split(Regex("(\\r?\\n){2,}")).filter { it.isNotBlank() }.size

        return TextStatistics(
            charCount = chars,
            charCountNoSpaces = charsNoSpaces,
            wordCount = words,
            lineCount = lines,
            paragraphCount = paragraphs
        )
    }
}
