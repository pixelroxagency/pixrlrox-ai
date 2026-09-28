#!/bin/bash
sed -i '/fun getApiKey(profileId: String): String {/i \
    fun encryptData(data: ByteArray): ByteArray? {\n\
        try {\n\
            val secretKey = getSecretKey() ?: return null\n\
            val cipher = Cipher.getInstance(transformation)\n\
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)\n\
            val iv = cipher.iv\n\
            val encryptedBytes = cipher.doFinal(data)\n\
            val combined = ByteArray(iv.size + encryptedBytes.size)\n\
            System.arraycopy(iv, 0, combined, 0, iv.size)\n\
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)\n\
            return combined\n\
        } catch (_: Exception) { return null }\n\
    }\n\
\n\
    fun decryptData(data: ByteArray): ByteArray? {\n\
        try {\n\
            if (data.size <= ivSize) return null\n\
            val iv = data.copyOfRange(0, ivSize)\n\
            val encryptedBytes = data.copyOfRange(ivSize, data.size)\n\
            val secretKey = getSecretKey() ?: return null\n\
            val cipher = Cipher.getInstance(transformation)\n\
            val spec = GCMParameterSpec(gcmTagLength, iv)\n\
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)\n\
            return cipher.doFinal(encryptedBytes)\n\
        } catch (_: Exception) { return null }\n\
    }\n\
' app/src/main/java/com/example/core/security/KeystoreSecretManager.kt
