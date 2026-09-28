package com.example.data.downloader.extractor

import com.example.data.downloader.model.MediaAnalysisResult
import okhttp3.OkHttpClient

interface PlatformExtractor {
    val platformName: String
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String, httpClient: OkHttpClient): MediaAnalysisResult
}
