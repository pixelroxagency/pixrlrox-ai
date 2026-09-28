package com.example.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoToAudioLogicTest {

    @Test
    fun testMp3AudioFormatMapping() {
        val format = AudioOutputFormat.MP3
        assertEquals(".mp3", format.extension)
        assertEquals("audio/mpeg", format.mimeType)
        assertEquals("MP3", format.title)
    }

    @Test
    fun testM4aAudioFormatMapping() {
        val format = AudioOutputFormat.M4A
        assertEquals(".m4a", format.extension)
        assertEquals("audio/mp4", format.mimeType)
        assertEquals("M4A (AAC)", format.title)
    }

    @Test
    fun testMp3Bitrates() {
        assertEquals(192, Mp3Bitrate.HIGH_192.bitrateKbps)
        assertEquals(128, Mp3Bitrate.STANDARD_128.bitrateKbps)
        assertEquals(96, Mp3Bitrate.SMALL_96.bitrateKbps)
    }

    @Test
    fun testGenerateSafeAudioDisplayName_mp3() {
        val name = AudioOutputPublisher.generateSafeAudioDisplayName(
            rawName = "my_clip.mp4",
            format = AudioOutputFormat.MP3,
            timestamp = 1700000000000L
        )
        assertTrue(name.startsWith("my_clip_audio_"))
        assertTrue(name.endsWith(".mp3"))
    }

    @Test
    fun testGenerateSafeAudioDisplayName_m4a() {
        val name = AudioOutputPublisher.generateSafeAudioDisplayName(
            rawName = "my_clip.mp4",
            format = AudioOutputFormat.M4A,
            timestamp = 1700000000000L
        )
        assertTrue(name.startsWith("my_clip_audio_"))
        assertTrue(name.endsWith(".m4a"))
    }

    @Test
    fun testGenerateSafeAudioDisplayName_specialCharacters() {
        val name = AudioOutputPublisher.generateSafeAudioDisplayName(
            rawName = "video file with spaces & symbols: #1!.mov",
            format = AudioOutputFormat.MP3,
            timestamp = 1700000000000L
        )
        assertTrue(name.startsWith("video_file_with_spaces____audio_"))
        assertTrue(name.endsWith(".mp3"))
    }

    @Test
    fun testAudioQualityPresets() {
        val presets = AudioQualityPreset.entries
        assertEquals(4, presets.size)

        val direct = AudioQualityPreset.DIRECT_REMUX
        assertNull(direct.targetBitrate)
        assertTrue(direct.title.contains("Original"))

        val high = AudioQualityPreset.HIGH_192
        assertEquals(192000, high.targetBitrate)

        val standard = AudioQualityPreset.STANDARD_128
        assertEquals(128000, standard.targetBitrate)

        val small = AudioQualityPreset.SMALL_96
        assertEquals(96000, small.targetBitrate)
    }
}
