package com.example.core.audio

enum class AudioOutputFormat(
    val title: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    MP3(
        title = "MP3",
        extension = ".mp3",
        mimeType = "audio/mpeg",
        description = "Universal compatibility (MPEG Layer III)"
    ),
    M4A(
        title = "M4A (AAC)",
        extension = ".m4a",
        mimeType = "audio/mp4",
        description = "Fast direct remux / original audio quality"
    ),
    WAV(
        title = "WAV",
        extension = ".wav",
        mimeType = "audio/wav",
        description = "Uncompressed lossless PCM audio"
    )
}

enum class Mp3Bitrate(
    val title: String,
    val bitrateKbps: Int,
    val description: String
) {
    HIGH_192("High (192 kbps)", 192, "Clear high fidelity audio"),
    STANDARD_128("Standard (128 kbps)", 128, "Balanced quality and size"),
    SMALL_96("Small (96 kbps)", 96, "Compact file size")
}
