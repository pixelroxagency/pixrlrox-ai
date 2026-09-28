package com.example.ui.screens.audio

import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioVolumeEngine
import com.example.core.audio.VolumePreset
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioStudioConsolidationTest {

    @Test
    fun `tool catalog registers Audio Studio and removes old 5 source tool cards`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        // 1. Assert Audio Studio card is registered
        val audioStudio = allTools["media_audio_studio"]
        assertNotNull("media_audio_studio must be registered in catalog", audioStudio)
        assertEquals(Screen.AudioStudio.route, audioStudio?.route)
        assertEquals("Audio Studio", audioStudio?.title)

        // 2. Assert old 5 consolidated cards are no longer top-level cards in catalog
        assertNull("media_audio_cut should no longer be a top-level card", allTools["media_audio_cut"])
        assertNull("media_video_audio should no longer be a top-level card", allTools["media_video_audio"])
        assertNull("media_audio_convert should no longer be a top-level card", allTools["media_audio_convert"])
        assertNull("media_audio_boost should no longer be a top-level card", allTools["media_audio_boost"])
        assertNull("media_audio_merger should no longer be a top-level card", allTools["media_audio_merger"])

        // 3. Catalog count assertion
        assertEquals(ToolCatalog.getAllTools().size, allTools.size)
    }

    @Test
    fun `AUDIO_STUDIO_VIEWMODEL_FACTORY_CONTRACT_TEST - audio studio viewmodels expose exact JVM constructor and construct via factory`() {
        val application = org.robolectric.RuntimeEnvironment.getApplication()
        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)

        val androidVms = listOf(
            AudioCutterViewModel::class.java,
            VideoToAudioViewModel::class.java,
            AudioConverterViewModel::class.java,
            AudioVolumeViewModel::class.java,
            AudioMergerViewModel::class.java
        )

        for (clazz in androidVms) {
            val constructor = clazz.getConstructor(android.app.Application::class.java)
            assertNotNull("Constructor for ${clazz.simpleName} must exist", constructor)
            val instance = factory.create(clazz)
            assertNotNull("Factory must instantiate ${clazz.simpleName}", instance)
        }
    }

    @Test
    fun `audio studio capability enum has all 5 required capabilities`() {
        val caps = AudioStudioCapability.entries
        assertEquals(5, caps.size)

        val expected = setOf(
            AudioStudioCapability.CUT,
            AudioStudioCapability.VIDEO_TO_AUDIO,
            AudioStudioCapability.CONVERT,
            AudioStudioCapability.VOLUME,
            AudioStudioCapability.MERGE
        )
        assertEquals(expected, caps.toSet())
    }

    @Test
    fun `audio output formats support MP3, M4A, and WAV specifications`() {
        val formats = AudioOutputFormat.entries
        assertTrue(formats.contains(AudioOutputFormat.MP3))
        assertTrue(formats.contains(AudioOutputFormat.M4A))
        assertTrue(formats.contains(AudioOutputFormat.WAV))

        assertEquals(".mp3", AudioOutputFormat.MP3.extension)
        assertEquals("audio/mpeg", AudioOutputFormat.MP3.mimeType)

        assertEquals(".m4a", AudioOutputFormat.M4A.extension)
        assertEquals("audio/mp4", AudioOutputFormat.M4A.mimeType)

        assertEquals(".wav", AudioOutputFormat.WAV.extension)
        assertEquals("audio/wav", AudioOutputFormat.WAV.mimeType)
    }

    @Test
    fun `volume presets define valid gain factors and descriptions`() {
        val factors = VolumePreset.entries.map { it.gainFactor }
        assertTrue(factors.contains(0.5f))
        assertTrue(factors.contains(1.0f))
        assertTrue(factors.contains(1.25f))
        assertTrue(factors.contains(1.5f))
        assertTrue(factors.contains(2.0f))

        VolumePreset.entries.forEach {
            assertTrue(it.title.isNotEmpty())
            assertTrue(it.description.isNotEmpty())
        }
    }

    @Test
    fun `audio volume engine applies accurate gain to PCM byte samples with soft-limiting`() {
        // Create 4 16-bit PCM samples: [1000, -1000, 20000, -20000]
        val byteBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.putShort(1000.toShort())
        byteBuffer.putShort((-1000).toShort())
        byteBuffer.putShort(20000.toShort())
        byteBuffer.putShort((-20000).toShort())
        val inBytes = byteBuffer.array()

        // Apply 2.0x gain
        val outBytes = AudioVolumeEngine.applyGainToPcm(inBytes, inBytes.size, 2.0f)
        val outBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(2000.toShort(), outBuffer.getShort())
        assertEquals((-2000).toShort(), outBuffer.getShort())
        // 20000 * 2.0 = 40000 -> clamped to 32767
        assertEquals(32767.toShort(), outBuffer.getShort())
        // -20000 * 2.0 = -40000 -> clamped to -32768
        assertEquals((-32768).toShort(), outBuffer.getShort())
    }
}
