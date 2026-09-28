package com.example.core.text

object WhatsAppFormatterEngine {

    fun wrapBold(text: String): String = "*$text*"
    fun wrapItalic(text: String): String = "_${text}_"
    fun wrapStrikethrough(text: String): String = "~$text~"
    fun wrapMonospace(text: String): String = "```$text```"

    fun applyFormatting(text: String, bold: Boolean, italic: Boolean, strikethrough: Boolean, monospace: Boolean): String {
        var result = text
        if (monospace) {
            return wrapMonospace(result)
        }
        if (bold) result = wrapBold(result)
        if (italic) result = wrapItalic(result)
        if (strikethrough) result = wrapStrikethrough(result)
        return result
    }

    /**
     * Formats selection inside full text with specific markdown style.
     */
    fun formatSelection(fullText: String, selectionStart: Int, selectionEnd: Int, tag: String): String {
        if (selectionStart < 0 || selectionEnd > fullText.length || selectionStart >= selectionEnd) {
            // Apply to entire text if nothing selected
            return if (tag == "```") wrapMonospace(fullText) else "$tag$fullText$tag"
        }
        val before = fullText.substring(0, selectionStart)
        val selected = fullText.substring(selectionStart, selectionEnd)
        val after = fullText.substring(selectionEnd)
        val wrapped = if (tag == "```") wrapMonospace(selected) else "$tag$selected$tag"
        return "$before$wrapped$after"
    }
}
