package com.example.core.text

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

enum class HashAlgorithm(val displayName: String, val algorithmName: String, val isLegacy: Boolean) {
    MD5("MD5 (128-bit)", "MD5", true),
    SHA1("SHA-1 (160-bit)", "SHA-1", true),
    SHA256("SHA-256 (256-bit)", "SHA-256", false),
    SHA512("SHA-512 (512-bit)", "SHA-512", false)
}

data class HashResult(
    val algorithm: HashAlgorithm,
    val hexValue: String,
    val bitLength: Int
)

object HashEngine {

    fun hashText(text: String, algorithm: HashAlgorithm): HashResult {
        val digest = MessageDigest.getInstance(algorithm.algorithmName)
        val bytes = digest.digest(text.toByteArray(StandardCharsets.UTF_8))
        val hex = bytesToHex(bytes)
        return HashResult(
            algorithm = algorithm,
            hexValue = hex,
            bitLength = bytes.size * 8
        )
    }

    fun hashAll(text: String): List<HashResult> {
        return HashAlgorithm.entries.map { hashText(text, it) }
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xff
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0f])
        }
        return result.toString()
    }
}
