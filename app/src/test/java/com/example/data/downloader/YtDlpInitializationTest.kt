package com.example.data.downloader

import com.example.data.downloader.analyzer.ExtractionFailedException
import com.example.data.downloader.extractor.YtDlpErrorSanitizer
import com.example.data.downloader.extractor.YtDlpInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YtDlpInitializationTest {

    @Before
    fun setUp() {
        YtDlpInitializer.resetForTesting()
    }

    @Test
    fun testInitialStateUninitialized() {
        assertFalse(YtDlpInitializer.isReady())
    }

    @Test
    fun testSanitizeUninitializedError() {
        val sanitized = YtDlpErrorSanitizer.sanitize("instance not initialized")
        assertEquals("Media extractor couldn't start. Please try again.", sanitized)

        val sanitized2 = YtDlpErrorSanitizer.sanitize("YoutubeDL instance not initialized. Call init() first.")
        assertEquals("Media extractor couldn't start. Please try again.", sanitized2)
    }

    @Test
    fun testMissingContextFailsGracefully() = runBlocking {
        try {
            YtDlpInitializer.ensureInitialized(null)
            fail("Expected ExtractionFailedException when context is missing")
        } catch (e: ExtractionFailedException) {
            assertEquals("Media extractor couldn't start. Please try again.", e.message)
        }
        assertFalse(YtDlpInitializer.isReady())
    }

    @Test
    fun testConcurrentEnsureInitializedCalls() = runBlocking {
        // Test that multiple concurrent calls to ensureInitialized do not crash or create race conditions
        val deferredList = (1..5).map {
            async(Dispatchers.Default) {
                try {
                    YtDlpInitializer.ensureInitialized(null)
                } catch (e: ExtractionFailedException) {
                    e.message
                }
            }
        }
        val results = deferredList.awaitAll()
        assertEquals(5, results.size)
        assertTrue(results.all { it == "Media extractor couldn't start. Please try again." })
    }

    @Test
    fun testDiagnosticClassification() {
        val err403 = RuntimeException("HTTP Error 403: Forbidden")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.HTTP_403, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(err403))

        val err429 = RuntimeException("HTTP Error 429: Too Many Requests")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.HTTP_429, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(err429))

        val errBot = RuntimeException("Sign in to confirm you're not a bot (captcha required)")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.BOT_CHALLENGE, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(errBot))

        val errLogin = RuntimeException("This account is private. Login required.")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.LOGIN_REQUIRED, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(errLogin))

        val errNative = java.lang.UnsatisfiedLinkError("Cannot dlopen libpython.so")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.NATIVE_LIBRARY_ERROR, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(errNative))

        val errNetwork = java.net.SocketTimeoutException("Connect timed out")
        assertEquals(com.example.data.downloader.extractor.YtDlpFailureCategory.NETWORK_ERROR, com.example.data.downloader.extractor.YtDlpDiagnostics.classifyError(errNetwork))
    }
}
