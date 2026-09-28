package com.example.data.downloader.extractor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadDiagnostics(
    val downloadId: String = "N/A",
    val sourcePlatform: String = "N/A",
    val selectedResolution: String = "N/A",
    val selectedFormatId: String = "N/A",
    val expectedMimeType: String = "N/A",
    val expectedFileSize: String = "0",
    val resolvedUrlPresent: String = "NO",
    val resolvedUrlScheme: String = "N/A",
    val resolvedUrlHost: String = "N/A",
    val resolvedUrlExpiredSuspected: String = "NO",

    val requestUserAgentPresent: String = "NO",
    val requestRefererPresent: String = "NO",
    val requestCookieRequired: String = "NO",
    val requestCookiePresent: String = "NO",
    val extractorHeadersCount: String = "0",
    val headersPassedToDownloadManager: String = "N/A",
    val missingRequiredHeaders: String = "N/A",

    val downloadManagerStatus: String = "N/A",
    val downloadManagerReasonCode: String = "N/A",
    val downloadManagerReasonName: String = "N/A",

    val localUriPresent: String = "NO",
    val downloadedBytes: String = "0",
    val totalExpectedBytes: String = "0",
    val transferEngine: String = "DOWNLOAD_MANAGER",

    val failureStage: String = "N/A",
    val finalDownloadError: String = "N/A",

    // URL Lifetime parameters
    val resolvedUrlHasExpiryParameter: String = "NO",
    val resolvedUrlHasSignatureParameters: String = "NO",
    val resolvedUrlAgeAtEnqueueMs: String = "0",
    val analyzeToDownloadDelayMs: String = "0",

    // V11 Additions
    val appDownloadId: String = "N/A",
    val downloadManagerId: String = "N/A",
    val downloadManagerIdPresent: String = "NO",
    val downloadManagerIdPersisted: String = "NO",
    val downloadManagerIdAssociatedWithCorrectAppItem: String = "NO",
    val downloadManagerQueriedId: String = "N/A",
    val downloadManagerQueryMatchedRow: String = "NO",
    val downloadManagerQueryRowCount: String = "0",
    val downloadManagerRowMissing: String = "NO",
    val downloadManagerFreshQueryPerformed: String = "NO",
    val downloadManagerCurrentStatus: String = "N/A",
    val diagnosticStatusTimestamp: String = "N/A",
    val applicationStatus: String = "N/A",
    val applicationStatusSetBy: String = "N/A",
    val applicationStatusSetAt: String = "N/A",
    val applicationFailureTrigger: String = "N/A",
    val applicationFailureReason: String = "N/A",
    val elapsedSinceEnqueueSeconds: String = "0",
    val progressEverObserved: String = "NO",
    val lastProgressBytes: String = "0",
    val lastProgressTimestamp: String = "N/A",
    val stuckAtZeroBytes: String = "NO",

    // Extended Merge Diagnostics
    val selectedVideoCodec: String = "N/A",
    val selectedAudioCodec: String = "N/A",
    val selectedFormatHasVideo: String = "NO",
    val selectedFormatHasAudio: String = "NO",
    val separateAudioAvailable: String = "NO",
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
    val finalFileValidationPassed: String = "NO",
    
    val downloadIntent: String = "VIDEO",
    val sourceAudioAvailable: String = "NO",
    val finalSelector: String = "N/A",
    val videoComponentDownloaded: String = "NO",
    val audioComponentDownloaded: String = "NO",
    val mergeAttempted: String = "NO",
    val mergeSucceeded: String = "NO",
    val temporaryVideoPublishedToDownloads: String = "NO",
    val temporaryAudioPublishedToDownloads: String = "NO",
    val finalAudioExpected: String = "YES",

    // Stale Format Recovery Diagnostics
    val originalSelectedFormatId: String = "N/A",
    val originalSelectedResolution: String = "N/A",
    val formatUnavailableDetected: String = "NO",
    val freshMetadataExtractionPerformed: String = "NO",
    val freshFormatsCount: String = "0",
    val equivalentFormatFound: String = "NO",
    val recoveredFormatId: String = "N/A",
    val recoveredResolution: String = "N/A",
    val recoveredHasVideo: String = "NO",
    val recoveredHasAudio: String = "NO",
    val formatRecoverySelector: String = "N/A",
    val formatRecoveryRetryPerformed: String = "NO",
    val formatRecoveryRetrySucceeded: String = "NO"
)

object DownloadDiagnosticsRegistry {
    private val _diagnosticsMap = MutableStateFlow<Map<String, DownloadDiagnostics>>(emptyMap())
    val diagnosticsMap: StateFlow<Map<String, DownloadDiagnostics>> = _diagnosticsMap.asStateFlow()

    fun reset() {
        _diagnosticsMap.value = emptyMap()
    }

    fun getDiagnostics(downloadId: String): DownloadDiagnostics? {
        return _diagnosticsMap.value[downloadId]
    }

    fun updateDiagnostics(downloadId: String, updater: (DownloadDiagnostics) -> DownloadDiagnostics) {
        val currentMap = _diagnosticsMap.value
        val existing = currentMap[downloadId] ?: DownloadDiagnostics(downloadId = downloadId)
        val updated = updater(existing)
        _diagnosticsMap.value = currentMap + (downloadId to updated)
    }
}

object AnalysisTimestampTracker {
    @Volatile var lastAnalysisUrl: String = ""
    @Volatile var lastAnalysisTimeMs: Long = 0L
}
