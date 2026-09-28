package com.example.data.downloader.extractor

object YtDlpErrorSanitizer {

    fun sanitize(message: String): String {
        val lower = message.lowercase()
        if (lower.contains("not initialized") ||
            lower.contains("instance not initialized")
        ) {
            return "Media extractor couldn't start. Please try again."
        }
        if (lower.contains("confirm you're not a bot") ||
            lower.contains("cookies-from-browser") ||
            lower.contains("--cookies") ||
            lower.contains("sign in to confirm") ||
            lower.contains("bot-detection") ||
            lower.contains("captcha") ||
            lower.contains("recaptcha") ||
            lower.contains("prove you're not a robot")
        ) {
            return "Unable to access this media without additional verification from the source website."
        }
        if (lower.contains("private video") ||
            lower.contains("members-only") ||
            lower.contains("requires authentication") ||
            lower.contains("private post") ||
            lower.contains("account is private") ||
            lower.contains("followers-only")
        ) {
            return "This media is private or restricted by the owner."
        }
        if (lower.contains("traceback") ||
            lower.contains("stack trace") ||
            lower.contains("python") ||
            lower.contains("exception in") ||
            lower.contains("yt-dlp") ||
            lower.contains("youtube-dl") ||
            lower.contains("/usr/") ||
            lower.contains("/home/") ||
            lower.contains("/app/") ||
            message.contains("sys.") ||
            message.contains("def ") ||
            message.contains("import ") ||
            lower.contains("invalid syntax") ||
            lower.contains("attributeerror") ||
            lower.contains("keyerror") ||
            lower.contains("typeerror") ||
            lower.contains("nameerror") ||
            lower.contains("valueerror") ||
            lower.contains("indexerror")
        ) {
            return "Media extraction failed. The content source is currently inaccessible or unsupported."
        }
        var cleanMsg = message
        if (cleanMsg.startsWith("ERROR:", ignoreCase = true)) {
            cleanMsg = cleanMsg.substring(6).trim()
        }
        val tagRegex = Regex("""^\[[a-zA-Z0-9_-]+\]\s*""")
        cleanMsg = tagRegex.replace(cleanMsg, "").trim()
        return cleanMsg.ifBlank { "Media extraction failed." }
    }
}
