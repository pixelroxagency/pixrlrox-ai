package com.example.core.trimmer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object VideoTrimmerUtils {

    /**
     * Formats milliseconds into human-readable duration strings.
     * Output examples: "00:05", "01:23", "01:05:30".
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats bytes into a human readable file size string.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Generates a safe, collision-resistant display name for the trimmed video output.
     * Example: "sample_trimmed_20260920_231545.mp4"
     */
    fun generateTrimmedDisplayName(originalName: String, timestamp: Long = System.currentTimeMillis()): String {
        val nameWithoutExt = if (originalName.contains(".")) {
            originalName.substringBeforeLast(".")
        } else {
            originalName.ifBlank { "video" }
        }
        val safeBaseName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val dateStr = timeFormat.format(Date(timestamp))
        return "${safeBaseName}_trimmed_$dateStr.mp4"
    }

    /**
     * Validates that the selected start and end timestamps constitute a valid trim range.
     * Ensures: 0 <= start < end <= totalDuration and (end - start) >= minClipDurationMs.
     */
    fun isValidTrimRange(
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        minClipDurationMs: Long = 500L
    ): Boolean {
        if (totalDurationMs <= 0) return false
        if (startMs < 0) return false
        if (endMs > totalDurationMs) return false
        if (endMs <= startMs) return false
        if ((endMs - startMs) < minClipDurationMs) return false
        return true
    }

    /**
     * Adjusts a position by a delta while clamping strictly to [minBound, maxBound].
     */
    fun adjustPosition(currentMs: Long, deltaMs: Long, minBoundMs: Long, maxBoundMs: Long): Long {
        val newPos = currentMs + deltaMs
        return max(minBoundMs, min(newPos, maxBoundMs))
    }
}
