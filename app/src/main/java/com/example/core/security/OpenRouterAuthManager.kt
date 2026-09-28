package com.example.core.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.repository.DirectAiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class OpenRouterAuthManager(
    private val context: Context,
    private val secretManager: KeystoreSecretManager,
    private val directAiRepository: () -> DirectAiRepository
) {
    private val prefs = context.getSharedPreferences("openrouter_auth_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun base64UrlEncode(input: ByteArray): String {
        return try {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(input, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING).trim()
        }
    }

    /**
     * Generates a cryptographically secure code verifier for PKCE.
     */
    fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(43) // Safe minimum length
        random.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    /**
     * Calculates the S256 code challenge from the code verifier:
     * BASE64URL_NO_PADDING(SHA256(code_verifier))
     */
    fun calculateCodeChallenge(verifier: String): String {
        return try {
            val bytes = verifier.toByteArray(Charsets.US_ASCII)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            base64UrlEncode(hash)
        } catch (e: Exception) {
            Log.e("OpenRouterAuthManager", "Failed to generate S256 challenge", e)
            ""
        }
    }

    /**
     * Initiates the official PKCE authorization flow.
     * Generates local state, stores it securely in shared preferences, and launches browser.
     */
    fun startAuthorizationFlow(context: Context) {
        val verifier = generateCodeVerifier()
        val challenge = calculateCodeChallenge(verifier)
        val state = UUID.randomUUID().toString()

        prefs.edit()
            .putString("pending_verifier", verifier)
            .putString("pending_state", state)
            .apply()

        val callbackUrl = "com.aistudio.pixelroxai.rkmpzq:/openrouter-callback"
        val authUrl = "https://openrouter.ai/auth" +
                "?callback_url=${Uri.encode(callbackUrl)}" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256" +
                "&state=$state"

        Log.d("OpenRouterAuthManager", "Launching OpenRouter auth: $authUrl")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Clears all temporary pending PKCE states from preferences.
     */
    fun clearPendingAuthState() {
        prefs.edit()
            .remove("pending_verifier")
            .remove("pending_state")
            .apply()
    }

    /**
     * Exchanges the received authorization code for the user's OpenRouter API key.
     */
    suspend fun exchangeCodeForApiKey(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = prefs.getString("pending_verifier", null)
        if (verifier.isNullOrBlank()) {
            Log.e("OpenRouterAuthManager", "Exchange failed: No pending authorization code verifier found")
            return@withContext false
        }

        val url = "https://openrouter.ai/api/v1/auth/keys"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val payload = JSONObject().apply {
            put("code", code)
            put("code_verifier", verifier)
            put("code_challenge_method", "S256")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val codeErr = resp.code
                    val bodyErr = resp.body?.string() ?: ""
                    Log.e("OpenRouterAuthManager", "Exchange API call failed. HTTP $codeErr: $bodyErr")
                    clearPendingAuthState()
                    return@withContext false
                }

                val bodyStr = resp.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val apiKey = json.optString("key", "")

                if (apiKey.isNotBlank()) {
                    // Store securely using KeystoreSecretManager
                    secretManager.storeApiKey("direct_ai_openrouter", apiKey)
                    
                    // Force the database entity to show as configured and as the default provider
                    val repository = directAiRepository()
                    repository.saveProviderConfig(
                        providerId = "openrouter",
                        displayName = "OpenRouter",
                        baseUrl = "https://openrouter.ai/api/v1/",
                        selectedModel = "google/gemini-2.5-flash",
                        rawApiKey = apiKey,
                        isDefault = true
                    )
                    
                    Log.i("OpenRouterAuthManager", "API Key successfully exchanged and stored securely.")
                    clearPendingAuthState()
                    return@withContext true
                } else {
                    Log.e("OpenRouterAuthManager", "Exchange succeeded but key is empty in payload")
                    clearPendingAuthState()
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRouterAuthManager", "Network failure during OpenRouter key exchange", e)
            clearPendingAuthState()
            return@withContext false
        }
    }
}
