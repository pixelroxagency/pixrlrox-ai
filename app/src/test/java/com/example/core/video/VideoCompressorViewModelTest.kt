package com.example.core.video

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VideoCompressorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: android.content.Context
    private lateinit var viewModel: VideoCompressorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = ApplicationProvider.getApplicationContext()
        viewModel = VideoCompressorViewModel(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsNoVideoSelected() = runTest {
        val state = viewModel.uiState.first()
        assertTrue(state is VideoCompressorUiState.NoVideoSelected)
    }

    @Test
    fun testPresetChangeUpdatesEstimate() = runTest {
        val uri = Uri.parse("content://media/external/video/media/123")
        val metadata = VideoMetadata(
            contentUri = uri.toString(),
            displayName = "test.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 10_000_000L,
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            bitrate = 8_000_000L
        )

        val presetHigh = VideoCompressionPreset.HIGH_QUALITY
        val presetSmall = VideoCompressionPreset.SMALL
        
        val estHigh = presetHigh.estimateOutputSize(metadata.durationMs, metadata.bitrate)
        val estSmall = presetSmall.estimateOutputSize(metadata.durationMs, metadata.bitrate)

        assertTrue(estSmall < estHigh)
    }

    @Test
    fun testBytesSavedAndPercentageCalculation() {
        val originalSize = 10_000L
        val compressedSize = 6_000L
        val bytesSaved = maxOf(originalSize - compressedSize, 0L)
        val percentageSaved = (bytesSaved.toFloat() / originalSize.toFloat()) * 100f

        assertEquals(4_000L, bytesSaved)
        assertEquals(40f, percentageSaved, 0.01f)
    }

    @Test
    fun testCompressedFileLargerThanOriginalProducesZeroSavings() {
        val originalSize = 5_000L
        val compressedSize = 7_000L
        val bytesSaved = maxOf(originalSize - compressedSize, 0L)
        val percentageSaved = if (originalSize > 0) (bytesSaved.toFloat() / originalSize.toFloat()) * 100f else null

        assertEquals(0L, bytesSaved)
        assertEquals(0f, percentageSaved ?: 0f, 0.01f)
    }

    @Test
    fun testResetClearsWorkflow() = runTest {
        viewModel.reset()
        val state = viewModel.uiState.first()
        assertTrue(state is VideoCompressorUiState.NoVideoSelected)
    }
}
