package com.example.data.repository

import com.example.core.database.dao.DirectProviderDao
import com.example.core.database.entity.DirectProviderEntity
import com.example.core.network.ChatCompletionRequest
import com.example.core.network.DirectAiApiClient
import com.example.core.security.KeystoreSecretManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

sealed class ModelDiscoveryResult {
    data class Success(val models: List<String>) : ModelDiscoveryResult()
    data class Error(val message: String) : ModelDiscoveryResult()
}

sealed class DirectAiTestResult {
    object Success : DirectAiTestResult()
    data class Error(val category: String, val message: String) : DirectAiTestResult()
}

enum class AiMode {
    AUTO,         // Intelligent local router (Recommended)
    DIRECT_AI     // Direct Provider REST
}

class DirectAiRepository(
    private val directProviderDao: DirectProviderDao,
    private val secretManager: KeystoreSecretManager,
    private val directAiApiClient: DirectAiApiClient,
    private val preferencesRepository: PreferencesRepository,
    private val context: android.content.Context
) {

    private val openRouterPrefs = context.getSharedPreferences("openrouter_preferences", android.content.Context.MODE_PRIVATE)

    private val _freeModels = MutableStateFlow<Set<String>>(emptySet())
    val freeModels: StateFlow<Set<String>> = _freeModels.asStateFlow()

    fun isOpenRouterFreeFilterEnabled(): Boolean {
        return openRouterPrefs.getBoolean("free_filter_enabled", true)
    }

    fun setOpenRouterFreeFilterEnabled(enabled: Boolean) {
        openRouterPrefs.edit().putBoolean("free_filter_enabled", enabled).apply()
    }

    fun disconnectOpenRouter() {
        secretManager.removeApiKey("direct_ai_openrouter")
        openRouterPrefs.edit().clear().apply()
        _freeModels.value = emptySet()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val existing = directProviderDao.getProviderById("openrouter")
            if (existing != null) {
                directProviderDao.insert(existing.copy(isConfigured = false))
            }
        }
    }

    private val _aiMode = MutableStateFlow(AiMode.AUTO)
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    private val _currentProvider = MutableStateFlow<DirectProviderEntity?>(null)
    val currentProvider: StateFlow<DirectProviderEntity?> = _currentProvider.asStateFlow()

    fun getAllProviders(): Flow<List<DirectProviderEntity>> = directProviderDao.getAllProviders()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Seed default providers if empty
        val defaultProv = directProviderDao.getDefaultProvider()
        if (defaultProv == null) {
            val openai = DirectProviderEntity(
                id = "openai",
                displayName = "OpenAI Compatible",
                providerType = "OPENAI_COMPATIBLE",
                baseUrl = "https://api.openai.com/v1/",
                selectedModel = "gpt-4o-mini",
                isConfigured = false,
                isDefault = true
            )
            val gemini = DirectProviderEntity(
                id = "gemini",
                displayName = "Google Gemini (Direct)",
                providerType = "GEMINI",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                selectedModel = "gemini-2.5-flash",
                isConfigured = false,
                isDefault = false
            )
            val ollama = DirectProviderEntity(
                id = "ollama",
                displayName = "Ollama (Local / Remote)",
                providerType = "OLLAMA",
                baseUrl = "http://10.0.2.2:11434/v1/",
                selectedModel = "llama3",
                isConfigured = false,
                isDefault = false
            )
            val openrouter = DirectProviderEntity(
                id = "openrouter",
                displayName = "OpenRouter",
                providerType = "OPEN_ROUTER",
                baseUrl = "https://openrouter.ai/api/v1/",
                selectedModel = "google/gemini-2.5-flash",
                isConfigured = false,
                isDefault = false
            )
            val groq = DirectProviderEntity(
                id = "groq",
                displayName = "Groq",
                providerType = "OPENAI_COMPATIBLE",
                baseUrl = "https://api.groq.com/openai/v1/",
                selectedModel = "llama-3.3-70b-versatile",
                isConfigured = false,
                isDefault = false
            )
            val cerebras = DirectProviderEntity(
                id = "cerebras",
                displayName = "Cerebras",
                providerType = "OPENAI_COMPATIBLE",
                baseUrl = "https://api.cerebras.ai/v1/",
                selectedModel = "llama3.1-8b",
                isConfigured = false,
                isDefault = false
            )
            val mistral = DirectProviderEntity(
                id = "mistral",
                displayName = "Mistral",
                providerType = "OPENAI_COMPATIBLE",
                baseUrl = "https://api.mistral.ai/v1/",
                selectedModel = "mistral-large-latest",
                isConfigured = false,
                isDefault = false
            )
            val custom = DirectProviderEntity(
                id = "custom",
                displayName = "Custom OpenAI Compatible",
                providerType = "OPENAI_COMPATIBLE",
                baseUrl = "https://api.example.com/v1/",
                selectedModel = "custom-model",
                isConfigured = false,
                isDefault = false
            )
            directProviderDao.insert(openai)
            directProviderDao.insert(gemini)
            directProviderDao.insert(ollama)
            directProviderDao.insert(openrouter)
            directProviderDao.insert(groq)
            directProviderDao.insert(cerebras)
            directProviderDao.insert(mistral)
            directProviderDao.insert(custom)
            _currentProvider.value = openai
        } else {
            // Ensure gemini, ollama, and openrouter exist if app was upgraded
            if (directProviderDao.getProviderById("gemini") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "gemini",
                        displayName = "Google Gemini (Direct)",
                        providerType = "GEMINI",
                        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                        selectedModel = "gemini-2.5-flash",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("ollama") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "ollama",
                        displayName = "Ollama (Local / Remote)",
                        providerType = "OLLAMA",
                        baseUrl = "http://10.0.2.2:11434/v1/",
                        selectedModel = "llama3",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("openrouter") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "openrouter",
                        displayName = "OpenRouter",
                        providerType = "OPEN_ROUTER",
                        baseUrl = "https://openrouter.ai/api/v1/",
                        selectedModel = "google/gemini-2.5-flash",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("groq") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "groq",
                        displayName = "Groq",
                        providerType = "OPENAI_COMPATIBLE",
                        baseUrl = "https://api.groq.com/openai/v1/",
                        selectedModel = "llama-3.3-70b-versatile",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("cerebras") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "cerebras",
                        displayName = "Cerebras",
                        providerType = "OPENAI_COMPATIBLE",
                        baseUrl = "https://api.cerebras.ai/v1/",
                        selectedModel = "llama3.1-8b",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("mistral") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "mistral",
                        displayName = "Mistral",
                        providerType = "OPENAI_COMPATIBLE",
                        baseUrl = "https://api.mistral.ai/v1/",
                        selectedModel = "mistral-large-latest",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }
            if (directProviderDao.getProviderById("custom") == null) {
                directProviderDao.insert(
                    DirectProviderEntity(
                        id = "custom",
                        displayName = "Custom OpenAI Compatible",
                        providerType = "OPENAI_COMPATIBLE",
                        baseUrl = "https://api.example.com/v1/",
                        selectedModel = "custom-model",
                        isConfigured = false,
                        isDefault = false
                    )
                )
            }

            // Auto-fix legacy or broken base URLs stored in database
            val existingGemini = directProviderDao.getProviderById("gemini")
            if (existingGemini != null && (existingGemini.baseUrl.contains("openai/v1") || !existingGemini.baseUrl.contains("openai"))) {
                directProviderDao.insert(existingGemini.copy(baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/"))
            }
            val existingOpenAi = directProviderDao.getProviderById("openai")
            if (existingOpenAi != null && !existingOpenAi.baseUrl.endsWith("v1/")) {
                directProviderDao.insert(existingOpenAi.copy(baseUrl = "https://api.openai.com/v1/"))
            }

            val reloadedDefault = directProviderDao.getDefaultProvider() ?: defaultProv
            _currentProvider.value = reloadedDefault
        }

        // Restore saved AI Mode
        val savedMode = preferencesRepository.getSavedAiMode()
        _aiMode.value = when (savedMode) {
            "AUTO" -> AiMode.AUTO
            "DIRECT_AI" -> AiMode.DIRECT_AI
            "HERMES_AGENT" -> AiMode.DIRECT_AI
            else -> AiMode.AUTO
        }
    }

    suspend fun setAiMode(mode: AiMode) = withContext(Dispatchers.IO) {
        _aiMode.value = mode
        preferencesRepository.setSavedAiMode(mode.name)
    }

    fun isCurrentProviderConfigured(): Boolean {
        val provider = _currentProvider.value ?: return false
        val key = secretManager.getApiKey("direct_ai_${provider.id}")
        return key.isNotBlank() || provider.id == "ollama" || provider.providerType == "OLLAMA"
    }

    fun isProviderConfigured(providerId: String): Boolean {
        val key = secretManager.getApiKey("direct_ai_$providerId")
        return key.isNotBlank() || providerId == "ollama"
    }

    fun getProviderApiKey(providerId: String): String {
        return secretManager.getApiKey("direct_ai_$providerId")
    }

    suspend fun getDefaultProvider(): DirectProviderEntity? = withContext(Dispatchers.IO) {
        directProviderDao.getDefaultProvider() ?: directProviderDao.getProviderById("openai")
    }

    suspend fun selectProvider(providerId: String) = withContext(Dispatchers.IO) {
        directProviderDao.setDefaultProvider(providerId)
        val provider = directProviderDao.getProviderById(providerId)
        _currentProvider.value = provider
    }

    suspend fun saveProviderConfig(
        providerId: String,
        displayName: String,
        baseUrl: String,
        selectedModel: String,
        rawApiKey: String?,
        isDefault: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        secretManager.storeApiKey("direct_ai_$providerId", rawApiKey ?: "")

        val existing = directProviderDao.getProviderById(providerId)
        val savedKey = secretManager.getApiKey("direct_ai_$providerId")
        val isConfig = savedKey.isNotBlank() || providerId == "ollama"

        val updated = DirectProviderEntity(
            id = providerId,
            displayName = displayName.trim(),
            providerType = existing?.providerType ?: when (providerId) {
                "gemini" -> "GEMINI"
                "ollama" -> "OLLAMA"
                "openrouter" -> "OPEN_ROUTER"
                else -> "OPENAI_COMPATIBLE"
            },
            baseUrl = directAiApiClient.sanitizeDirectAiBaseUrl(baseUrl),
            selectedModel = selectedModel.trim().ifBlank {
                existing?.selectedModel ?: when (providerId) {
                    "gemini" -> "gemini-2.5-flash"
                    "ollama" -> "llama3"
                    "openrouter" -> "google/gemini-2.5-flash"
                    else -> "gpt-4o-mini"
                }
            },
            isConfigured = isConfig,
            isDefault = isDefault || (existing?.isDefault == true)
        )
        directProviderDao.insert(updated)

        if (isDefault) {
            directProviderDao.setDefaultProvider(providerId)
        }
        if (_currentProvider.value?.id == providerId || isDefault) {
            _currentProvider.value = updated
        }
        true
    }

    fun streamDirectAiChat(request: ChatCompletionRequest): Flow<String> {
        val provider = _currentProvider.value
            ?: throw IllegalStateException("Direct AI provider not selected")
        val apiKey = secretManager.getApiKey("direct_ai_${provider.id}")
        if (apiKey.isBlank() && provider.providerType != "OLLAMA") {
            throw IllegalStateException("Direct AI is not configured. Please add an API key in Settings.")
        }

        // OpenRouter Safeguards against accidental paid routing
        if (provider.id == "openrouter") {
            val isFreeFilter = isOpenRouterFreeFilterEnabled()
            val model = provider.selectedModel
            val isFree = _freeModels.value.contains(model) || model.endsWith(":free")
            if (isFreeFilter && !isFree) {
                throw IllegalStateException("The selected model '$model' is a paid model, but 'Free Models Only' filter is active. Please select a free model in Settings.")
            }
            if (_freeModels.value.isNotEmpty() && !_freeModels.value.contains(model)) {
                throw IllegalStateException("The selected model '$model' is no longer available as a free model on OpenRouter. Please select an available free model in Settings.")
            }
        }

        return directAiApiClient.streamDirectAi(
            baseUrl = provider.baseUrl,
            apiKey = apiKey,
            request = request.copy(model = provider.selectedModel)
        )
    }

    fun selectBestGeminiModel(models: List<String>, currentSavedModel: String): String {
        if (models.isEmpty()) return currentSavedModel
        if (currentSavedModel.isNotBlank() && models.contains(currentSavedModel)) {
            return currentSavedModel
        }
        val textModels = models.filter {
            !it.contains("embedding") && !it.contains("imagen") && !it.contains("tts") && !it.contains("audio")
        }
        return textModels.firstOrNull { it.contains("flash") && !it.contains("exp") && !it.contains("lite") }
            ?: textModels.firstOrNull { it.contains("flash") }
            ?: textModels.firstOrNull { it.contains("gemini") && !it.contains("exp") }
            ?: textModels.firstOrNull()
            ?: currentSavedModel
    }

    suspend fun fetchAvailableModelsResult(
        baseUrl: String,
        rawApiKey: String,
        providerId: String
    ): ModelDiscoveryResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = secretManager.normalizeApiKey(rawApiKey)
            val cleanBase = directAiApiClient.sanitizeDirectAiBaseUrl(baseUrl)
            if (cleanBase.isEmpty()) {
                return@withContext ModelDiscoveryResult.Error("Base URL is empty")
            }

            val modelsUrl = when {
                cleanBase.endsWith("v1/") || cleanBase.endsWith("openai/") || cleanBase.endsWith("v1beta/") -> "${cleanBase}models"
                else -> "${cleanBase}v1/models"
            }

            val requestBuilder = okhttp3.Request.Builder().url(modelsUrl).get()
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
                requestBuilder.header("x-goog-api-key", apiKey)
            }

            val client = directAiApiClient.buildOkHttpClient(rawApiKeyProvider = { apiKey })
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val errMsg = when (code) {
                        401 -> "HTTP 401 Unauthorized: Invalid API Key"
                        404 -> "HTTP 404 Not Found: Endpoint unavailable"
                        429 -> "HTTP 429 Rate Limit Exceeded"
                        else -> "HTTP $code: ${resp.message}"
                    }
                    return@withContext ModelDiscoveryResult.Error(errMsg)
                }

                val bodyStr = resp.body?.string() ?: return@withContext ModelDiscoveryResult.Error("Empty response body")
                val json = org.json.JSONObject(bodyStr)
                val modelsList = mutableListOf<String>()

                if (json.has("data")) {
                    val dataArray = json.getJSONArray("data")
                    val currentFree = mutableSetOf<String>()
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val id = item.optString("id")
                        if (id.isNotBlank()) {
                            val cleanId = id.removePrefix("models/")
                            modelsList.add(cleanId)

                            val isFreeByName = cleanId.endsWith(":free") || cleanId.contains("/free")
                            val pricing = item.optJSONObject("pricing")
                            val isFreeByPricing = if (pricing != null) {
                                val promptPrice = pricing.opt("prompt")
                                val compPrice = pricing.opt("completion")
                                promptPrice != null && compPrice != null && isPriceZero(promptPrice) && isPriceZero(compPrice)
                            } else {
                                false
                            }

                            if (isFreeByPricing || isFreeByName) {
                                currentFree.add(cleanId)
                            }
                        }
                    }
                    if (providerId == "openrouter") {
                        _freeModels.value = currentFree
                    }
                } else if (json.has("models")) {
                    val modelsArray = json.getJSONArray("models")
                    for (i in 0 until modelsArray.length()) {
                        val item = modelsArray.getJSONObject(i)
                        val name = item.optString("name", item.optString("id"))
                        if (name.isNotBlank()) {
                            modelsList.add(name.removePrefix("models/"))
                        }
                    }
                }

                val distinct = modelsList.distinct().sorted()
                if (distinct.isEmpty()) {
                    ModelDiscoveryResult.Error("No models found in response")
                } else {
                    ModelDiscoveryResult.Success(distinct)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DirectAiRepository", "Error fetching models from $baseUrl", e)
            ModelDiscoveryResult.Error(e.localizedMessage ?: "Failed to fetch models")
        }
    }

    suspend fun fetchAvailableModels(baseUrl: String, rawApiKey: String, providerId: String): List<String> {
        return when (val res = fetchAvailableModelsResult(baseUrl, rawApiKey, providerId)) {
            is ModelDiscoveryResult.Success -> res.models
            is ModelDiscoveryResult.Error -> emptyList()
        }
    }

    suspend fun testDirectAiConnection(
        baseUrl: String,
        rawApiKey: String,
        selectedModel: String,
        providerId: String
    ): DirectAiTestResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = secretManager.normalizeApiKey(rawApiKey)
            if (apiKey.isBlank() && providerId != "ollama") {
                return@withContext DirectAiTestResult.Error("Invalid API key", "API key is required.")
            }
            if (selectedModel.isBlank()) {
                return@withContext DirectAiTestResult.Error("Model unavailable", "Please select or specify a model name.")
            }

            val targetUrl = directAiApiClient.buildDirectAiUrl(baseUrl)
            // For Google Gemini OpenAI-compatible endpoint, ensure we remove "models/" prefix if present because the compatibility layer expects the model ID without prefix
            val finalModel = selectedModel.trim().removePrefix("models/")
            val jsonBody = """{"model":"$finalModel","messages":[{"role":"user","content":"hi"}],"max_tokens":1,"stream":false}"""
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val requestBuilder = okhttp3.Request.Builder()
                .url(targetUrl)
                .post(jsonBody.toRequestBody(mediaType))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
                requestBuilder.header("x-goog-api-key", apiKey)
            }

            val client = directAiApiClient.buildOkHttpClient(rawApiKeyProvider = { apiKey })
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    DirectAiTestResult.Success
                } else {
                    val errBody = try { resp.body?.string()?.trim() ?: "" } catch (_: Exception) { "" }
                    val isOpenRouter = targetUrl.contains("openrouter.ai")
                    if (isOpenRouter) {
                        when (resp.code) {
                            401 -> DirectAiTestResult.Error("Authentication error", "HTTP 401 Unauthorized: OpenRouter authentication or key problem.")
                            402 -> DirectAiTestResult.Error("Payment required", "HTTP 402 Payment Required: Credits/payment required on your OpenRouter account.")
                            403 -> DirectAiTestResult.Error("Permission denied", "HTTP 403 Forbidden: OpenRouter authorization or permission problem.")
                            404 -> DirectAiTestResult.Error("Model unavailable", "HTTP 404 Not Found: Selected model '$selectedModel' is unavailable on OpenRouter.")
                            429 -> DirectAiTestResult.Error("Rate limited", "HTTP 429 Too Many Requests: OpenRouter/model rate limit exceeded.")
                            else -> DirectAiTestResult.Error("API error", "HTTP ${resp.code}: ${errBody.ifEmpty { resp.message }}")
                        }
                    } else {
                        when (resp.code) {
                            401 -> DirectAiTestResult.Error("Invalid API key", "HTTP 401 Unauthorized: Invalid API key or missing permissions.")
                            404 -> DirectAiTestResult.Error(
                                "Model unavailable",
                                if (providerId == "gemini") "Gemini model '$selectedModel' unavailable. Open Settings → Direct AI → Gemini and select an available model."
                                else "Model '$selectedModel' or endpoint unavailable."
                            )
                            429 -> DirectAiTestResult.Error("Rate limited", "HTTP 429: API rate limit or quota exceeded.")
                            else -> DirectAiTestResult.Error("API error", "HTTP ${resp.code}: ${errBody.ifEmpty { resp.message }}")
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            DirectAiTestResult.Error("Network error", "Failed to connect to server: ${e.localizedMessage ?: "Network error"}")
        } catch (e: Exception) {
            DirectAiTestResult.Error("API error", e.localizedMessage ?: "An unexpected error occurred.")
        }
    }

    private fun isPriceZero(priceValue: Any?): Boolean {
        if (priceValue == null) return false
        val str = priceValue.toString().trim()
        val d = str.toDoubleOrNull() ?: return false
        return d == 0.0
    }
}
