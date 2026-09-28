package com.example.data.downloader.analyzer

import com.example.data.downloader.model.MediaAnalysisResult

interface MediaSourceAnalyzer {
    val priority: Int get() = 0
    suspend fun canHandle(url: String): Boolean
    suspend fun analyze(url: String): MediaAnalysisResult
}
