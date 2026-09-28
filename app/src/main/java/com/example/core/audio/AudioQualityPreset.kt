package com.example.core.audio

enum class AudioQualityPreset(
    val title: String,
    val description: String,
    val targetBitrate: Int? // in bps, null means direct lossless copy/remux
) {
    DIRECT_REMUX(
        title = "Original Audio Quality",
        description = "Lossless direct remux (fastest, original bitrate)",
        targetBitrate = null
    ),
    HIGH_192(
        title = "High Quality (192 kbps)",
        description = "AAC encoding at 192 kbps for clear high fidelity",
        targetBitrate = 192000
    ),
    STANDARD_128(
        title = "Standard Quality (128 kbps)",
        description = "Balanced size and quality (recommended)",
        targetBitrate = 128000
    ),
    SMALL_96(
        title = "Small File (96 kbps)",
        description = "Compact AAC encoding for smaller file size",
        targetBitrate = 96000
    )
}
