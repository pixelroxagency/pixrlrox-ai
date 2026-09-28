package com.example.core.video

import androidx.media3.common.MimeTypes

enum class VideoCompressionPreset(
    val title: String,
    val description: String,
    val maxDimension: Int,
    val targetVideoBitrate: Long, // in bps
    val targetAudioBitrate: Long = 128_000L, // 128 kbps AAC
    val videoMimeType: String = MimeTypes.VIDEO_H264,
    val audioMimeType: String = MimeTypes.AUDIO_AAC
) {
    HIGH_QUALITY(
        title = "High Quality",
        description = "Preserves near-original quality with moderate compression",
        maxDimension = 1920,
        targetVideoBitrate = 4_000_000L, // 4 Mbps
        targetAudioBitrate = 192_000L // 192 kbps AAC
    ),
    MEDIUM(
        title = "Medium",
        description = "Balanced size and quality, great for sharing",
        maxDimension = 1280,
        targetVideoBitrate = 2_000_000L, // 2 Mbps
        targetAudioBitrate = 128_000L // 128 kbps AAC
    ),
    SMALL(
        title = "Small File",
        description = "Maximum compression for smallest file size",
        maxDimension = 854,
        targetVideoBitrate = 1_000_000L, // 1 Mbps
        targetAudioBitrate = 96_000L // 96 kbps AAC
    );

    /**
     * Calculates target dimensions preserving aspect ratio and strictly enforcing:
     * 1. Never upscale source video (if source fits within maxDimension, retain source dimensions).
     * 2. Ensure dimensions are even numbers (required by hardware H.264/AVC encoders).
     */
    fun calculateTargetDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return Pair(1280, 720)

        val sourceMaxDim = maxOf(sourceWidth, sourceHeight)
        if (sourceMaxDim <= maxDimension) {
            // Source is already smaller than or equal to preset limit; do not upscale!
            val evenW = if (sourceWidth % 2 != 0) sourceWidth - 1 else sourceWidth
            val evenH = if (sourceHeight % 2 != 0) sourceHeight - 1 else sourceHeight
            return Pair(maxOf(evenW, 2), maxOf(evenH, 2))
        }

        val isPortrait = sourceHeight > sourceWidth
        val targetWidth: Int
        val targetHeight: Int

        if (isPortrait) {
            targetHeight = maxDimension
            targetWidth = ((sourceWidth.toFloat() / sourceHeight.toFloat()) * targetHeight).toInt()
        } else {
            targetWidth = maxDimension
            targetHeight = ((sourceHeight.toFloat() / sourceWidth.toFloat()) * targetWidth).toInt()
        }

        val evenWidth = if (targetWidth % 2 != 0) targetWidth - 1 else targetWidth
        val evenHeight = if (targetHeight % 2 != 0) targetHeight - 1 else targetHeight

        return Pair(maxOf(evenWidth, 2), maxOf(evenHeight, 2))
    }

    /**
     * Estimates output file size in bytes based on duration and target bitrates.
     * Uses the exact shared encoder bitrate targets plus a ~5% MP4 container/muxer overhead.
     */
    fun estimateOutputSize(durationMs: Long, sourceBitrate: Long = 0L): Long {
        if (durationMs <= 0) return 0L
        val durationSeconds = durationMs / 1000.0

        val effectiveVideoBitrate = if (sourceBitrate > 0) {
            minOf(targetVideoBitrate, sourceBitrate)
        } else {
            targetVideoBitrate
        }

        val totalBitrate = effectiveVideoBitrate + targetAudioBitrate
        val estimatedBytes = (totalBitrate * durationSeconds / 8.0).toLong()
        return (estimatedBytes * 1.05).toLong()
    }
}

