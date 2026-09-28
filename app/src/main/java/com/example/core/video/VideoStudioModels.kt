package com.example.core.video

import android.net.Uri

enum class VideoCropPreset(
    val title: String,
    val aspectWidth: Float,
    val aspectHeight: Float
) {
    ORIGINAL("Original", 0f, 0f),
    SQUARE_1_1("1:1 Square", 1f, 1f),
    PORTRAIT_4_5("4:5 Portrait", 4f, 5f),
    STORY_9_16("9:16 Story/Reel", 9f, 16f),
    LANDSCAPE_16_9("16:9 Landscape", 16f, 9f),
    CUSTOM("Custom", 0f, 0f);

    val aspectRatio: Float
        get() = if (aspectHeight > 0f) aspectWidth / aspectHeight else 0f
}

enum class VideoSpeedPreset(val multiplier: Float, val label: String) {
    SPEED_0_5X(0.5f, "0.5x Slow"),
    SPEED_0_75X(0.75f, "0.75x"),
    SPEED_1_0X(1.0f, "1.0x Normal"),
    SPEED_1_25X(1.25f, "1.25x"),
    SPEED_1_5X(1.5f, "1.5x"),
    SPEED_2_0X(2.0f, "2.0x Fast")
}

enum class VideoRotationAngle(val degrees: Int, val label: String) {
    ROTATION_90(90, "90° Clockwise"),
    ROTATION_180(180, "180° Half Turn"),
    ROTATION_270(270, "270° (90° CCW)")
}

enum class FrameImageFormat(val extension: String, val mimeType: String) {
    JPEG(".jpg", "image/jpeg"),
    PNG(".png", "image/png")
}

sealed interface VideoStudioResult {
    data class Success(
        val outputUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val durationMs: Long
    ) : VideoStudioResult

    data class Failure(val message: String, val cause: Throwable? = null) : VideoStudioResult
    data object Cancelled : VideoStudioResult
}

sealed interface FrameExtractResult {
    data class Success(
        val outputUri: Uri,
        val displayName: String,
        val width: Int,
        val height: Int,
        val timestampMs: Long
    ) : FrameExtractResult

    data class Failure(val message: String, val cause: Throwable? = null) : FrameExtractResult
}
