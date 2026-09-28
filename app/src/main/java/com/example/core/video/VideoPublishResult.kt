package com.example.core.video

import android.net.Uri

sealed interface VideoPublishResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String
    ) : VideoPublishResult

    data class Failure(
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : VideoPublishResult
}
