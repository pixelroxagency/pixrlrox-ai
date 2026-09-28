package com.example.data.downloader.extractor

import com.example.data.downloader.extractor.DownloaderDiagnostics
import com.example.data.downloader.extractor.DownloadDiagnostics

object DiagnosticReportFormatter {

    fun format(
        extractionDiagnostics: DownloaderDiagnostics? = null,
        downloadDiagnostics: DownloadDiagnostics? = null
    ): String {
        val sb = StringBuilder()
        sb.append("PixelRox Downloader Debug Report\n")
        sb.append("================================\n\n")

        if (extractionDiagnostics != null) {
            sb.append("SourcePlatform=${extractionDiagnostics.sourcePlatform}\n")
            sb.append("ExtractorSelected=${extractionDiagnostics.extractorSelected}\n")
            sb.append("ExtractorAttemptOrder=${extractionDiagnostics.extractorAttemptOrder}\n")
            sb.append("ExtractorReached=${extractionDiagnostics.extractorReached}\n\n")
            sb.append("HttpStatus=${extractionDiagnostics.httpStatus}\n")
            sb.append("RedirectCount=${extractionDiagnostics.redirectCount}\n")
            sb.append("ResponseContentType=${extractionDiagnostics.responseContentType}\n\n")
            sb.append("FailureCategory=${extractionDiagnostics.failureCategory}\n")
            sb.append("UnderlyingExceptionClass=${extractionDiagnostics.underlyingExceptionClass}\n")
            sb.append("UnderlyingExceptionMessage=${extractionDiagnostics.underlyingExceptionMessage}\n\n")
            sb.append("RateLimitDetected=${extractionDiagnostics.rateLimitDetected}\n")
            sb.append("BotChallengeDetected=${extractionDiagnostics.botChallengeDetected}\n")
            sb.append("VerificationChallengeDetected=${extractionDiagnostics.verificationChallengeDetected}\n")
            sb.append("NoMediaFound=${extractionDiagnostics.noMediaFound}\n")
            sb.append("UnsupportedSource=${extractionDiagnostics.unsupportedSource}\n")
            sb.append("TemporaryExtractionFailure=${extractionDiagnostics.temporaryExtractionFailure}\n\n")
            sb.append("YtDlpFallbackInvoked=${extractionDiagnostics.ytDlpFallbackInvoked}\n")
            sb.append("YtDlpInitialized=${extractionDiagnostics.ytDlpInitialized}\n")
            sb.append("YtDlpExecutionReached=${extractionDiagnostics.ytDlpExecutionReached}\n\n")
            sb.append("YtDlpExceptionClass=${extractionDiagnostics.ytDlpExceptionClass}\n")
            sb.append("YtDlpExceptionMessage=${extractionDiagnostics.ytDlpExceptionMessage}\n")
            sb.append("YtDlpCauseClass=${extractionDiagnostics.ytDlpCauseClass}\n")
            sb.append("YtDlpCauseMessage=${extractionDiagnostics.ytDlpCauseMessage}\n")
            sb.append("YtDlpExitCode=${extractionDiagnostics.ytDlpExitCode}\n")
            sb.append("YtDlpStdoutSummary=${extractionDiagnostics.ytDlpStdoutSummary}\n")
            sb.append("YtDlpStderrSummary=${extractionDiagnostics.ytDlpStderrSummary}\n\n")
            sb.append("YtDlpResultSuccess=${extractionDiagnostics.ytDlpResultSuccess}\n")
            sb.append("YtDlpInfoExtractionSucceeded=${extractionDiagnostics.ytDlpInfoExtractionSucceeded}\n")
            sb.append("YtDlpFormatsCount=${extractionDiagnostics.ytDlpFormatsCount}\n\n")
            sb.append("NetworkTimeoutDetected=${extractionDiagnostics.networkTimeoutDetected}\n")
            sb.append("YtDlpAttemptCount=${extractionDiagnostics.ytDlpAttemptCount}\n")
            sb.append("YtDlpRetryPerformed=${extractionDiagnostics.ytDlpRetryPerformed}\n")
            sb.append("YtDlpSocketTimeoutSeconds=${extractionDiagnostics.ytDlpSocketTimeoutSeconds}\n\n")
            sb.append("YtDlpBundledVersion=${extractionDiagnostics.ytDlpBundledVersion}\n")
            sb.append("YtDlpRuntimeVersionBeforeUpdate=${extractionDiagnostics.ytDlpRuntimeVersionBeforeUpdate}\n")
            sb.append("YtDlpUpdateChannel=${extractionDiagnostics.ytDlpUpdateChannel}\n")
            sb.append("YtDlpUpdateCheckPerformed=${extractionDiagnostics.ytDlpUpdateCheckPerformed}\n")
            sb.append("YtDlpUpdateAvailable=${extractionDiagnostics.ytDlpUpdateAvailable}\n")
            sb.append("YtDlpUpdateSucceeded=${extractionDiagnostics.ytDlpUpdateSucceeded}\n")
            sb.append("YtDlpRuntimeVersionAfterUpdate=${extractionDiagnostics.ytDlpRuntimeVersionAfterUpdate}\n")
            sb.append("YtDlpUsingBundledFallback=${extractionDiagnostics.ytDlpUsingBundledFallback}\n\n")
            sb.append("FinalMappedUserMessage=${extractionDiagnostics.finalMappedUserMessage}\n\n")
        }

        if (downloadDiagnostics != null) {
            sb.append("DownloadId=${downloadDiagnostics.downloadId}\n")
            sb.append("SelectedResolution=${downloadDiagnostics.selectedResolution}\n")
            sb.append("SelectedFormatId=${downloadDiagnostics.selectedFormatId}\n")
            sb.append("ExpectedMimeType=${downloadDiagnostics.expectedMimeType}\n")
            sb.append("ExpectedFileSize=${downloadDiagnostics.expectedFileSize}\n\n")
            sb.append("ResolvedUrlPresent=${downloadDiagnostics.resolvedUrlPresent}\n")
            sb.append("ResolvedUrlScheme=${downloadDiagnostics.resolvedUrlScheme}\n")
            sb.append("ResolvedUrlHost=${downloadDiagnostics.resolvedUrlHost}\n")
            sb.append("ResolvedUrlExpiredSuspected=${downloadDiagnostics.resolvedUrlExpiredSuspected}\n\n")
            sb.append("RequestUserAgentPresent=${downloadDiagnostics.requestUserAgentPresent}\n")
            sb.append("RequestRefererPresent=${downloadDiagnostics.requestRefererPresent}\n")
            sb.append("RequestCookieRequired=${downloadDiagnostics.requestCookieRequired}\n")
            sb.append("RequestCookiePresent=${downloadDiagnostics.requestCookiePresent}\n")
            sb.append("ExtractorHeadersCount=${downloadDiagnostics.extractorHeadersCount}\n")
            sb.append("HeadersPassedToDownloadManager=${downloadDiagnostics.headersPassedToDownloadManager}\n")
            sb.append("MissingRequiredHeaders=${downloadDiagnostics.missingRequiredHeaders}\n\n")
            sb.append("DownloadManagerStatus=${downloadDiagnostics.downloadManagerStatus}\n")
            sb.append("DownloadManagerReasonCode=${downloadDiagnostics.downloadManagerReasonCode}\n")
            sb.append("DownloadManagerReasonName=${downloadDiagnostics.downloadManagerReasonName}\n")
            sb.append("LocalUriPresent=${downloadDiagnostics.localUriPresent}\n")
            sb.append("DownloadedBytes=${downloadDiagnostics.downloadedBytes}\n")
            sb.append("TotalExpectedBytes=${downloadDiagnostics.totalExpectedBytes}\n")
            sb.append("FailureStage=${downloadDiagnostics.failureStage}\n")
            sb.append("FinalDownloadError=${downloadDiagnostics.finalDownloadError}\n\n")

            // V11 Additions
            sb.append("AppDownloadId=${downloadDiagnostics.appDownloadId}\n")
            sb.append("DownloadManagerId=${downloadDiagnostics.downloadManagerId}\n")
            sb.append("DownloadManagerIdPresent=${downloadDiagnostics.downloadManagerIdPresent}\n")
            sb.append("DownloadManagerIdPersisted=${downloadDiagnostics.downloadManagerIdPersisted}\n")
            sb.append("DownloadManagerIdAssociatedWithCorrectAppItem=${downloadDiagnostics.downloadManagerIdAssociatedWithCorrectAppItem}\n\n")

            sb.append("DownloadManagerQueriedId=${downloadDiagnostics.downloadManagerQueriedId}\n")
            sb.append("DownloadManagerQueryMatchedRow=${downloadDiagnostics.downloadManagerQueryMatchedRow}\n")
            sb.append("DownloadManagerQueryRowCount=${downloadDiagnostics.downloadManagerQueryRowCount}\n")
            sb.append("DownloadManagerRowMissing=${downloadDiagnostics.downloadManagerRowMissing}\n")
            sb.append("DownloadManagerFreshQueryPerformed=${downloadDiagnostics.downloadManagerFreshQueryPerformed}\n")
            sb.append("DownloadManagerCurrentStatus=${downloadDiagnostics.downloadManagerCurrentStatus}\n")
            sb.append("DiagnosticStatusTimestamp=${downloadDiagnostics.diagnosticStatusTimestamp}\n\n")

            sb.append("ApplicationStatus=${downloadDiagnostics.applicationStatus}\n")
            sb.append("ApplicationStatusSetBy=${downloadDiagnostics.applicationStatusSetBy}\n")
            sb.append("ApplicationStatusSetAt=${downloadDiagnostics.applicationStatusSetAt}\n")
            sb.append("ApplicationFailureTrigger=${downloadDiagnostics.applicationFailureTrigger}\n")
            sb.append("ApplicationFailureReason=${downloadDiagnostics.applicationFailureReason}\n\n")

            sb.append("ElapsedSinceEnqueueSeconds=${downloadDiagnostics.elapsedSinceEnqueueSeconds}\n")
            sb.append("DownloadedBytes=${downloadDiagnostics.downloadedBytes}\n")
            sb.append("TotalExpectedBytes=${downloadDiagnostics.totalExpectedBytes}\n")
            sb.append("ProgressEverObserved=${downloadDiagnostics.progressEverObserved}\n")
            sb.append("LastProgressBytes=${downloadDiagnostics.lastProgressBytes}\n")
            sb.append("LastProgressTimestamp=${downloadDiagnostics.lastProgressTimestamp}\n")
            sb.append("StuckAtZeroBytes=${downloadDiagnostics.stuckAtZeroBytes}\n\n")

            // Extended Merge Diagnostics
            sb.append("SelectedVideoCodec=${downloadDiagnostics.selectedVideoCodec}\n")
            sb.append("SelectedAudioCodec=${downloadDiagnostics.selectedAudioCodec}\n")
            sb.append("SelectedFormatHasVideo=${downloadDiagnostics.selectedFormatHasVideo}\n")
            sb.append("SelectedFormatHasAudio=${downloadDiagnostics.selectedFormatHasAudio}\n")
            sb.append("SeparateAudioAvailable=${downloadDiagnostics.separateAudioAvailable}\n")
            sb.append("ActualYtDlpFormatSelector=${downloadDiagnostics.actualYtDlpFormatSelector}\n")
            sb.append("MergeRequired=${downloadDiagnostics.mergeRequired}\n")
            sb.append("MergeEngine=${downloadDiagnostics.mergeEngine}\n")
            sb.append("FfmpegInitialized=${downloadDiagnostics.ffmpegInitialized}\n")
            sb.append("FfmpegInitializationError=${downloadDiagnostics.ffmpegInitializationError}\n")
            sb.append("YtDlpDownloadInvoked=${downloadDiagnostics.ytDlpDownloadInvoked}\n")
            sb.append("YtDlpDownloadCompleted=${downloadDiagnostics.ytDlpDownloadCompleted}\n")
            sb.append("FinalFileExists=${downloadDiagnostics.finalFileExists}\n")
            sb.append("FinalFileSize=${downloadDiagnostics.finalFileSize}\n")
            sb.append("FinalFileVideoStreamDetected=${downloadDiagnostics.finalFileVideoStreamDetected}\n")
            sb.append("FinalFileAudioStreamDetected=${downloadDiagnostics.finalFileAudioStreamDetected}\n")
            sb.append("FinalFileValidationPassed=${downloadDiagnostics.finalFileValidationPassed}\n")
        }

        return sb.toString()
    }
}
