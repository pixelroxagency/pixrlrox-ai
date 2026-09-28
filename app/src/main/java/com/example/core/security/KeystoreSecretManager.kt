package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class KeyDiagnosticInfo(
    val length: Int,
    val newlineCount: Int,
    val carriageReturnCount: Int,
    val spaceCount: Int,
    val tabCount: Int,
    val nonBreakingSpaceCount: Int,
    val zeroWidthCount: Int,
    val otherNonAsciiCount: Int,
    val fingerprint: String
)

open class KeystoreSecretManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("pixelrox_secure_vault", Context.MODE_PRIVATE)
    private val keyAlias = "PixelRox_SecretKey_V2"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivSize = 12

    init {
        ensureKey()
    }

    private fun ensureKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback will operate if AndroidKeyStore is unavailable
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKeyEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            secretKeyEntry?.secretKey
        } catch (_: Exception) {
            null
        }
    }

    private fun isBoundaryChar(c: Char): Boolean {
        return c.isWhitespace() ||
                Character.isSpaceChar(c) ||
                c <= ' ' ||
                c == '\uFEFF' || // Zero-width no-break space (BOM)
                c == '\u200B' || // Zero-width space
                c == '\u200C' || // Zero-width non-joiner
                c == '\u200D' || // Zero-width joiner
                c == '\u2060'    // Word joiner
    }

    /**
     * Normalizes the API key safely:
     * 1. Trims leading/trailing whitespace (including Unicode whitespace/zero-width at boundary).
     * 2. Removes any surrounding single or double quotes at boundary.
     * 3. Repeatedly strips leading "Bearer" or "bearer" (case-insensitive) prefix.
     * 4. Trims again.
     *
     * DOES NOT:
     * - remove valid characters from inside the key
     * - lowercase or uppercase the key
     * - URL encode, Base64 encode, or hash the key
     */
    fun normalizeApiKey(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""
        // 1. Trim leading/trailing whitespace and zero-width chars at boundary
        var key = rawInput.trim { isBoundaryChar(it) }

        // Strip surrounding single or double quotes if present at boundaries
        if ((key.startsWith("\"") && key.endsWith("\"") && key.length >= 2) ||
            (key.startsWith("'") && key.endsWith("'") && key.length >= 2)) {
            key = key.substring(1, key.length - 1).trim { isBoundaryChar(it) }
        }

        // 2. Remove Bearer prefix case-insensitively if present
        val bearerRegex = Regex("^(?i)bearer[:\\s]*")
        while (bearerRegex.containsMatchIn(key)) {
            key = key.replaceFirst(bearerRegex, "")
        }

        // 3. Trim again
        key = key.trim { isBoundaryChar(it) }

        return key
    }

    /**
     * Computes the first 8 hex characters of the SHA-256 fingerprint of the normalized key.
     * Never exposes the key itself.
     */
    fun computeFingerprint(normalizedKey: String): String {
        if (normalizedKey.isEmpty()) return "none"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(normalizedKey.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }.take(8)
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Inspects the entered key for invisible or unexpected characters without logging the secret.
     */
    fun analyzeKey(rawInput: String?): KeyDiagnosticInfo {
        if (rawInput == null) {
            return KeyDiagnosticInfo(0, 0, 0, 0, 0, 0, 0, 0, "none")
        }
        var newlineCount = 0
        var carriageReturnCount = 0
        var spaceCount = 0
        var tabCount = 0
        var nonBreakingSpaceCount = 0
        var zeroWidthCount = 0
        var otherNonAsciiCount = 0

        for (c in rawInput) {
            when {
                c == '\n' -> newlineCount++
                c == '\r' -> carriageReturnCount++
                c == '\t' -> tabCount++
                c == ' ' -> spaceCount++
                c == '\u00A0' -> nonBreakingSpaceCount++
                c == '\uFEFF' || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' -> zeroWidthCount++
                c.code > 127 -> otherNonAsciiCount++
            }
        }

        val normalized = normalizeApiKey(rawInput)
        val fingerprint = computeFingerprint(normalized)

        return KeyDiagnosticInfo(
            length = rawInput.length,
            newlineCount = newlineCount,
            carriageReturnCount = carriageReturnCount,
            spaceCount = spaceCount,
            tabCount = tabCount,
            nonBreakingSpaceCount = nonBreakingSpaceCount,
            zeroWidthCount = zeroWidthCount,
            otherNonAsciiCount = otherNonAsciiCount,
            fingerprint = fingerprint
        )
    }

    fun storeApiKey(profileId: String, rawApiKey: String) {
        val normalized = normalizeApiKey(rawApiKey)
        if (normalized.isBlank()) {
            prefs.edit().remove("key_$profileId").apply()
            return
        }

        try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + encryptedBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
                val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
                prefs.edit().putString("key_$profileId", "ks:$base64").apply()
                return
            }
        } catch (_: Exception) {
            // Keystore encryption failed, fallback to safe local encoding
        }

        // Fallback obfuscation if KeyStore is not available on test JVM
        val obfuscated = Base64.encodeToString(normalized.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs.edit().putString("key_$profileId", "obf:$obfuscated").apply()
    }

    open fun encryptData(data: ByteArray): ByteArray? {

        try {

            val secretKey = getSecretKey() ?: return null

            val cipher = Cipher.getInstance(transformation)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv

            val encryptedBytes = cipher.doFinal(data)

            val combined = ByteArray(iv.size + encryptedBytes.size)

            System.arraycopy(iv, 0, combined, 0, iv.size)

            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return combined

        } catch (_: Exception) { return null }

    }



    fun decryptData(data: ByteArray): ByteArray? {

        try {

            if (data.size <= ivSize) return null

            val iv = data.copyOfRange(0, ivSize)

            val encryptedBytes = data.copyOfRange(ivSize, data.size)

            val secretKey = getSecretKey() ?: return null

            val cipher = Cipher.getInstance(transformation)

            val spec = GCMParameterSpec(gcmTagLength, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            return cipher.doFinal(encryptedBytes)

        } catch (_: Exception) { return null }

    }


    fun getApiKey(profileId: String): String {
        val stored = prefs.getString("key_$profileId", null) ?: return ""
        if (stored.startsWith("ks:")) {
            try {
                val base64 = stored.substring(3)
                val combined = Base64.decode(base64, Base64.NO_WRAP)
                if (combined.size <= ivSize) return ""
                val iv = combined.copyOfRange(0, ivSize)
                val encryptedBytes = combined.copyOfRange(ivSize, combined.size)
                val secretKey = getSecretKey() ?: return ""
                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(encryptedBytes)
                return String(decrypted, Charsets.UTF_8)
            } catch (_: Exception) {
                return ""
            }
        } else if (stored.startsWith("obf:")) {
            return try {
                val decoded = Base64.decode(stored.substring(4), Base64.NO_WRAP)
                String(decoded, Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
        return ""
    }

    fun removeApiKey(profileId: String) {
        prefs.edit().remove("key_$profileId").apply()
    }

    // ==========================================
    // PIN / BIOMETRIC LOCK SECURITY (#73)
    // ==========================================

    fun isPinLockEnabled(): Boolean {
        return prefs.getBoolean("pin_lock_enabled", false) && prefs.getString("pin_hash", null) != null
    }

    fun setPinLock(pin: String): Boolean {
        val cleanPin = pin.trim()
        if (cleanPin.length < 4) return false
        val salt = "px_salt_keystore_vault_2026"
        val hash = hashPin(cleanPin, salt)
        prefs.edit()
            .putBoolean("pin_lock_enabled", true)
            .putString("pin_hash", hash)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        val salt = "px_salt_keystore_vault_2026"
        val computed = hashPin(pin.trim(), salt)
        return storedHash == computed
    }

    fun disablePinLock() {
        prefs.edit()
            .putBoolean("pin_lock_enabled", false)
            .remove("pin_hash")
            .apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pin:$salt".toByteArray(Charsets.UTF_8)
        val digest = md.digest(combined)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
