package com.example.core.trimmer

import android.net.Uri
import java.io.File

sealed interface VideoTrimmerResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val wasTransmuxed: Boolean
    ) : VideoTrimmerResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : VideoTrimmerResult

    data object Cancelled : VideoTrimmerResult
}

interface IVideoTrimmerEngine {
    suspend fun trimVideo(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (Int) -> Unit
    ): VideoTrimmerResult

    fun cancel()
}
