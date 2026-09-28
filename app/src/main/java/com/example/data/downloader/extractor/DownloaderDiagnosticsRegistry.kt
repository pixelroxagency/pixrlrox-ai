package com.example.data.downloader.extractor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloaderDiagnostics(
    val sourcePlatform: String = "N/A",
    val extractorSelected: String = "N/A",
    val extractorAttemptOrder: String = "N/A",
    val extractorReached: String = "N/A",
    val httpStatus: String = "N/A",
    val redirectCount: String = "0",
    val responseContentType: String = "N/A",
    val failureCategory: String = "N/A",
    val underlyingExceptionClass: String = "N/A",
    val underlyingExceptionMessage: String = "N/A",
    val rateLimitDetected: String = "NO",
    val botChallengeDetected: String = "NO",
    val verificationChallengeDetected: String = "NO",
    val noMediaFound: String = "NO",
    val unsupportedSource: String = "NO",
    val temporaryExtractionFailure: String = "NO",
    val ytDlpFallbackInvoked: String = "NO",
    val ytDlpInitialized: String = "NO",
    val ytDlpExecutionReached: String = "NO",
    val finalMappedUserMessage: String = "N/A",
    val ytDlpExceptionClass: String = "N/A",
    val ytDlpExceptionMessage: String = "N/A",
    val ytDlpCauseClass: String = "N/A",
    val ytDlpCauseMessage: String = "N/A",
    val ytDlpExitCode: String = "N/A",
    val ytDlpStdoutSummary: String = "N/A",
    val ytDlpStderrSummary: String = "N/A",
    val ytDlpResultSuccess: String = "N/A",
    val ytDlpInfoExtractionSucceeded: String = "N/A",
    val ytDlpFormatsCount: String = "N/A",
    val networkTimeoutDetected: String = "NO",
    val ytDlpAttemptCount: String = "0",
    val ytDlpRetryPerformed: String = "NO",
    val ytDlpSocketTimeoutSeconds: String = "0",
    val ytDlpBundledVersion: String = "N/A",
    val ytDlpRuntimeVersionBeforeUpdate: String = "N/A",
    val ytDlpUpdateChannel: String = "N/A",
    val ytDlpUpdateCheckPerformed: String = "NO",
    val ytDlpUpdateAvailable: String = "NO",
    val ytDlpUpdateSucceeded: String = "NO",
    val ytDlpRuntimeVersionAfterUpdate: String = "N/A",
    val ytDlpUsingBundledFallback: String = "YES",
    val selectedFormatId: String = "N/A",
    val selectedVideoCodec: String = "N/A",
    val selectedAudioCodec: String = "N/A",
    val selectedFormatHasVideo: String = "NO",
    val selectedFormatHasAudio: String = "NO",
    val separateAudioAvailable: String = "NO",
    val transferEngine: String = "N/A",
    val actualYtDlpFormatSelector: String = "N/A",
    val mergeRequired: String = "NO",
    val mergeEngine: String = "N/A",
    val ffmpegInitialized: String = "NO",
    val ffmpegInitializationError: String = "N/A",
    val ytDlpDownloadInvoked: String = "NO",
    val ytDlpDownloadCompleted: String = "NO",
    val finalFileExists: String = "NO",
    val finalFileSize: String = "0",
    val finalFileVideoStreamDetected: String = "N/A",
    val finalFileAudioStreamDetected: String = "N/A",
    val finalFileValidationPassed: String = "NO"
)

object DownloaderDiagnosticsRegistry {
    private val _diagnostics = MutableStateFlow<DownloaderDiagnostics?>(null)
    val diagnostics: StateFlow<DownloaderDiagnostics?> = _diagnostics.asStateFlow()

    private val attempts = mutableListOf<String>()

    fun reset() {
        synchronized(attempts) {
            attempts.clear()
        }
        _diagnostics.value = null
    }

    fun recordAttempt(extractorName: String, outcome: String) {
        synchronized(attempts) {
            attempts.add("${attempts.size + 1}. $extractorName → $outcome")
        }
        updateDiagnostics { it } // trigger update to include the attempts
    }

    fun updateDiagnostics(updater: (DownloaderDiagnostics) -> DownloaderDiagnostics) {
        val current = _diagnostics.value ?: DownloaderDiagnostics()
        val next = updater(current)
        _diagnostics.value = next.copy(
            extractorAttemptOrder = synchronized(attempts) {
                if (attempts.isEmpty()) "N/A" else attempts.joinToString("\n")
            }
        )
    }
}
