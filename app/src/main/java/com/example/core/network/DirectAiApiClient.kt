package com.example.core.network

import android.util.Log
import com.example.core.security.KeystoreSecretManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class DirectAiApiClient(
    private val secretManager: KeystoreSecretManager
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    fun cancelActiveCall() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
    }

    fun sanitizeDirectAiBaseUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val prefix = if (url.startsWith("https://")) "https://" else "http://"
        val path = url.removePrefix(prefix).replace(Regex("/{2,}"), "/")
        url = "$prefix$path"
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }

    fun buildDirectAiUrl(rawUrl: String): String {
        val clean = sanitizeDirectAiBaseUrl(rawUrl)
        if (clean.isEmpty()) return ""
        return when {
            clean.endsWith("chat/completions/") || clean.endsWith("chat/completions") -> clean.removeSuffix("/")
            clean.endsWith("v1/") || clean.endsWith("openai/") || clean.endsWith("v1beta/") -> "${clean}chat/completions"
            else -> "${clean}v1/chat/completions"
        }
    }

    fun buildOkHttpClient(
        credentialSource: String = "KEYSTORE",
        enteredKeyLength: Int = 0,
        rawApiKeyProvider: () -> String
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val rawKey = rawApiKeyProvider()
            val normalizedKey = secretManager.normalizeApiKey(rawKey)
            val requestBuilder = original.newBuilder()

            val isKeyPresent = normalizedKey.isNotBlank()
            if (isKeyPresent) {
                requestBuilder.header("Authorization", "Bearer $normalizedKey")
            } else {
                requestBuilder.removeHeader("Authorization")
            }
            requestBuilder.header("Accept", "application/json")

            val finalRequest = requestBuilder.build()
            val response = chain.proceed(finalRequest)
            response
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()
    }

    fun streamDirectAi(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<String> = flow {
        val normalizedKey = secretManager.normalizeApiKey(apiKey)
        val client = buildOkHttpClient(rawApiKeyProvider = { normalizedKey })
        val targetUrl = buildDirectAiUrl(baseUrl)

        val finalModel = request.model.trim().removePrefix("models/")
        val jsonBody = moshi.adapter(ChatCompletionRequest::class.java).toJson(request.copy(model = finalModel, stream = true))
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(jsonBody.toRequestBody(mediaType))
            .header("Accept", "text/event-stream")

        if (targetUrl.contains("openrouter.ai")) {
            requestBuilder.header("HTTP-Referer", "https://pixelrox.com/")
            requestBuilder.header("X-Title", "PixelRox AI")
        }

        if (normalizedKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $normalizedKey")
            requestBuilder.header("x-goog-api-key", normalizedKey)
        }

        val httpRequest = requestBuilder.build()
        val call = client.newCall(httpRequest)
        activeCall = call
        var callResponse: Response? = null
        try {
            callResponse = call.execute()
            if (!callResponse.isSuccessful) {
                val errBody = try { callResponse.body?.string()?.trim() ?: "" } catch (_: Exception) { "" }
                val code = callResponse.code
                val isGemini = targetUrl.contains("generativelanguage.googleapis.com") || request.model.contains("gemini")
                val isOpenRouter = targetUrl.contains("openrouter.ai")
                val detailMsg = when {
                    isOpenRouter -> when (code) {
                        401 -> "HTTP 401 Unauthorized: OpenRouter authentication or key problem."
                        402 -> "HTTP 402 Payment Required: Credits/payment required on your OpenRouter account."
                        403 -> "HTTP 403 Forbidden: OpenRouter authorization or permission problem."
                        404 -> "HTTP 404 Not Found: Selected model '${request.model}' is unavailable on OpenRouter."
                        429 -> "HTTP 429 Too Many Requests: OpenRouter/model rate limit exceeded."
                        in 500..599 -> "HTTP $code: OpenRouter provider or service error."
                        else -> "OpenRouter Error HTTP $code: ${errBody.ifEmpty { callResponse.message }}"
                    }
                    else -> when (code) {
                        401 -> "HTTP 401 Unauthorized: Invalid API key or missing permissions."
                        404 -> if (isGemini) {
                            "Gemini model unavailable. Open Settings → Direct AI → Gemini and select an available model."
                        } else {
                            "HTTP 404 Not Found: Model '${request.model}' or endpoint unavailable."
                        }
                        429 -> "HTTP 429 Rate Limit / Quota Exceeded."
                        else -> "Direct AI Error HTTP $code: ${errBody.ifEmpty { callResponse.message }}"
                    }
                }
                throw IllegalStateException(detailMsg)
            }
            val body = callResponse.body ?: throw IllegalStateException("Empty response body from Direct AI")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue
                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.optJSONObject(0)
                            if (firstChoice != null) {
                                val delta = firstChoice.optJSONObject("delta")
                                var content = delta?.optString("content", "") ?: ""
                                if (content.isEmpty()) {
                                    val messageObj = firstChoice.optJSONObject("message")
                                    content = messageObj?.optString("content", "") ?: ""
                                }
                                if (content.isEmpty()) {
                                    content = firstChoice.optString("text", "")
                                }
                                if (content.isNotEmpty()) emit(content)
                            }
                        } else {
                            val resp = json.optString("response", "")
                            if (resp.isNotEmpty()) emit(resp)
                        }
                    } catch (e: Exception) {
                        Log.e("DirectAiApiClient", "Failed to parse Direct AI stream chunk", e)
                    }
                }
            }
        } finally {
            if (activeCall == call) activeCall = null
            try { call.cancel() } catch (_: Exception) {}
            callResponse?.close()
        }
    }.flowOn(Dispatchers.IO)
}
