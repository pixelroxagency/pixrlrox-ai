package com.example.data.downloader

import org.junit.Assert.*
import org.junit.Test

class YtDlpMergeLogicTest {

    // Test a pure representation of the logic in BaseYtDlpExtractor
    private fun computeAudioMetadata(
        vcodec: String?,
        acodec: String?,
        hasSeparateAudio: Boolean
    ): Pair<Boolean, Boolean> {
        val hasVideoCodec = !vcodec.isNullOrBlank() && !vcodec.equals("none", ignoreCase = true)
        val hasAudioCodec = !acodec.isNullOrBlank() && !acodec.equals("none", ignoreCase = true)

        val audioIncluded = hasAudioCodec
        val mergeRequired = hasVideoCodec && !hasAudioCodec && hasSeparateAudio
        return Pair(audioIncluded, mergeRequired)
    }

    @Test
    fun `test muxed video logic`() {
        val (audioIncluded, mergeRequired) = computeAudioMetadata("h264", "aac", true)
        assertTrue("Muxed video should have audioIncluded=true", audioIncluded)
        assertFalse("Muxed video should have mergeRequired=false", mergeRequired)
    }

    @Test
    fun `test video-only with separate audio logic`() {
        val (audioIncluded, mergeRequired) = computeAudioMetadata("h264", "none", true)
        assertFalse("Video-only should have audioIncluded=false", audioIncluded)
        assertTrue("Video-only with separate audio should have mergeRequired=true", mergeRequired)
    }

    @Test
    fun `test video-only without separate audio logic`() {
        val (audioIncluded, mergeRequired) = computeAudioMetadata("h264", "none", false)
        assertFalse("Video-only should have audioIncluded=false", audioIncluded)
        assertFalse("Video-only without separate audio should have mergeRequired=false", mergeRequired)
    }

    @Test
    fun `test audio-only logic`() {
        val (audioIncluded, mergeRequired) = computeAudioMetadata("none", "aac", false)
        assertTrue("Audio-only should have audioIncluded=true", audioIncluded)
        assertFalse("Audio-only should have mergeRequired=false", mergeRequired)
    }

    @Test
    fun `test output file extraction from merger stdout`() {
        val stdout = """
            [download] Destination: /storage/emulated/0/Download/fb_video.f1030383410050327v.mp4
            [download] 100% of 10.50MiB in 00:01
            [download] Destination: /storage/emulated/0/Download/fb_video.f251.m4a
            [download] 100% of 1.20MiB in 00:00
            [Merger] Merging formats into "/storage/emulated/0/Download/fb_video.mp4"
            Deleting original file /storage/emulated/0/Download/fb_video.f1030383410050327v.mp4 (pass -k to keep)
            Deleting original file /storage/emulated/0/Download/fb_video.f251.m4a (pass -k to keep)
        """.trimIndent()

        val mergerRegex = Regex("""\[Merger\]\s+Merging formats into\s+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE)
        val match = stdout.lines().reversed().firstNotNullOfOrNull { mergerRegex.find(it) }
        assertNotNull("Should find merger line", match)
        assertEquals("/storage/emulated/0/Download/fb_video.mp4", match!!.groupValues[1])
    }

    @Test
    fun `test prefix matching excludes partial files`() {
        val tempDir = java.io.File.createTempFile("test_downloads", "")
        tempDir.delete()
        tempDir.mkdirs()
        try {
            val baseName = "test_vid_1234"
            val partFile = java.io.File(tempDir, "$baseName.mp4.part")
            partFile.writeText("partial content")
            val ytdlFile = java.io.File(tempDir, "$baseName.ytdl")
            ytdlFile.writeText("ytdl metadata")
            val finalMergedFile = java.io.File(tempDir, "$baseName.mkv")
            finalMergedFile.writeText("full merged media content")

            val candidates = tempDir.listFiles()?.filter { f ->
                f.isFile &&
                f.length() > 0 &&
                f.name.startsWith(baseName) &&
                !f.name.endsWith(".part", ignoreCase = true) &&
                !f.name.endsWith(".ytdl", ignoreCase = true) &&
                !f.name.endsWith(".temp", ignoreCase = true)
            } ?: emptyList()

            assertEquals(1, candidates.size)
            assertEquals("$baseName.mkv", candidates.first().name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
