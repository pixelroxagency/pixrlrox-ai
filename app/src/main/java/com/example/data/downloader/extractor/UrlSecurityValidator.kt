package com.example.data.downloader.extractor

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object UrlSecurityValidator {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun parseScheme(url: String): String? {
        val trimmed = url.trim()
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx == -1) return null
        val scheme = trimmed.substring(0, colonIdx).lowercase().trim()
        if (scheme.isNotEmpty() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
            return scheme
        }
        return null
    }

    fun parseHost(url: String): String? {
        val trimmed = url.trim()
        val scheme = parseScheme(trimmed) ?: return null
        val afterScheme = trimmed.substring(scheme.length + 1).trimStart('/')
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPort = if (authority.contains('@')) authority.substringAfterLast('@') else authority
        val host = hostPort.substringBefore(':').lowercase().trim()
        return host.takeIf { it.isNotBlank() }
    }

    fun isAllowedScheme(scheme: String?): Boolean {
        if (scheme == null) return false
        return scheme.lowercase().trim() in ALLOWED_SCHEMES
    }

    fun isPrivateOrLocalHost(host: String): Boolean {
        val h = host.lowercase().trim()
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
            return true
        }

        return try {
            val addresses = InetAddress.getAllByName(h)
            addresses.any { isPrivateOrLocalAddress(it) }
        } catch (_: Exception) {
            isPrivateIpLiteral(h)
        }
    }

    private fun isPrivateIpLiteral(host: String): Boolean {
        if (host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") return true
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return true
        if (host.startsWith("172.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }
        if (host.startsWith("100.")) {
            val parts = host.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 64..127) return true
            }
        }
        return false
    }

    fun isPrivateOrLocalAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }

        if (address is Inet4Address) {
            val bytes = address.address
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF

            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true
            // 10.0.0.0/8 (Private)
            if (b0 == 10) return true
            // 172.16.0.0/12 (Private: 172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && b1 in 16..31) return true
            // 192.168.0.0/16 (Private)
            if (b0 == 192 && b1 == 168) return true
            // 169.254.0.0/16 (Link-local)
            if (b0 == 169 && b1 == 254) return true
            // 0.0.0.0/8 (Current network)
            if (b0 == 0) return true
            // 100.64.0.0/10 (Shared address space / CGNAT: 100.64.0.0 - 100.127.255.255)
            if (b0 == 100 && (b1 and 0xC0) == 64) return true
        } else if (address is Inet6Address) {
            val bytes = address.address
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF

            // ::1 (Loopback) or :: (Unspecified)
            if (address.isLoopbackAddress || address.isAnyLocalAddress) return true
            // fe80::/10 (Link-local)
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
            // fc00::/7 (Unique local address fc00:: - fdff::)
            if ((b0 and 0xFE) == 0xFC) return true
        }
        return false
    }

    fun validateUrl(url: String): Boolean {
        val trimmed = url.trim()
        val scheme = parseScheme(trimmed) ?: throw IllegalArgumentException("Missing URL scheme.")
        if (scheme !in ALLOWED_SCHEMES) {
            throw SecurityException("URL scheme '$scheme' is not permitted. Only HTTP and HTTPS are allowed.")
        }

        val host = parseHost(trimmed) ?: throw IllegalArgumentException("Missing host in URL.")
        if (host.isBlank()) {
            throw IllegalArgumentException("Host cannot be blank in URL.")
        }

        if (isPrivateOrLocalHost(host)) {
            throw SecurityException("Access to internal, loopback, or private network address ($host) is prohibited.")
        }

        return true
    }

    fun sanitizeFilename(rawTitle: String, fallback: String = "Social_Media_Download"): String {
        var clean = rawTitle
            .replace(Regex("""\.\.[/\\]"""), "_") // path traversal
            .replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_") // invalid filesystem chars & control chars
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.', ' ')

        if (clean.length > 80) {
            clean = clean.substring(0, 80).trim()
        }
        return clean.ifBlank { fallback }
    }
}
