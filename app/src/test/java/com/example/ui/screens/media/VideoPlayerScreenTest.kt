package com.example.ui.screens.media

import org.junit.Assert.*
import org.junit.Test

class VideoPlayerScreenTest {

    @Test
    fun `formatTime handles zero milliseconds`() {
        val result = formatTime(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatTime formats standard minutes and seconds`() {
        // 2 minutes, 35 seconds = 155,000 milliseconds
        val result = formatTime(155000L)
        assertEquals("02:35", result)
    }

    @Test
    fun `formatTime formats large durations with hours`() {
        // 1 hour, 5 minutes, 20 seconds = 3,920,000 milliseconds
        val result = formatTime(3920000L)
        assertEquals("01:05:20", result)
    }

    @Test
    fun `VideoResizeMode enum maps to correct display names and values`() {
        assertEquals("Fit", VideoResizeMode.FIT.displayName)
        assertEquals("Fill / Zoom", VideoResizeMode.FILL.displayName)
        assertEquals("Stretch", VideoResizeMode.STRETCH.displayName)
    }
}
