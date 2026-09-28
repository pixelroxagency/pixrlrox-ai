package com.example.core.video

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VideoCompressorEngineThreadAffinityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var engine: VideoCompressorEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        engine = VideoCompressorEngine(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCancelFromBackgroundThreadDoesNotThrow() = runTest {
        withContext(Dispatchers.IO) {
            engine.cancel()
        }
    }

    @Test
    fun testCancelFromMainThreadDoesNotThrow() = runTest {
        withContext(Dispatchers.Main) {
            engine.cancel()
        }
    }

    @Test
    fun testCompressVideoWithInvalidUriFailsGracefullyWithoutThreadViolation() = runTest {
        val nonExistentUri = Uri.parse("content://media/external/video/media/99999999")
        val result = engine.compressVideo(
            sourceUri = nonExistentUri,
            preset = VideoCompressionPreset.SMALL,
            onProgress = {}
        )
        assertTrue(
            "Expected Failure or Cancelled, but got $result",
            result is VideoCompressionResult.Failure || result is VideoCompressionResult.Cancelled
        )
        if (result is VideoCompressionResult.Failure) {
            assertFalse(
                "Error must not be a thread affinity error: ${result.errorMessage}",
                result.errorMessage.contains("Transformer is accessed on the wrong thread")
            )
        }
    }
}
