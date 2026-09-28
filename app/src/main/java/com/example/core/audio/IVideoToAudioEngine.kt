package com.example.core.audio

import android.net.Uri

interface IVideoToAudioEngine {
    suspend fun extractAudio(
        sourceUri: Uri,
        format: AudioOutputFormat,
        mp3Bitrate: Mp3Bitrate = Mp3Bitrate.HIGH_192,
        m4aPreset: AudioQualityPreset = AudioQualityPreset.DIRECT_REMUX,
        onProgress: (Int) -> Unit
    ): VideoToAudioResult

    fun cancel()
}
