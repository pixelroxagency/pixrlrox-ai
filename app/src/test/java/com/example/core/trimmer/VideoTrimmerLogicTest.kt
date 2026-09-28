package com.example.core.trimmer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTrimmerLogicTest {

    @Test
    fun testFormatDuration_secondsOnly() {
        val formatted = VideoTrimmerUtils.formatDuration(5000L)
        assertEquals("00:05", formatted)
    }

    @Test
    fun testFormatDuration_minutesAndSeconds() {
        val formatted = VideoTrimmerUtils.formatDuration(83000L) // 1 min 23 sec
        assertEquals("01:23", formatted)
    }

    @Test
    fun testFormatDuration_hoursMinutesAndSeconds() {
        val formatted = VideoTrimmerUtils.formatDuration(3930000L) // 1 hr 05 min 30 sec
        assertEquals("01:05:30", formatted)
    }

    @Test
    fun testFormatFileSize_bytesAndMb() {
        assertEquals("0 B", VideoTrimmerUtils.formatFileSize(0L))
        assertEquals("500 B", VideoTrimmerUtils.formatFileSize(500L))
        assertEquals("1.5 KB", VideoTrimmerUtils.formatFileSize(1536L))
        assertEquals("10.0 MB", VideoTrimmerUtils.formatFileSize(10485760L))
    }

    @Test
    fun testGenerateTrimmedDisplayName() {
        val name = VideoTrimmerUtils.generateTrimmedDisplayName("vacation_clip.mp4", 1700000000000L)
        assertTrue(name.startsWith("vacation_clip_trimmed_"))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun testIsValidTrimRange_validRange() {
        val isValid = VideoTrimmerUtils.isValidTrimRange(
            startMs = 5000L,
            endMs = 15000L,
            totalDurationMs = 30000L
        )
        assertTrue(isValid)
    }

    @Test
    fun testIsValidTrimRange_invalidRange_startAfterEnd() {
        val isValid = VideoTrimmerUtils.isValidTrimRange(
            startMs = 15000L,
            endMs = 5000L,
            totalDurationMs = 30000L
        )
        assertFalse(isValid)
    }

    @Test
    fun testIsValidTrimRange_invalidRange_tooShort() {
        val isValid = VideoTrimmerUtils.isValidTrimRange(
            startMs = 5000L,
            endMs = 5200L, // 200ms < 500ms min clip duration
            totalDurationMs = 30000L,
            minClipDurationMs = 500L
        )
        assertFalse(isValid)
    }

    @Test
    fun testAdjustPosition_clamping() {
        val adjusted = VideoTrimmerUtils.adjustPosition(
            currentMs = 2000L,
            deltaMs = -5000L,
            minBoundMs = 0L,
            maxBoundMs = 10000L
        )
        assertEquals(0L, adjusted)

        val adjustedMax = VideoTrimmerUtils.adjustPosition(
            currentMs = 8000L,
            deltaMs = 5000L,
            minBoundMs = 0L,
            maxBoundMs = 10000L
        )
        assertEquals(10000L, adjustedMax)
    }
}
