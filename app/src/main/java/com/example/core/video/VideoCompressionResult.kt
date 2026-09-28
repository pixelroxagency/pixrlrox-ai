package com.example.core.video

import java.io.File

sealed interface VideoCompressionResult {
    data class Success(
        val outputFile: File,
        val outputSizeBytes: Long,
        val outputMetadata: VideoMetadata
    ) : VideoCompressionResult

    data class Failure(
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : VideoCompressionResult

    data object Cancelled : VideoCompressionResult
}
