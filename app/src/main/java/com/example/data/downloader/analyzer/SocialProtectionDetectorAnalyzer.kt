package com.example.data.downloader.analyzer

import com.example.data.downloader.extractor.UrlSecurityValidator
import com.example.data.downloader.model.MediaAnalysisResult

class PublicMediaPageException(
    val platformName: String,
    val pageUrl: String,
    override val message: String = "This source isn't supported for direct on-device download."
) : Exception(message)

class AuthRequiredException(
    val platformName: String,
    override val message: String = "The source requires verification that the on-device downloader cannot complete."
) : Exception(message)

class PrivateOrRestrictedException(
    val platformName: String,
    override val message: String = "This media is private or restricted by the owner."
) : Exception(message)

class DrmProtectedException(
    val platformName: String,
    override val message: String = "The source requires verification that the on-device downloader cannot complete."
) : Exception(message)

class NetworkErrorException(
    override val message: String = "Couldn't reach the source. Check your connection and try again."
) : Exception(message)

class ExtractionFailedException(
    override val message: String = "Couldn't extract this media right now. Please try Analyze again."
) : Exception(message)

class ExtractionTimeoutException(
    override val message: String = "Connection to the media source timed out. Please try again."
) : Exception(message)

class UnsupportedPlatformException(
    val platformName: String,
    override val message: String = "This source isn't supported for direct on-device download."
) : Exception(message)

class ResponseParsingException(
    override val message: String = "Couldn't extract this media right now. Please try Analyze again."
) : Exception(message)

class SocialProtectionDetectorAnalyzer : MediaSourceAnalyzer {
    override val priority: Int = 50

    private val platformDomains = mapOf(
        "youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "fb.watch" to "Facebook",
        "tiktok.com" to "TikTok",
        "twitter.com" to "X (Twitter)",
        "x.com" to "X (Twitter)",
        "threads.net" to "Threads",
        "threads.com" to "Threads",
        "pinterest.com" to "Pinterest",
        "twitch.tv" to "Twitch",
        "vimeo.com" to "Vimeo",
        "dailymotion.com" to "Dailymotion",
        "snapchat.com" to "Snapchat",
        "reddit.com" to "Reddit"
    )

    override suspend fun canHandle(url: String): Boolean {
        val host = UrlSecurityValidator.parseHost(url) ?: return false
        val isDomainMatch = platformDomains.keys.any { domain -> host == domain || host.endsWith(".$domain") }
        if (!isDomainMatch) return false

        val path = url.lowercase().substringBefore('?').substringBefore('#')
        val isDirectMediaFile = path.endsWith(".mp4") || path.endsWith(".webm") || 
                                path.endsWith(".m3u8") || path.endsWith(".mp3") || 
                                path.endsWith(".m4a") || path.endsWith(".wav") || 
                                path.endsWith(".ogg") || path.endsWith(".mov") || 
                                path.endsWith(".mkv")
        return !isDirectMediaFile
    }

    override suspend fun analyze(url: String): MediaAnalysisResult {
        val host = UrlSecurityValidator.parseHost(url) ?: ""
        val lower = url.lowercase()
        val platform = platformDomains.entries.firstOrNull { host == it.key || host.endsWith(".${it.key}") }?.value ?: "Social Media"

        if (lower.contains("login") || lower.contains("signin") || lower.contains("auth") || lower.contains("checkpoint")) {
            throw AuthRequiredException(platform)
        }
        if (lower.contains("private") || lower.contains("exclusive") || lower.contains("subscriber")) {
            throw PrivateOrRestrictedException(platform)
        }

        throw PublicMediaPageException(platform, url)
    }
}
