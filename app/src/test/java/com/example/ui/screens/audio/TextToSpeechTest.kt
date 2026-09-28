package com.example.ui.screens.audio

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.tts.AndroidTextToSpeechEngine
import com.example.core.audio.tts.ITextToSpeechEngine
import com.example.core.audio.tts.TtsEngineState
import com.example.core.audio.tts.TtsLanguage
import com.example.core.audio.tts.TtsPlaybackStatus
import com.example.core.audio.tts.TtsVoice
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.io.File
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextToSpeechTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fakeEngine: FakeTtsEngine

    class FakeTtsEngine : ITextToSpeechEngine {
        private val _engineState = MutableStateFlow<TtsEngineState>(TtsEngineState.Uninitialized)
        override val engineState: StateFlow<TtsEngineState> = _engineState.asStateFlow()

        private val _playbackStatus = MutableStateFlow<TtsPlaybackStatus>(TtsPlaybackStatus.Idle)
        override val playbackStatus: StateFlow<TtsPlaybackStatus> = _playbackStatus.asStateFlow()

        var lastSpokenText: String? = null
        var lastLocale: Locale? = null
        var lastVoiceName: String? = null
        var lastSpeechRate: Float? = null
        var lastPitch: Float? = null
        var stopCount: Int = 0
        var shutdownCount: Int = 0

        override fun initialize() {
            val eng = TtsLanguage(Locale.US, "English (United States)", "en-US")
            val bng = TtsLanguage(Locale("bn", "BD"), "Bengali (Bangladesh)", "bn-BD")
            val voices = listOf(
                TtsVoice("en-us-x-sfg#female_1-local", Locale.US, "English: Female 1"),
                TtsVoice("en-us-x-sfg#male_1-local", Locale.US, "English: Male 1"),
                TtsVoice("bn-bd-x-bdf#female_1-local", Locale("bn", "BD"), "Bengali: Female 1")
            )
            _engineState.value = TtsEngineState.Ready(listOf(eng, bng), voices)
        }

        override fun speak(
            text: String,
            locale: Locale,
            voiceName: String?,
            speechRate: Float,
            pitch: Float
        ): Boolean {
            if (text.isBlank()) return false
            lastSpokenText = text
            lastLocale = locale
            lastVoiceName = voiceName
            lastSpeechRate = speechRate
            lastPitch = pitch
            _playbackStatus.value = TtsPlaybackStatus.Speaking("utterance_1")
            return true
        }

        override fun stop() {
            stopCount++
            _playbackStatus.value = TtsPlaybackStatus.Stopped
        }

        override suspend fun synthesizeToFile(
            text: String,
            targetFile: File,
            locale: Locale,
            voiceName: String?,
            speechRate: Float,
            pitch: Float
        ): Result<File> {
            if (text.isBlank()) return Result.failure(IllegalArgumentException("Text cannot be empty"))
            targetFile.parentFile?.mkdirs()
            targetFile.writeText("RIFF_WAV_HEADER_AUDIO_BYTES")
            return Result.success(targetFile)
        }

        override fun shutdown() {
            shutdownCount++
            _engineState.value = TtsEngineState.Uninitialized
            _playbackStatus.value = TtsPlaybackStatus.Idle
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        fakeEngine = FakeTtsEngine()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tool catalog removed 4 tools and registered media_text_to_speech`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        // Assert 4 tools are deleted completely
        assertNull("media_meme_maker must be removed", allTools["media_meme_maker"])
        assertNull("media_color_picker must be removed", allTools["media_color_picker"])
        assertNull("media_palette_extractor must be removed", allTools["media_palette_extractor"])
        assertNull("media_image_rotate must be removed", allTools["media_image_rotate"])

        // Assert media_text_to_speech exists with correct active route
        val ttsTool = allTools["media_text_to_speech"]
        assertNotNull("media_text_to_speech must exist", ttsTool)
        assertEquals("text_to_speech", ttsTool?.route)
        assertEquals(Screen.TextToSpeech.route, ttsTool?.route)

        // Assert exact catalog count (113 - 4 = 109)
        assertEquals(109, ToolCatalog.getAllTools().size)
    }

    @Test
    fun `empty text validation blocks speak call and displays error`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearText()
        viewModel.speak()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Error message must be set for empty speech", viewModel.uiState.value.errorMessage)
        assertEquals("Please enter some text to speak", viewModel.uiState.value.errorMessage)
        assertNull("Engine should not have been called with speech", fakeEngine.lastSpokenText)
    }

    @Test
    fun `speech rate bounds clamps values safely between 0_5 and 2_0`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSpeechRate(0.1f)
        assertEquals(0.5f, viewModel.uiState.value.speechRate, 0.001f)

        viewModel.setSpeechRate(3.5f)
        assertEquals(2.0f, viewModel.uiState.value.speechRate, 0.001f)

        viewModel.setSpeechRate(1.25f)
        assertEquals(1.25f, viewModel.uiState.value.speechRate, 0.001f)
    }

    @Test
    fun `pitch bounds clamps values safely between 0_5 and 2_0`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setPitch(0.2f)
        assertEquals(0.5f, viewModel.uiState.value.pitch, 0.001f)

        viewModel.setPitch(4.0f)
        assertEquals(2.0f, viewModel.uiState.value.pitch, 0.001f)

        viewModel.setPitch(1.1f)
        assertEquals(1.1f, viewModel.uiState.value.pitch, 0.001f)
    }

    @Test
    fun `language and voice selection correctly updates filtered voices`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.availableLanguages.size)
        assertEquals(3, state.availableVoices.size)

        // Select Bengali
        val bengali = state.availableLanguages.first { it.locale.language == "bn" }
        viewModel.selectLanguage(bengali)

        val updatedState = viewModel.uiState.value
        assertEquals(bengali, updatedState.selectedLanguage)
        assertEquals(1, updatedState.filteredVoices.size)
        assertEquals("bn-bd-x-bdf#female_1-local", updatedState.selectedVoice?.name)
    }

    @Test
    fun `speaking and stopping transitions playback status correctly`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTextChanged("PixelRox audio test")
        viewModel.setSpeechRate(1.25f)
        viewModel.setPitch(0.9f)
        viewModel.speak()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("PixelRox audio test", fakeEngine.lastSpokenText)
        assertEquals(1.25f, fakeEngine.lastSpeechRate ?: 0f, 0.001f)
        assertEquals(0.9f, fakeEngine.lastPitch ?: 0f, 0.001f)
        assertTrue(viewModel.uiState.value.playbackStatus is TtsPlaybackStatus.Speaking)

        viewModel.stopSpeaking()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeEngine.stopCount)
        assertTrue(viewModel.uiState.value.playbackStatus is TtsPlaybackStatus.Stopped)
    }

    @Test
    fun `text copy to system clipboard works and updates info message`() = runTest {
        val viewModel = TextToSpeechViewModel(application, fakeEngine)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onTextChanged("Synthesized speech clipboard content")
        viewModel.copyTextToClipboard()

        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Synthesized speech clipboard content", clip)
        assertEquals("Text copied to clipboard", viewModel.uiState.value.infoMessage)
    }

    @Test
    fun `export safe audio display name generation adheres to WAV format`() {
        val fileName = AudioOutputPublisher.generateSafeAudioDisplayName(
            rawName = "PixelRox Welcome Speech",
            extension = ".wav",
            timestamp = 1710000000000L
        )
        assertTrue("Filename should start with sanitized title", fileName.startsWith("PixelRox_Welcome_Speech_audio_"))
        assertTrue("Filename should end with .wav", fileName.endsWith(".wav"))
    }

    @Test
    fun `text chunker handles long sentences safely`() {
        val longText = "First sentence is here. Second sentence follows immediately! Third sentence arrives now? Fourth sentence wraps it up."
        val chunks = AndroidTextToSpeechEngine.chunkText(longText, 40)
        assertTrue("Must be split into multiple chunks", chunks.size > 1)
        val reconstructed = chunks.joinToString(" ")
        assertTrue(reconstructed.contains("First sentence"))
        assertTrue(reconstructed.contains("Fourth sentence"))
    }

    @Test
    fun `voice display name formatting produces clear readable label`() {
        val label = AndroidTextToSpeechEngine.formatVoiceDisplayName(
            voiceName = "en-us-x-sfg#female_1-local",
            locale = Locale.US
        )
        assertTrue("Label should format voice gracefully: $label", label.contains("English") || label.contains("female"))
    }
}
