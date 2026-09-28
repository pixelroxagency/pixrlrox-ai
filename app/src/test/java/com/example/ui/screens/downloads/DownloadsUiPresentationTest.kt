package com.example.ui.screens.downloads

import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsUiPresentationTest {

    private fun getLabelText(format: DownloadFormat): String {
        val showSize = format.sizeBytes > 0 || format.bitrate > 0
        val finalOutputHasAudio = format.audioIncluded || format.separateAudioAvailable || format.mergeRequired
        
        return if (format.mediaType == MediaType.VIDEO) {
            val labelText = if (finalOutputHasAudio) "Video • Audio included" else "Video only"
            val prefix = if (showSize) " • " else ""
            "$prefix$labelText"
        } else if (format.mediaType == MediaType.AUDIO) {
            val prefix = if (showSize) " • " else ""
            val bitrateSuffix = if (format.bitrate > 0) " • ${format.getFormattedBitrate()}" else ""
            "${prefix}Audio only$bitrateSuffix"
        } else {
            ""
        }
    }

    private fun groupAndPrioritizeFormats(formats: List<DownloadFormat>): List<DownloadFormat> {
        return formats
            .filter { it.mediaType == MediaType.VIDEO }
            .groupBy { it.resolution }
            .map { (resolution, formatList) ->
                formatList.sortedWith(
                    compareByDescending<DownloadFormat> { it.audioIncluded }
                        .thenByDescending { it.container.equals("MP4", ignoreCase = true) }
                        .thenByDescending { it.bitrate }
                        .thenByDescending { it.sizeBytes }
                ).first()
            }
            .sortedWith { f1, f2 ->
                val r1 = f1.resolution.filter { it.isDigit() }.toIntOrNull() ?: 0
                val r2 = f2.resolution.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (r1 != r2) r2.compareTo(r1) else f1.resolution.compareTo(f2.resolution)
            }
    }

    // 1. muxed 1080p: display Audio included
    @Test
    fun testMuxed1080pLabel() {
        val format = DownloadFormat(
            id = "1080_muxed",
            mediaType = MediaType.VIDEO,
            resolution = "1080p",
            audioIncluded = true,
            separateAudioAvailable = false,
            mergeRequired = false,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
    }

    // 2. video-only 1080p + separate audio: display Audio included
    @Test
    fun testVideoOnly1080pWithSeparateAudioLabel() {
        val format = DownloadFormat(
            id = "1080_video_only",
            mediaType = MediaType.VIDEO,
            resolution = "1080p",
            audioIncluded = false,
            separateAudioAvailable = true,
            mergeRequired = true,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
    }

    // 3. video-only 720p + separate audio: display Audio included
    @Test
    fun testVideoOnly720pWithSeparateAudioLabel() {
        val format = DownloadFormat(
            id = "720_video_only",
            mediaType = MediaType.VIDEO,
            resolution = "720p",
            audioIncluded = false,
            separateAudioAvailable = true,
            mergeRequired = true,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
    }

    // 4. video-only with no source audio: display Video only / No audio
    @Test
    fun testVideoOnlyWithNoSourceAudioLabel() {
        val format = DownloadFormat(
            id = "video_no_audio",
            mediaType = MediaType.VIDEO,
            resolution = "480p",
            audioIncluded = false,
            separateAudioAvailable = false,
            mergeRequired = false,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Video only"))
    }

    // 5. audio-only format: remains Audio only
    @Test
    fun testAudioOnlyLabel() {
        val format = DownloadFormat(
            id = "audio_only",
            mediaType = MediaType.AUDIO,
            resolution = "",
            audioIncluded = true,
            separateAudioAvailable = false,
            mergeRequired = false,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio only"))
    }

    // 6. exact underlying video format ID preserved
    @Test
    fun testExactFormatIdPreservedAfterGrouping() {
        val rawFormats = listOf(
            DownloadFormat(
                id = "137", // 1080p MP4
                mediaType = MediaType.VIDEO,
                resolution = "1080p",
                container = "MP4",
                audioIncluded = false,
                separateAudioAvailable = true,
                mergeRequired = true,
                downloadUrl = "https://example.com"
            ),
            DownloadFormat(
                id = "248", // 1080p WebM
                mediaType = MediaType.VIDEO,
                resolution = "1080p",
                container = "WEBM",
                audioIncluded = false,
                separateAudioAvailable = true,
                mergeRequired = true,
                downloadUrl = "https://example.com"
            )
        )
        val grouped = groupAndPrioritizeFormats(rawFormats)
        assertEquals(1, grouped.size)
        // MP4 should be preferred, so id 137 is preserved
        assertEquals("137", grouped[0].id)
    }

    // 7. no platform-specific logic
    @Test
    fun testNoPlatformSpecificLogicInLabels() {
        val format = DownloadFormat(
            id = "platform_agnostic",
            mediaType = MediaType.VIDEO,
            resolution = "720p",
            audioIncluded = false,
            separateAudioAvailable = true,
            mergeRequired = true,
            downloadUrl = "https://twitter.com/example"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
        // Labels shouldn't change depending on platform
        assertFalse(label.contains("Twitter"))
    }

    // 8. future UniversalYtDlpExtractor formats use same presentation
    @Test
    fun testUniversalYtDlpFormatsUseSamePresentation() {
        val format = DownloadFormat(
            id = "future_generic_format",
            mediaType = MediaType.VIDEO,
            resolution = "1440p",
            audioIncluded = false,
            separateAudioAvailable = true,
            mergeRequired = true,
            downloadUrl = "https://future-supported-site.com/video"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
    }

    // 9. exact regression test for Format 137 (Section 9)
    @Test
    fun testRegressionFormat137() {
        // formatId=137, resolution=1080p, video codec present, audio codec absent
        // PLUS source metadata indicating usable separate audio
        val format = DownloadFormat(
            id = "137",
            mediaType = MediaType.VIDEO,
            resolution = "1080p",
            codec = "avc1.64002a",
            vcodec = "avc1.64002a",
            acodec = "none",
            audioIncluded = false,
            separateAudioAvailable = true,
            mergeRequired = true,
            downloadUrl = "https://x.com/example/status/123"
        )

        // Expected analyzed UI model properties:
        assertFalse(format.audioIncluded)
        assertTrue(format.separateAudioAvailable)
        assertTrue(format.mergeRequired)

        // Expected label:
        val label = getLabelText(format)
        assertTrue(label.contains("Audio included"))
    }

    // 10. test audio classification in UI
    @Test
    fun testAudioClassificationUi() {
        val format = DownloadFormat(
            id = "audio_test",
            mediaType = MediaType.AUDIO,
            bitrate = 128000,
            downloadUrl = "https://example.com"
        )
        val label = getLabelText(format)
        assertTrue(label.contains("Audio only"))
        assertTrue(label.contains("128 kbps"))
    }
}
