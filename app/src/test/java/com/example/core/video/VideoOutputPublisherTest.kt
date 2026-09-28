package com.example.core.video

import org.junit.Assert.*
import org.junit.Test

class VideoOutputPublisherTest {

    @Test
    fun testGenerateSafeDisplayNameNormal() {
        val name = VideoOutputPublisher.generateSafeDisplayName("holiday.mp4")
        assertTrue("Should contain holiday_compressed", name.contains("holiday_compressed"))
        assertTrue("Should end with .mp4", name.endsWith(".mp4"))
    }

    @Test
    fun testGenerateSafeDisplayNameBlank() {
        val name = VideoOutputPublisher.generateSafeDisplayName("   ")
        assertTrue("Should default to video_compressed", name.contains("video_compressed"))
        assertTrue("Should end with .mp4", name.endsWith(".mp4"))
    }

    @Test
    fun testGenerateSafeDisplayNameInvalidChars() {
        val name = VideoOutputPublisher.generateSafeDisplayName("my:video*file?.mp4")
        assertFalse("Should not contain colon", name.contains(":"))
        assertFalse("Should not contain asterisk", name.contains("*"))
        assertFalse("Should not contain question mark", name.contains("?"))
        assertTrue("Should end with .mp4", name.endsWith(".mp4"))
    }

    @Test
    fun testGenerateSafeDisplayNameMissingExtension() {
        val name = VideoOutputPublisher.generateSafeDisplayName("my_clip")
        assertTrue("Should contain my_clip_compressed", name.contains("my_clip_compressed"))
        assertTrue("Should end with .mp4", name.endsWith(".mp4"))
    }

    @Test
    fun testAlreadyCompressedSuffixHandling() {
        val name = VideoOutputPublisher.generateSafeDisplayName("clip_compressed.mp4")
        // Should not duplicate _compressed multiple times unnecessarily or should handle gracefully
        assertTrue("Should contain clip_compressed", name.contains("clip_compressed"))
        assertTrue("Should end with .mp4", name.endsWith(".mp4"))
    }
}
