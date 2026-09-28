package com.example.core.image

import java.io.File

sealed interface ImageCompressionResult {
    data class Success(
        val tempFile: File,
        val outputWidth: Int,
        val outputHeight: Int,
        val outputSizeBytes: Long,
        val format: OutputFormat,
        val qualityUsed: Int
    ) : ImageCompressionResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ImageCompressionResult

    data object Cancelled : ImageCompressionResult
}
