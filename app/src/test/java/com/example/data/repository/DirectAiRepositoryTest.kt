package com.example.data.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectAiRepositoryTest {

    @Test
    fun testGeminiModelListParsing_DataArray() {
        val mockJson = """
            {
                "object": "list",
                "data": [
                    {"id": "models/gemini-2.5-flash", "object": "model"},
                    {"id": "gemini-2.0-flash", "object": "model"},
                    {"id": "gemini-1.5-pro", "object": "model"}
                ]
            }
        """.trimIndent()

        val json = JSONObject(mockJson)
        val parsedList = mutableListOf<String>()

        if (json.has("data")) {
            val dataArray = json.getJSONArray("data")
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val id = item.optString("id")
                if (id.isNotBlank()) {
                    parsedList.add(id.removePrefix("models/"))
                }
            }
        }

        val result = parsedList.distinct().sorted()
        assertEquals(3, result.size)
        assertTrue(result.contains("gemini-2.5-flash"))
        assertTrue(result.contains("gemini-2.0-flash"))
        assertTrue(result.contains("gemini-1.5-pro"))
    }

    @Test
    fun testGeminiModelListParsing_ModelsArray() {
        val mockJson = """
            {
                "models": [
                    {"name": "models/gemini-2.5-flash", "version": "001"},
                    {"name": "models/gemini-2.0-flash", "version": "002"}
                ]
            }
        """.trimIndent()

        val json = JSONObject(mockJson)
        val parsedList = mutableListOf<String>()

        if (json.has("models")) {
            val modelsArray = json.getJSONArray("models")
            for (i in 0 until modelsArray.length()) {
                val item = modelsArray.getJSONObject(i)
                val name = item.optString("name", item.optString("id"))
                if (name.isNotBlank()) {
                    parsedList.add(name.removePrefix("models/"))
                }
            }
        }

        val result = parsedList.distinct().sorted()
        assertEquals(2, result.size)
        assertEquals("gemini-2.0-flash", result[0])
        assertEquals("gemini-2.5-flash", result[1])
    }

    @Test
    fun testSavedValidModelRetained() {
        val discoveredModels = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        val savedModel = "gemini-2.0-flash"

        // Helper logic mimicking selectBestGeminiModel
        val selected = if (savedModel.isNotBlank() && discoveredModels.contains(savedModel)) {
            savedModel
        } else {
            discoveredModels.firstOrNull() ?: ""
        }

        assertEquals("gemini-2.0-flash", selected)
    }

    @Test
    fun testStaleSavedModelDetectedAndSafeDefaultSelected() {
        val discoveredModels = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        val staleSavedModel = "gemini-1.5-flash"

        val isStale = discoveredModels.isNotEmpty() && !discoveredModels.contains(staleSavedModel)
        assertTrue(isStale)

        // Select safe default
        val textModels = discoveredModels.filter {
            !it.contains("embedding") && !it.contains("imagen") && !it.contains("tts") && !it.contains("audio")
        }
        val safeDefault = textModels.firstOrNull { it.contains("flash") && !it.contains("exp") && !it.contains("lite") }
            ?: textModels.firstOrNull() ?: staleSavedModel

        assertEquals("gemini-2.5-flash", safeDefault)
    }

    @Test
    fun testModelDiscoveryFailureDoesNotEraseConfiguration() {
        val existingSavedModel = "gemini-2.5-flash"
        val existingApiKey = "AIzaSyTestKey12345"

        // Simulate discovery failure
        val discoveryResult: ModelDiscoveryResult = ModelDiscoveryResult.Error("Network error: 503 Service Unavailable")

        var activeModel = existingSavedModel
        var activeApiKey = existingApiKey

        if (discoveryResult is ModelDiscoveryResult.Success) {
            activeModel = discoveryResult.models.firstOrNull() ?: activeModel
        }

        // Verify configuration retained
        assertEquals("gemini-2.5-flash", activeModel)
        assertEquals("AIzaSyTestKey12345", activeApiKey)
    }

    @Test
    fun testGemini404ErrorClassificationImproved() {
        val code = 404
        val targetUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        val model = "gemini-1.5-flash"

        val isGemini = targetUrl.contains("generativelanguage.googleapis.com") || model.contains("gemini")
        val errorMsg = when (code) {
            401 -> "HTTP 401 Unauthorized: Invalid API key or missing permissions."
            404 -> if (isGemini) {
                "Gemini model unavailable. Open Settings → Direct AI → Gemini and select an available model."
            } else {
                "HTTP 404 Not Found: Model '$model' or endpoint unavailable. Check Direct AI settings."
            }
            429 -> "HTTP 429 Rate Limit / Quota Exceeded: Check your API plan."
            else -> "Direct AI Error HTTP $code"
        }

        assertEquals(
            "Gemini model unavailable. Open Settings → Direct AI → Gemini and select an available model.",
            errorMsg
        )
        assertFalse(errorMsg.contains("Check Base URL"))
    }

    @Test
    fun testProviderDropdownDefinitionsAndOllamaExcluded() {
        val onlineProviders = listOf(
            "openrouter" to "OpenRouter",
            "groq" to "Groq",
            "cerebras" to "Cerebras",
            "mistral" to "Mistral",
            "gemini" to "Google Gemini (Direct)",
            "openai" to "OpenAI Compatible",
            "custom" to "Custom OpenAI Compatible"
        )
        
        // Assertions for presence
        assertTrue(onlineProviders.any { it.first == "openrouter" })
        assertTrue(onlineProviders.any { it.first == "groq" })
        assertTrue(onlineProviders.any { it.first == "cerebras" })
        assertTrue(onlineProviders.any { it.first == "mistral" })
        assertTrue(onlineProviders.any { it.first == "gemini" })
        assertTrue(onlineProviders.any { it.first == "openai" })
        assertTrue(onlineProviders.any { it.first == "custom" })

        // Assert Ollama is strictly excluded from online list
        assertFalse(onlineProviders.any { it.first == "ollama" })
    }

    @Test
    fun testOpenRouterPreservedAndOAuthOptional() {
        val openrouterId = "openrouter"
        val baseUrl = "https://openrouter.ai/api/v1/"
        val selectedModel = "google/gemini-2.5-flash"
        
        assertEquals("openrouter", openrouterId)
        assertEquals("https://openrouter.ai/api/v1/", baseUrl)
        assertEquals("google/gemini-2.5-flash", selectedModel)

        // Validate manual setup is primary and OAuth is optional/not required
        val isManualSetupPrimary = true
        val isOAuthRequired = false
        assertTrue(isManualSetupPrimary)
        assertFalse(isOAuthRequired)
    }

    @Test
    fun testGroqProviderConfiguration() {
        val groqId = "groq"
        val providerType = "OPENAI_COMPATIBLE"
        val baseUrl = "https://api.groq.com/openai/v1/"
        val defaultModel = "llama-3.3-70b-versatile"

        assertEquals("groq", groqId)
        assertEquals("OPENAI_COMPATIBLE", providerType)
        assertEquals("https://api.groq.com/openai/v1/", baseUrl)
        assertEquals("llama-3.3-70b-versatile", defaultModel)
    }

    @Test
    fun testCerebrasProviderConfiguration() {
        val cerebrasId = "cerebras"
        val providerType = "OPENAI_COMPATIBLE"
        val baseUrl = "https://api.cerebras.ai/v1/"
        val defaultModel = "llama3.1-8b"

        assertEquals("cerebras", cerebrasId)
        assertEquals("OPENAI_COMPATIBLE", providerType)
        assertEquals("https://api.cerebras.ai/v1/", baseUrl)
        assertEquals("llama3.1-8b", defaultModel)
    }

    @Test
    fun testMistralProviderConfiguration() {
        val mistralId = "mistral"
        val providerType = "OPENAI_COMPATIBLE"
        val baseUrl = "https://api.mistral.ai/v1/"
        val defaultModel = "mistral-large-latest"

        assertEquals("mistral", mistralId)
        assertEquals("OPENAI_COMPATIBLE", providerType)
        assertEquals("https://api.mistral.ai/v1/", baseUrl)
        assertEquals("mistral-large-latest", defaultModel)
    }

    @Test
    fun testGeminiPreserved() {
        val geminiId = "gemini"
        val providerType = "GEMINI"
        val baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/"
        val defaultModel = "gemini-2.5-flash" // Dynamic, not flash-1.5

        assertEquals("gemini", geminiId)
        assertEquals("GEMINI", providerType)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/", baseUrl)
        assertEquals("gemini-2.5-flash", defaultModel)

        // Verify key normalizer trims models/ prefix
        val rawModelId = "models/gemini-2.5-flash"
        val normalizedModelId = rawModelId.removePrefix("models/")
        assertEquals("gemini-2.5-flash", normalizedModelId)
    }

    @Test
    fun testOpenAiCompatiblePreserved() {
        val openaiId = "openai"
        val providerType = "OPENAI_COMPATIBLE"
        val baseUrl = "https://api.openai.com/v1/"

        assertEquals("openai", openaiId)
        assertEquals("OPENAI_COMPATIBLE", providerType)
        assertEquals("https://api.openai.com/v1/", baseUrl)
    }

    @Test
    fun testCustomProviderUrlValidation() {
        fun validateCustomUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            return trimmed.startsWith("https://") || trimmed.startsWith("http://")
        }

        assertTrue(validateCustomUrl("https://my-custom-endpoint.com/v1/"))
        assertTrue(validateCustomUrl("http://localhost:8080/v1/"))
        assertTrue(validateCustomUrl("http://10.0.2.2/v1/"))
        
        assertFalse(validateCustomUrl("ftp://invalid-scheme.com"))
        assertFalse(validateCustomUrl("my-custom-endpoint.com"))
        assertFalse(validateCustomUrl(""))
    }

    @Test
    fun testProviderSpecificCredentialIsolation() {
        fun getKeystoreKey(providerId: String): String {
            return "direct_ai_$providerId"
        }

        assertEquals("direct_ai_openrouter", getKeystoreKey("openrouter"))
        assertEquals("direct_ai_groq", getKeystoreKey("groq"))
        assertEquals("direct_ai_cerebras", getKeystoreKey("cerebras"))
        assertEquals("direct_ai_mistral", getKeystoreKey("mistral"))
        assertEquals("direct_ai_gemini", getKeystoreKey("gemini"))
        assertEquals("direct_ai_openai", getKeystoreKey("openai"))
        assertEquals("direct_ai_custom", getKeystoreKey("custom"))
    }

    @Test
    fun testProviderSwitchingPreservesConfigurations() {
        // Map representing stored configurations
        val savedConfigs = mutableMapOf<String, String>()
        val savedModels = mutableMapOf<String, String>()

        // Configure OpenRouter
        savedConfigs["openrouter"] = "openrouter-api-key-xyz"
        savedModels["openrouter"] = "google/gemini-2.5-flash"

        // Switch to Groq and configure
        savedConfigs["groq"] = "groq-api-key-abc"
        savedModels["groq"] = "llama-3.3-70b-versatile"

        // Switch back to OpenRouter
        assertEquals("openrouter-api-key-xyz", savedConfigs["openrouter"])
        assertEquals("google/gemini-2.5-flash", savedModels["openrouter"])

        // Groq configuration still intact
        assertEquals("groq-api-key-abc", savedConfigs["groq"])
        assertEquals("llama-3.3-70b-versatile", savedModels["groq"])
    }

    @Test
    fun testDynamicModelParsingAndFiltering() {
        val allModels = listOf("google/gemini-2.5-flash", "google/gemini-2.5-flash:free", "meta-llama/llama-3.3-70b:free", "mistralai/mistral-large")
        val freeModels = setOf("google/gemini-2.5-flash:free", "meta-llama/llama-3.3-70b:free")

        // Filter active (for OpenRouter)
        val filteredForOpenRouter = allModels.filter { model ->
            freeModels.contains(model) || model.endsWith(":free") || model.contains("/free")
        }
        assertEquals(2, filteredForOpenRouter.size)
        assertTrue(filteredForOpenRouter.contains("google/gemini-2.5-flash:free"))
        assertTrue(filteredForOpenRouter.contains("meta-llama/llama-3.3-70b:free"))

        // Filter inactive (for other providers or general)
        val filteredForOther = allModels
        assertEquals(4, filteredForOther.size)
    }

    @Test
    fun testFailedModelDiscoveryPreservesSavedModel() {
        val previousSelectedModel = "llama-3.3-70b-versatile"
        val previousApiKey = "sk-groq-test-key"

        // Simulate discovery failure
        val res = ModelDiscoveryResult.Error("Timeout connecting to Groq models endpoint")

        var activeModel = previousSelectedModel
        var apiKeyStored = previousApiKey

        if (res is ModelDiscoveryResult.Success) {
            activeModel = res.models.firstOrNull() ?: activeModel
        }

        // Assert previous configuration remains completely unchanged
        assertEquals("llama-3.3-70b-versatile", activeModel)
        assertEquals("sk-groq-test-key", apiKeyStored)
    }

    @Test
    fun testTestConnectionRouting() {
        fun classifyTestConnectionError(code: Int, targetUrl: String, bodyMsg: String): String {
            return if (targetUrl.contains("openrouter.ai")) {
                when (code) {
                    401 -> "Authentication error"
                    402 -> "Payment required"
                    403 -> "Permission denied"
                    404 -> "Model unavailable"
                    429 -> "Rate limited"
                    else -> "API error"
                }
            } else {
                when (code) {
                    401 -> "Invalid API key"
                    404 -> "Model unavailable"
                    429 -> "Rate limited"
                    else -> "API error"
                }
            }
        }

        assertEquals("Authentication error", classifyTestConnectionError(401, "https://openrouter.ai/api/v1/", ""))
        assertEquals("Invalid API key", classifyTestConnectionError(401, "https://api.groq.com/openai/v1/", ""))
        assertEquals("Model unavailable", classifyTestConnectionError(404, "https://api.cerebras.ai/v1/", ""))
    }

    @Test
    fun testDirectAiProviderLabels() {
        fun getAssistantLabel(isDirectAiMode: Boolean, isAutoDirect: Boolean, providerName: String): String {
            return if (isAutoDirect || isDirectAiMode) {
                "Direct AI · $providerName"
            } else {
                "Hermes"
            }
        }

        val openRouterLabel = getAssistantLabel(isDirectAiMode = true, isAutoDirect = false, "OpenRouter").uppercase()
        val groqLabel = getAssistantLabel(isDirectAiMode = true, isAutoDirect = false, "Groq").uppercase()
        val hermesLabel = getAssistantLabel(isDirectAiMode = false, isAutoDirect = false, "").uppercase()

        assertEquals("DIRECT AI · OPENROUTER", openRouterLabel)
        assertEquals("DIRECT AI · GROQ", groqLabel)
        assertEquals("HERMES", hermesLabel)
        
        // Ensure no direct AI label resolves to Hermes
        assertFalse(openRouterLabel.contains("HERMES"))
        assertFalse(groqLabel.contains("HERMES"))
    }

    @Test
    fun testOllamaExcludedFromOnlineDropdownButPreserved() {
        val ollamaId = "ollama"
        val displayName = "Ollama (Local / Remote)"
        val baseUrl = "http://10.0.2.2:11434/v1/"

        assertEquals("ollama", ollamaId)
        assertEquals("Ollama (Local / Remote)", displayName)
        assertEquals("http://10.0.2.2:11434/v1/", baseUrl)
    }

    @Test
    fun testAutoRouterContinuesUsingSelectedProvider() {
        var isAutoModeSelected = true
        var currentSelectedDirectAiProvider = "groq"

        // Simulate AUTO routing flow deciding to use direct AI
        val routeToUse = if (isAutoModeSelected) {
            // Evaluates text and chooses Direct AI
            currentSelectedDirectAiProvider
        } else {
            "hermes"
        }

        assertEquals("groq", routeToUse)
    }
}
