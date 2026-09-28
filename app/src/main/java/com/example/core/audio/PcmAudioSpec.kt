package com.example.core.audio

data class PcmAudioSpec(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitsPerSample: Int = 16
) {
    val bytesPerFrame: Int
        get() = channelCount * (bitsPerSample / 8)

    val byteRate: Int
        get() = sampleRate * bytesPerFrame
}
