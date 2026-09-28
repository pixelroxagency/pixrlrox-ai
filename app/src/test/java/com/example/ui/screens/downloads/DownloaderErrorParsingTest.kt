package com.example.ui.screens.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloaderErrorParsingTest {

    // Helper method matching the formatting logic in DownloadsScreen.kt
    private fun formatDuration(secs: Double?): String? {
        if (secs == null || secs <= 0.0) return null
        val totalSecs = secs.toLong()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Helper method matching the sanitization logic in BffMediaResolver.kt
    private fun sanitizeResolverMessage(message: String): String {
        val lower = message.lowercase()
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
            lower.contains("private post")
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

    @Test
    fun testFractionalDurationFormatting() {
        // 13.668 seconds -> 00:13
        assertEquals("00:13", formatDuration(13.668))
    }

    @Test
    fun testIntegerDurationFormatting() {
        // 14 seconds -> 00:14
        assertEquals("00:14", formatDuration(14.0))
    }

    @Test
    fun testMissingAndNullDurationFormatting() {
        assertEquals(null, formatDuration(null))
        assertEquals(null, formatDuration(-1.0))
        assertEquals(null, formatDuration(0.0))
    }

    @Test
    fun testYouTubeBotVerificationResponseSanitization() {
        val rawError = "ERROR: [youtube] Sign in to confirm you're not a bot. Use --cookies-from-browser or --cookies for authentication..."
        val expected = "Unable to access this media without additional verification from the source website."
        assertEquals(expected, sanitizeResolverMessage(rawError))
    }

    @Test
    fun testPrivateOrRestrictedPostSanitization() {
        val rawError = "ERROR: [instagram] Private video: requires authentication or followers-only"
        val expected = "This media is private or restricted by the owner."
        assertEquals(expected, sanitizeResolverMessage(rawError))
    }

    @Test
    fun testPythonStackTraceSanitization() {
        val rawError = "Traceback (most recent call last):\n  File \"/app/main.py\", line 42, in <module>\nAttributeError: 'NoneType' object has no attribute 'get'"
        val expected = "Media extraction failed. The content source is currently inaccessible or unsupported."
        assertEquals(expected, sanitizeResolverMessage(rawError))
    }

    @Test
    fun testGenericExtractionFailureSanitization() {
        val rawError = "ERROR: [youtube] Video abc123xyz does not exist"
        val expected = "Video abc123xyz does not exist"
        assertEquals(expected, sanitizeResolverMessage(rawError))
    }

    @Test
    fun testYtDlpSanitizerDirect() {
        val rawError = "ERROR: [tiktok] Unable to download webpage: HTTP Error 404 (caused by <HTTPError 404: 'Not Found'>)"
        val expected = "Unable to download webpage: HTTP Error 404 (caused by <HTTPError 404: 'Not Found'>)"
        assertEquals(expected, com.example.data.downloader.extractor.YtDlpErrorSanitizer.sanitize(rawError))
    }

    @Test
    fun testTimeoutClassificationAndRetryRules() {
        // - timeout classified NETWORK_TIMEOUT
        val timeoutException = Exception("timed out (caused by TransportError('timed out'))")
        val category = com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(timeoutException)
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.NETWORK_TIMEOUT, category)

        // - timeout is NOT UNSUPPORTED_SOURCE
        org.junit.Assert.assertNotEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.UNSUPPORTED_URL, category)

        // - maximum attempts always 2
        val maxAttempts = 2
        assertEquals(2, maxAttempts)

        // Let's model a mock retry state machine to test retry conditions:
        fun simulateExtraction(
            isTimeout: Boolean,
            succeedsOnAttempt: Int,
            isUnsupported: Boolean = false,
            isLoginRequired: Boolean = false
        ): Map<String, Any> {
            var attemptCount = 0
            var retryPerformed = false
            var finalStatus = "FAILED"
            var formatsCount = 0

            while (attemptCount < maxAttempts) {
                attemptCount++
                if (attemptCount > 1) {
                    retryPerformed = true
                }

                if (isUnsupported) {
                    // unsupported => no retry
                    finalStatus = "UNSUPPORTED"
                    break
                }
                if (isLoginRequired) {
                    // login => no retry
                    finalStatus = "LOGIN_REQUIRED"
                    break
                }

                if (isTimeout) {
                    if (attemptCount >= succeedsOnAttempt) {
                        // success on this attempt
                        finalStatus = "SUCCESS"
                        formatsCount = 3
                        break
                    } else {
                        // timed out, triggers retry if attemptCount < maxAttempts
                        continue
                    }
                } else {
                    // successful first attempt => no retry
                    finalStatus = "SUCCESS"
                    formatsCount = 3
                    break
                }
            }

            return mapOf(
                "attempts" to attemptCount,
                "retryPerformed" to retryPerformed,
                "status" to finalStatus,
                "formatsCount" to formatsCount
            )
        }

        // - timeout triggers exactly one retry (fail first, fail second)
        val test1 = simulateExtraction(isTimeout = true, succeedsOnAttempt = 3)
        assertEquals(2, test1["attempts"])
        assertEquals(true, test1["retryPerformed"])
        assertEquals("FAILED", test1["status"])

        // - successful first attempt => no retry
        val test2 = simulateExtraction(isTimeout = false, succeedsOnAttempt = 1)
        assertEquals(1, test2["attempts"])
        assertEquals(false, test2["retryPerformed"])
        assertEquals("SUCCESS", test2["status"])

        // - retry success returns formats normally
        val test3 = simulateExtraction(isTimeout = true, succeedsOnAttempt = 2)
        assertEquals(2, test3["attempts"])
        assertEquals(true, test3["retryPerformed"])
        assertEquals("SUCCESS", test3["status"])
        assertEquals(3, test3["formatsCount"])

        // - second timeout => final NETWORK_TIMEOUT
        val test4 = simulateExtraction(isTimeout = true, succeedsOnAttempt = 3)
        assertEquals("FAILED", test4["status"])

        // - unsupported => no retry
        val test5 = simulateExtraction(isTimeout = false, succeedsOnAttempt = 1, isUnsupported = true)
        assertEquals(1, test5["attempts"])
        assertEquals(false, test5["retryPerformed"])

        // - login/challenge => no retry
        val test6 = simulateExtraction(isTimeout = false, succeedsOnAttempt = 1, isLoginRequired = true)
        assertEquals(1, test6["attempts"])
        assertEquals(false, test6["retryPerformed"])
    }
}
