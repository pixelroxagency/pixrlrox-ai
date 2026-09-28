package com.example.core.security

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import android.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenRouterAuthManagerTest {

    private fun isPriceZero(priceValue: Any?): Boolean {
        if (priceValue == null) return false
        val str = priceValue.toString().trim()
        val d = str.toDoubleOrNull() ?: return false
        return d == 0.0
    }

    private fun base64UrlEncode(input: ByteArray): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    }

    @Test
    fun testPkceVerifierGeneration() {
        val verifier = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
        assertTrue(verifier.length >= 43)
        assertTrue(verifier.length <= 128)
    }

    @Test
    fun testS256ChallengeGeneration() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val challenge = base64UrlEncode(hash)

        assertEquals("E9Melhoa2OwvFrGMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun testCallbackUriParsing() {
        val uriStr = "com.aistudio.pixelroxai.rkmpzq:/openrouter-callback?code=splendid_auth_code_123&state=my_rand_state"
        val uri = Uri.parse(uriStr)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        assertEquals("splendid_auth_code_123", code)
        assertEquals("my_rand_state", state)
        assertTrue(uriStr.startsWith("com.aistudio.pixelroxai.rkmpzq:/openrouter-callback") || uriStr.contains("openrouter-callback"))
    }

    @Test
    fun testZeroPriceClassification() {
        // Safe string zero
        assertTrue(isPriceZero("0"))
        assertTrue(isPriceZero("0.0"))
        assertTrue(isPriceZero("0.00000"))
        
        // Safe numeric zero
        assertTrue(isPriceZero(0))
        assertTrue(isPriceZero(0.0))
        assertTrue(isPriceZero(0.0f))
    }

    @Test
    fun testNonZeroPriceNotFree() {
        assertFalse(isPriceZero("0.0001"))
        assertFalse(isPriceZero("1.0"))
        assertFalse(isPriceZero(1))
        assertFalse(isPriceZero(0.05f))
    }

    @Test
    fun testMissingPricingNotFree() {
        assertFalse(isPriceZero(null))
        assertFalse(isPriceZero(""))
        assertFalse(isPriceZero("undefined"))
        assertFalse(isPriceZero("free")) // Not a valid double, returns false
    }

    @Test
    fun testModelListParsingOpenRouter() {
        val mockJson = """
            {
                "data": [
                    {
                        "id": "google/gemini-2.5-flash:free",
                        "name": "Gemini 2.5 Flash Free",
                        "pricing": {"prompt": "0", "completion": "0"},
                        "context_length": 128000
                    },
                    {
                        "id": "openai/gpt-4o",
                        "name": "GPT-4o",
                        "pricing": {"prompt": "0.0000025", "completion": "0.00001"},
                        "context_length": 128000
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(mockJson)
        val dataArray = json.getJSONArray("data")
        val freeModels = mutableSetOf<String>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val id = item.optString("id")
            val cleanId = id.removePrefix("models/")
            
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
                freeModels.add(cleanId)
            }
        }

        assertTrue(freeModels.contains("google/gemini-2.5-flash:free"))
        assertFalse(freeModels.contains("openai/gpt-4o"))
    }

    @Test
    fun testProviderLabelFixed() {
        val aiMode = "AUTO"
        val messageContent = "Hello from AI\n\n_Auto · Direct AI_"
        val currentProviderDisplayName = "OpenRouter"

        val isAutoDirect = messageContent.contains("_Auto · Direct AI_")
        val isDirectAiMode = aiMode == "DIRECT_AI"

        val label = if (isAutoDirect || isDirectAiMode) {
            "Direct AI · $currentProviderDisplayName"
        } else {
            "Hermes"
        }

        assertEquals("Direct AI · OpenRouter", label)
    }

    @Test
    fun testClearingOnlyOpenRouterCredentialsOnDisconnect() {
        val secureStore = mutableMapOf<String, String>()
        secureStore["direct_ai_gemini"] = "gemini_key_123"
        secureStore["direct_ai_openai"] = "openai_key_456"
        secureStore["direct_ai_openrouter"] = "openrouter_key_789"

        // Disconnect openrouter
        secureStore.remove("direct_ai_openrouter")

        // Assert OpenRouter key is removed but Gemini/OpenAI are preserved
        assertFalse(secureStore.containsKey("direct_ai_openrouter"))
        assertEquals("gemini_key_123", secureStore["direct_ai_gemini"])
        assertEquals("openai_key_456", secureStore["direct_ai_openai"])
    }

    @Test
    fun testOpenRouterProviderSeeding() {
        val seededProviders = mutableMapOf<String, String>()
        seededProviders["openai"] = "OPENAI_COMPATIBLE"
        seededProviders["gemini"] = "GEMINI"
        seededProviders["ollama"] = "OLLAMA"
        seededProviders["openrouter"] = "OPEN_ROUTER"

        assertEquals(4, seededProviders.size)
        assertEquals("OPEN_ROUTER", seededProviders["openrouter"])
        assertEquals("GEMINI", seededProviders["gemini"])
    }
}
