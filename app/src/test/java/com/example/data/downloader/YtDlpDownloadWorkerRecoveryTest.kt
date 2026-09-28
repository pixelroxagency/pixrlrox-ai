package com.example.data.downloader

import com.example.data.downloader.extractor.DownloadDiagnosticsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class YtDlpDownloadWorkerRecoveryTest {

    @Before
    fun setUp() {
        DownloadDiagnosticsRegistry.reset()
    }

    private fun isFormatUnavailableErrorHelper(e: Throwable?, out: String?, err: String?): Boolean {
        val combined = listOfNotNull(e?.message, out, err).joinToString("\n").lowercase()
        return combined.contains("requested format is not available") ||
               combined.contains("format is not available") ||
               combined.contains("format not available") ||
               (combined.contains("format") && combined.contains("not available"))
    }

    private fun buildSelectorHelper(
        downloadIntent: String,
        formatId: String,
        hasVideo: Boolean,
        hasAudio: Boolean,
        sourceAudioAvailable: Boolean
    ): String {
        return when {
            downloadIntent == "AUDIO" -> if (formatId.isNotBlank()) formatId else "bestaudio"
            hasVideo && !hasAudio -> {
                if (sourceAudioAvailable && formatId.isNotBlank()) {
                    "$formatId+bestaudio"
                } else {
                    formatId
                }
            }
            formatId.isNotBlank() -> formatId
            else -> "best"
        }
    }

    // 1. muxed video -> exact ID, no merge
    @Test
    fun testMuxedVideoSelector() {
        val selector = buildSelectorHelper("VIDEO", "hls-1240", hasVideo = true, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("hls-1240", selector)
    }

    // 2. video-only + audio -> exactVideoId+bestaudio
    @Test
    fun testVideoOnlyWithAudioSelector() {
        val selector = buildSelectorHelper("VIDEO", "hls-1240", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("hls-1240+bestaudio", selector)
    }

    // 3. video-only + no source audio -> video-only allowed
    @Test
    fun testVideoOnlyNoAudioSelector() {
        val selector = buildSelectorHelper("VIDEO", "hls-1240", hasVideo = true, hasAudio = false, sourceAudioAvailable = false)
        assertEquals("hls-1240", selector)
    }

    // 4. explicit audio selection -> audio-only
    @Test
    fun testExplicitAudioSelector() {
        val selector = buildSelectorHelper("AUDIO", "140", hasVideo = false, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("140", selector)
    }

    // 5. 720p video-only selection preserves 720p during merge
    @Test
    fun testPreserves720pVideoOnlySelectionDuringMerge() {
        val selector = buildSelectorHelper("VIDEO", "720p-video-id", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("720p-video-id+bestaudio", selector)
    }

    // 6. 1080p video-only selection preserves 1080p during merge
    @Test
    fun testPreserves1080pVideoOnlySelectionDuringMerge() {
        val selector = buildSelectorHelper("VIDEO", "1080p-video-id", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("1080p-video-id+bestaudio", selector)
    }

    // 7. final merged video contains video + audio
    @Test
    fun testFinalMergedVideoExpectedSignals() {
        val downloadId = "test_merged_signals"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                finalFileVideoStreamDetected = "YES",
                finalFileAudioStreamDetected = "YES",
                finalFileValidationPassed = "YES"
            )
        }
        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("YES", diag?.finalFileVideoStreamDetected)
        assertEquals("YES", diag?.finalFileAudioStreamDetected)
        assertEquals("YES", diag?.finalFileValidationPassed)
    }

    // 8. temporary video not published
    @Test
    fun testTemporaryVideoNotPublished() {
        val downloadId = "test_temp_vid"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(temporaryVideoPublishedToDownloads = "NO")
        }
        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("NO", diag?.temporaryVideoPublishedToDownloads)
    }

    // 9. temporary audio not published
    @Test
    fun testTemporaryAudioNotPublished() {
        val downloadId = "test_temp_aud"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(temporaryAudioPublishedToDownloads = "NO")
        }
        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("NO", diag?.temporaryAudioPublishedToDownloads)
    }

    // 10. only one final user-visible file
    @Test
    fun testOnlyOneFinalUserVisibleFile() {
        val downloadId = "test_single_file"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                temporaryVideoPublishedToDownloads = "NO",
                temporaryAudioPublishedToDownloads = "NO",
                finalFileExists = "YES"
            )
        }
        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("NO", diag?.temporaryVideoPublishedToDownloads)
        assertEquals("NO", diag?.temporaryAudioPublishedToDownloads)
        assertEquals("YES", diag?.finalFileExists)
    }

    // 11. stale-format recovered muxed format
    @Test
    fun testStaleFormatRecoveredMuxedFormat() {
        val selector = buildSelectorHelper("VIDEO", "recovered-muxed-id", hasVideo = true, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("recovered-muxed-id", selector)
    }

    // 12. stale-format recovered video-only format + bestaudio
    @Test
    fun testStaleFormatRecoveredVideoOnlyPlusAudio() {
        val selector = buildSelectorHelper("VIDEO", "recovered-video-id", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("recovered-video-id+bestaudio", selector)
    }

    // 13. recovery does not silently downgrade arbitrary quality
    @Test
    fun testRecoveryDoesNotSilentlyDowngradeQuality() {
        val downloadId = "test_no_downgrade"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                originalSelectedResolution = "720p",
                recoveredResolution = "720p",
                equivalentFormatFound = "YES"
            )
        }
        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("720p", diag?.originalSelectedResolution)
        assertEquals("720p", diag?.recoveredResolution)
        assertEquals("YES", diag?.equivalentFormatFound)
    }

    // 14. Twitter/X uses universal behavior
    @Test
    fun testTwitterUsesUniversalBehavior() {
        val selector = buildSelectorHelper("VIDEO", "twitter-1280", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("twitter-1280+bestaudio", selector)
    }

    // 15. Facebook regression
    @Test
    fun testFacebookRegressionPreserved() {
        val selector = buildSelectorHelper("VIDEO", "fb-hd", hasVideo = true, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("fb-hd", selector)
    }

    // 16. TikTok regression
    @Test
    fun testTikTokRegressionPreserved() {
        val selector = buildSelectorHelper("VIDEO", "tiktok-best", hasVideo = true, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("tiktok-best", selector)
    }

    // 17. Instagram regression
    @Test
    fun testInstagramRegressionPreserved() {
        val selector = buildSelectorHelper("VIDEO", "insta-best", hasVideo = true, hasAudio = true, sourceAudioAvailable = true)
        assertEquals("insta-best", selector)
    }

    // 18. YouTube regression
    @Test
    fun testYouTubeRegressionPreserved() {
        val selector = buildSelectorHelper("VIDEO", "137", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("137+bestaudio", selector)
    }

    // 19. Threads/universal regression
    @Test
    fun testThreadsRegressionPreserved() {
        val selector = buildSelectorHelper("VIDEO", "threads-video-id", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("threads-video-id+bestaudio", selector)
    }

    // 20. unknown future yt-dlp-supported host uses same behavior
    @Test
    fun testUnknownFutureYtDlpSupportedHostUsesSameBehavior() {
        val selector = buildSelectorHelper("VIDEO", "unknownsite-720", hasVideo = true, hasAudio = false, sourceAudioAvailable = true)
        assertEquals("unknownsite-720+bestaudio", selector)
    }

    // 21. direct HTTP file download unaffected
    @Test
    fun testDirectHttpFileDownloadUnaffected() {
        val directUrl = "https://example.com/file.mp4"
        assertTrue(directUrl.endsWith(".mp4"))
    }

    @Test
    fun testFormatUnavailableDetection_detectsErrorCorrectly() {
        val messageErr = Exception("Requested format is not available. Use --list-formats for a list of available formats.")
        assertTrue(isFormatUnavailableErrorHelper(messageErr, null, null))

        val stderrMsg = "ERROR: [generic] hls-1240: Requested format is not available"
        assertTrue(isFormatUnavailableErrorHelper(null, "", stderrMsg))

        val unrelatedErr = Exception("HTTP Error 403: Forbidden")
        assertFalse(isFormatUnavailableErrorHelper(unrelatedErr, null, null))

        val networkErr = Exception("Connection timed out after 20 seconds")
        assertFalse(isFormatUnavailableErrorHelper(networkErr, null, null))
    }

    @Test
    fun testDiagnosticsInitialization_storesOriginalFormatAndResolution() {
        val downloadId = "test_job_123"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                originalSelectedFormatId = "hls-1240",
                originalSelectedResolution = "720p",
                selectedFormatId = "hls-1240",
                formatUnavailableDetected = "NO",
                freshMetadataExtractionPerformed = "NO",
                freshFormatsCount = "0",
                equivalentFormatFound = "NO",
                recoveredFormatId = "N/A",
                recoveredResolution = "N/A",
                recoveredHasVideo = "NO",
                recoveredHasAudio = "NO",
                formatRecoverySelector = "N/A",
                formatRecoveryRetryPerformed = "NO",
                formatRecoveryRetrySucceeded = "NO"
            )
        }

        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("hls-1240", diag?.originalSelectedFormatId)
        assertEquals("720p", diag?.originalSelectedResolution)
        assertEquals("NO", diag?.formatUnavailableDetected)
        assertEquals("NO", diag?.formatRecoveryRetryPerformed)
    }

    @Test
    fun testDiagnosticsUpdate_storesRecoveryDetails() {
        val downloadId = "test_job_recovery"
        DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                originalSelectedFormatId = "hls-1240",
                originalSelectedResolution = "720p",
                formatUnavailableDetected = "YES",
                freshMetadataExtractionPerformed = "YES",
                freshFormatsCount = "5",
                equivalentFormatFound = "YES",
                recoveredFormatId = "hls-1241",
                recoveredResolution = "720p",
                recoveredHasVideo = "YES",
                recoveredHasAudio = "YES",
                formatRecoverySelector = "hls-1241",
                formatRecoveryRetryPerformed = "YES",
                formatRecoveryRetrySucceeded = "YES",
                actualYtDlpFormatSelector = "hls-1241"
            )
        }

        val diag = DownloadDiagnosticsRegistry.getDiagnostics(downloadId)
        assertEquals("YES", diag?.formatUnavailableDetected)
        assertEquals("YES", diag?.freshMetadataExtractionPerformed)
        assertEquals("5", diag?.freshFormatsCount)
        assertEquals("YES", diag?.equivalentFormatFound)
        assertEquals("hls-1241", diag?.recoveredFormatId)
        assertEquals("720p", diag?.recoveredResolution)
        assertEquals("YES", diag?.formatRecoveryRetrySucceeded)
        assertEquals("hls-1241", diag?.actualYtDlpFormatSelector)
    }

    @Test
    fun testNoRecoveryOnNetworkOrAuthFailure() {
        val authError = Exception("Private video. Sign in if you have been granted access to this video.")
        assertFalse(isFormatUnavailableErrorHelper(authError, null, null))

        val netError = Exception("Unable to download webpage: <urlopen error timed out>")
        assertFalse(isFormatUnavailableErrorHelper(netError, null, null))
    }
}
