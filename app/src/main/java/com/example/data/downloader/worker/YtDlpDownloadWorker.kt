package com.example.data.downloader.worker

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.database.dao.download.DownloadDao
import com.example.data.downloader.extractor.YtDlpInitializer
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FormatRecoveryException(message: String) : Exception(message)

class YtDlpDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val downloadDao: DownloadDao by lazy {
        com.example.core.database.AppDatabase.getInstance(appContext).downloadDao()
    }

    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_FORMAT_ID = "format_id"
        const val KEY_FILENAME = "filename"
        const val KEY_MERGE_REQUIRED = "merge_required"
        const val KEY_SEPARATE_AUDIO_AVAILABLE = "separate_audio_available"
        const val KEY_VCODEC = "vcodec"
        const val KEY_ACODEC = "acodec"
        const val KEY_SELECTED_RESOLUTION = "selected_resolution"
        const val KEY_DOWNLOAD_INTENT = "download_intent"
        
        private const val TAG = "YtDlpDownloadWorker"
    }

    private fun buildSelector(
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

    private fun cleanupTemporaryFiles(downloadsDir: File, baseFilename: String, finalFile: File?) {
        try {
            val baseName = baseFilename.substringBeforeLast(".")
            downloadsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.exists()) {
                    val name = file.name
                    val isTempSuffix = name.endsWith(".part", ignoreCase = true) ||
                                       name.endsWith(".ytdl", ignoreCase = true) ||
                                       name.endsWith(".temp", ignoreCase = true)
                    
                    val isIntermediateStream = name.startsWith(baseName) && 
                                               file.absolutePath != finalFile?.absolutePath &&
                                               (name.contains(".f") || name.contains("temp") || name.contains(".video") || name.contains(".audio"))

                    if (isTempSuffix || isIntermediateStream) {
                        Log.i(TAG, "Cleaning up temporary file: ${file.absolutePath}")
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary files", e)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val formatId = inputData.getString(KEY_FORMAT_ID) ?: ""
        val filename = inputData.getString(KEY_FILENAME) ?: "download_${System.currentTimeMillis()}.mp4"
        val mergeRequired = inputData.getBoolean(KEY_MERGE_REQUIRED, false)
        val separateAudioAvailable = inputData.getBoolean(KEY_SEPARATE_AUDIO_AVAILABLE, mergeRequired)
        val vcodec = inputData.getString(KEY_VCODEC) ?: "N/A"
        val acodec = inputData.getString(KEY_ACODEC) ?: "N/A"
        val selectedResolution = inputData.getString(KEY_SELECTED_RESOLUTION) ?: "N/A"
        val downloadIntent = inputData.getString(KEY_DOWNLOAD_INTENT) ?: "VIDEO"
        
        val hasVideo = vcodec != "N/A" && vcodec.isNotBlank() && !vcodec.equals("none", ignoreCase = true)
        val hasAudio = acodec != "N/A" && acodec.isNotBlank() && !acodec.equals("none", ignoreCase = true)

        var currentSourceAudioAvailable = hasAudio || separateAudioAvailable
        var currentMergeRequired = (downloadIntent == "VIDEO") && hasVideo && !hasAudio && currentSourceAudioAvailable
        var currentAudioExpected = if (downloadIntent == "VIDEO" && currentSourceAudioAvailable) "YES" else "NO"

        val initialFormatSelector = buildSelector(
            downloadIntent = downloadIntent,
            formatId = formatId,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            sourceAudioAvailable = currentSourceAudioAvailable
        )

        Log.d(TAG, "Starting native yt-dlp download: id=$downloadId, url=$sourceUrl, selector=$initialFormatSelector, res=$selectedResolution, merge=$currentMergeRequired")
        
        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                downloadIntent = downloadIntent,
                originalSelectedFormatId = formatId,
                originalSelectedResolution = selectedResolution,
                selectedResolution = selectedResolution,
                ytDlpDownloadInvoked = "YES",
                selectedFormatId = formatId,
                selectedVideoCodec = vcodec,
                selectedAudioCodec = acodec,
                selectedFormatHasVideo = if (hasVideo) "YES" else "NO",
                selectedFormatHasAudio = if (hasAudio) "YES" else "NO",
                sourceAudioAvailable = if (currentSourceAudioAvailable) "YES" else "NO",
                separateAudioAvailable = if (separateAudioAvailable || currentMergeRequired) "YES" else "NO",
                mergeRequired = if (currentMergeRequired) "YES" else "NO",
                mergeEngine = "FFmpeg (youtubedl-android component)",
                transferEngine = "YTDLP",
                finalSelector = initialFormatSelector,
                finalAudioExpected = currentAudioExpected,
                videoComponentDownloaded = "NO",
                audioComponentDownloaded = "NO",
                mergeAttempted = "NO",
                mergeSucceeded = "NO",
                temporaryVideoPublishedToDownloads = "NO",
                temporaryAudioPublishedToDownloads = "NO",
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

        try {
            YtDlpInitializer.ensureInitialized(applicationContext)
            val isFfmpegReady = YtDlpInitializer.isFfmpegReady()
            val ffmpegError = YtDlpInitializer.getFfmpegErrorMessage()

            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    ffmpegInitialized = if (isFfmpegReady) "YES" else "NO",
                    ffmpegInitializationError = if (isFfmpegReady) "N/A" else (ffmpegError ?: "FFmpeg not initialized")
                )
            }
            
            if (currentMergeRequired && !isFfmpegReady) {
                Log.e(TAG, "Merge required but FFmpeg is not ready: $ffmpegError")
                downloadDao.updateDownloadStatus(downloadId, "FAILED", 0)
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(
                        finalDownloadError = "Audio and video could not be merged on this device.",
                        ffmpegInitialized = "NO",
                        ffmpegInitializationError = ffmpegError ?: "FFmpeg failed to initialize"
                    )
                }
                return@withContext Result.failure()
            }
            
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val intendedFile = File(downloadsDir, filename)

            var currentSelector = initialFormatSelector
            var response: com.yausername.youtubedl_android.YoutubeDLResponse? = null
            var retryCount = 0

            while (retryCount <= 1) {
                val request = YoutubeDLRequest(sourceUrl)
                request.addOption("-f", currentSelector)
                request.addOption("-o", intendedFile.absolutePath)
                request.addOption("--no-playlist")
                if (currentMergeRequired) {
                    request.addOption("--merge-output-format", "mp4")
                }

                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(
                        actualYtDlpFormatSelector = currentSelector,
                        finalSelector = currentSelector
                    )
                }

                try {
                    val startTime = System.currentTimeMillis()
                    response = YoutubeDL.getInstance().execute(request) { progress, eta, line ->
                        Log.v(TAG, "Progress: $progress%, ETA: $eta, Line: $line")
                        
                        val lowerLine = line.lowercase()
                        var currentInternalStatus = "DOWNLOADING"
                        
                        if (lowerLine.contains("[merger]") || lowerLine.contains("merging formats")) {
                            currentInternalStatus = "MERGING"
                            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                                current.copy(
                                    mergeAttempted = "YES",
                                    videoComponentDownloaded = "YES",
                                    audioComponentDownloaded = "YES"
                                )
                            }
                        } else if (lowerLine.contains("[download]") && (lowerLine.contains("destination") || lowerLine.contains("downloading"))) {
                            if (lowerLine.contains("audio") || lowerLine.contains("f140") || lowerLine.contains("m4a")) {
                                currentInternalStatus = "DOWNLOADING_AUDIO"
                                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                                    current.copy(audioComponentDownloaded = "YES")
                                }
                            } else {
                                currentInternalStatus = "DOWNLOADING_VIDEO"
                                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                                    current.copy(videoComponentDownloaded = "YES")
                                }
                            }
                        } else if (lowerLine.contains("fixing") || lowerLine.contains("correcting") || lowerLine.contains("validating")) {
                            currentInternalStatus = "VALIDATING"
                        }

                        workerScope.launch {
                            try {
                                downloadDao.updateDownloadStatus(downloadId, currentInternalStatus, progress.toInt().coerceIn(0, 100))
                                setProgress(workDataOf("progress" to progress.toInt()))
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update progress for $downloadId", e)
                            }
                        }
                    }

                    if (response != null && isFormatUnavailableError(null, response.out, response.err) && retryCount == 0) {
                        throw YoutubeDLException("Requested format is not available")
                    }

                    if (retryCount == 1) {
                        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                            current.copy(formatRecoveryRetrySucceeded = "YES")
                        }
                    }

                    if (currentMergeRequired) {
                        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                            current.copy(
                                mergeAttempted = "YES",
                                mergeSucceeded = "YES"
                            )
                        }
                    }

                    Log.i(TAG, "yt-dlp execution completed in ${System.currentTimeMillis() - startTime}ms, exitCode=${response?.exitCode ?: 0}")
                    break
                } catch (e: Exception) {
                    val out = response?.out
                    val err = response?.err
                    if (retryCount == 0 && isFormatUnavailableError(e, out, err)) {
                        retryCount++
                        Log.w(TAG, "Exact format unavailable for $downloadId. Attempting stale format recovery...")

                        val recovered = try {
                            attemptFormatRecovery(
                                downloadId = downloadId,
                                sourceUrl = sourceUrl,
                                selectedResolution = selectedResolution,
                                originalHasVideo = hasVideo || formatId.contains("video", ignoreCase = true) || !formatId.contains("audio", ignoreCase = true),
                                originalHasAudio = hasAudio
                            )
                        } catch (recEx: Exception) {
                            Log.e(TAG, "Format recovery failed for $downloadId: ${recEx.message}")
                            downloadDao.updateDownloadStatus(downloadId, "FAILED", 0)
                            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                                current.copy(
                                    formatUnavailableDetected = "YES",
                                    equivalentFormatFound = "NO",
                                    formatRecoveryRetryPerformed = "NO",
                                    formatRecoveryRetrySucceeded = "NO",
                                    finalDownloadError = recEx.message ?: "The selected quality is no longer available."
                                )
                            }
                            return@withContext Result.failure()
                        }

                        currentSelector = recovered.selector
                        currentMergeRequired = recovered.mergeRequired
                        currentSourceAudioAvailable = recovered.hasAudio || (recovered.hasVideo && !recovered.hasAudio && recovered.mergeRequired)
                        currentAudioExpected = if (downloadIntent == "VIDEO" && currentSourceAudioAvailable) "YES" else "NO"

                        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                            current.copy(
                                formatUnavailableDetected = "YES",
                                equivalentFormatFound = "YES",
                                recoveredFormatId = recovered.formatId,
                                recoveredResolution = recovered.resolution,
                                recoveredHasVideo = if (recovered.hasVideo) "YES" else "NO",
                                recoveredHasAudio = if (recovered.hasAudio) "YES" else "NO",
                                formatRecoverySelector = recovered.selector,
                                formatRecoveryRetryPerformed = "YES",
                                actualYtDlpFormatSelector = recovered.selector,
                                mergeRequired = if (recovered.mergeRequired) "YES" else "NO",
                                sourceAudioAvailable = if (currentSourceAudioAvailable) "YES" else "NO",
                                finalAudioExpected = currentAudioExpected,
                                finalSelector = recovered.selector
                            )
                        }

                        continue
                    } else {
                        if (retryCount == 1) {
                            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                                current.copy(formatRecoveryRetrySucceeded = "NO")
                            }
                        }
                        throw e
                    }
                }
            }

            val finalFile: File? = discoverOutputFile(
                response = response,
                intendedFile = intendedFile,
                downloadsDir = downloadsDir,
                filename = filename
            )

            cleanupTemporaryFiles(downloadsDir, filename, finalFile)

            if (finalFile != null && finalFile.exists() && finalFile.length() > 0) {
                val finalHasVideo = hasVideoStream(finalFile)
                val finalHasAudio = hasAudioStream(finalFile)
                
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(
                        ytDlpDownloadCompleted = "YES",
                        finalFileExists = "YES",
                        finalFileSize = finalFile.length().toString(),
                        finalFileVideoStreamDetected = if (finalHasVideo) "YES" else "NO",
                        finalFileAudioStreamDetected = if (finalHasAudio) "YES" else "NO"
                    )
                }

                val finalAudioRequired = currentAudioExpected == "YES"
                if (finalAudioRequired && !finalHasAudio) {
                    Log.e(TAG, "Merge was required but final file has no audio: $downloadId")
                    downloadDao.updateDownloadStatus(downloadId, "FAILED", 0)
                    com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                        current.copy(
                            finalFileValidationPassed = "NO",
                            finalDownloadError = "Validation failed: Final video file is missing audio stream."
                        )
                    }
                    return@withContext Result.failure()
                }

                downloadDao.markCompleted(
                    id = downloadId,
                    destinationUri = finalFile.absolutePath,
                    completedTime = System.currentTimeMillis()
                )
                
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(finalFileValidationPassed = "YES")
                }
                
                Log.i(TAG, "Native yt-dlp download COMPLETED: $downloadId -> ${finalFile.absolutePath}")
                Result.success()
            } else {
                downloadDao.updateDownloadStatus(downloadId, "FAILED", 0)
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(
                        ytDlpDownloadCompleted = "NO",
                        finalFileExists = if (intendedFile.exists()) "YES (empty)" else "NO",
                        finalDownloadError = "Output file was not created or is empty."
                    )
                }
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Native yt-dlp download FAILED: $downloadId", e)
            downloadDao.updateDownloadStatus(downloadId, "FAILED", 0)
            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    ytDlpDownloadCompleted = "NO",
                    finalDownloadError = e.message ?: "Unknown yt-dlp error"
                )
            }
            Result.failure()
        }
    }

    private fun isFormatUnavailableError(e: Throwable?, out: String?, err: String?): Boolean {
        val combined = listOfNotNull(e?.message, out, err).joinToString("\n").lowercase()
        return combined.contains("requested format is not available") ||
               combined.contains("format is not available") ||
               combined.contains("format not available") ||
               (combined.contains("format") && combined.contains("not available"))
    }

    private data class RecoveredFormatInfo(
        val formatId: String,
        val resolution: String,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val mergeRequired: Boolean,
        val selector: String
    )

    private fun attemptFormatRecovery(
        downloadId: String,
        sourceUrl: String,
        selectedResolution: String,
        originalHasVideo: Boolean,
        originalHasAudio: Boolean
    ): RecoveredFormatInfo {
        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                formatUnavailableDetected = "YES",
                freshMetadataExtractionPerformed = "YES"
            )
        }

        val freshRequest = YoutubeDLRequest(sourceUrl)
        freshRequest.addOption("--no-playlist")
        freshRequest.addOption("--no-warnings")
        freshRequest.addOption("--socket-timeout", "20")

        val info = try {
            YoutubeDL.getInstance().getInfo(freshRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract fresh metadata during format recovery for $sourceUrl", e)
            null
        }

        val rawFormats = info?.formats ?: emptyList()
        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
            current.copy(
                freshFormatsCount = rawFormats.size.toString()
            )
        }

        if (rawFormats.isEmpty()) {
            throw FormatRecoveryException("The selected quality is no longer available. Please analyze the link again and choose one of the currently available formats.")
        }

        val heightMatch = Regex("""(\d+)p?""").find(selectedResolution)
        val targetHeight = heightMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val hasSeparateAudioInFresh = rawFormats.any { fmt ->
            val ac = fmt.acodec?.trim()
            val vc = fmt.vcodec?.trim()
            val hasAc = !ac.isNullOrBlank() && !ac.equals("none", ignoreCase = true)
            val hasVc = !vc.isNullOrBlank() && !vc.equals("none", ignoreCase = true)
            hasAc && !hasVc
        }

        if (originalHasVideo) {
            val videoCandidates = rawFormats.filter { fmt ->
                val vc = fmt.vcodec?.trim()
                !vc.isNullOrBlank() && !vc.equals("none", ignoreCase = true)
            }

            val matchingCandidates = videoCandidates.filter { fmt ->
                val fmtRes = if (fmt.height > 0) "${fmt.height}p" else fmt.formatNote ?: ""
                (targetHeight > 0 && fmt.height == targetHeight) ||
                fmtRes.equals(selectedResolution, ignoreCase = true) ||
                (selectedResolution.isNotBlank() && selectedResolution != "N/A" && fmtRes.contains(selectedResolution, ignoreCase = true))
            }

            // Priority 1a: Muxed candidate at requested resolution
            val muxedCandidate = matchingCandidates.firstOrNull { fmt ->
                val ac = fmt.acodec?.trim()
                !ac.isNullOrBlank() && !ac.equals("none", ignoreCase = true)
            }

            if (muxedCandidate != null) {
                val resStr = if (muxedCandidate.height > 0) "${muxedCandidate.height}p" else selectedResolution
                val fmtId = muxedCandidate.formatId ?: "best"
                return RecoveredFormatInfo(
                    formatId = fmtId,
                    resolution = resStr,
                    hasVideo = true,
                    hasAudio = true,
                    mergeRequired = false,
                    selector = fmtId
                )
            }

            // Priority 1b: Video-only candidate at requested resolution + bestaudio
            val videoOnlyCandidate = matchingCandidates.firstOrNull()
            if (videoOnlyCandidate != null) {
                val resStr = if (videoOnlyCandidate.height > 0) "${videoOnlyCandidate.height}p" else selectedResolution
                val fmtId = videoOnlyCandidate.formatId ?: "bestvideo"
                return if (hasSeparateAudioInFresh) {
                    RecoveredFormatInfo(
                        formatId = fmtId,
                        resolution = resStr,
                        hasVideo = true,
                        hasAudio = false,
                        mergeRequired = true,
                        selector = "$fmtId+bestaudio"
                    )
                } else {
                    RecoveredFormatInfo(
                        formatId = fmtId,
                        resolution = resStr,
                        hasVideo = true,
                        hasAudio = false,
                        mergeRequired = false,
                        selector = fmtId
                    )
                }
            }
        } else {
            val audioCandidate = rawFormats.firstOrNull { fmt ->
                val ac = fmt.acodec?.trim()
                val vc = fmt.vcodec?.trim()
                val hasAc = !ac.isNullOrBlank() && !ac.equals("none", ignoreCase = true)
                val hasVc = !vc.isNullOrBlank() && !vc.equals("none", ignoreCase = true)
                hasAc && !hasVc
            }
            if (audioCandidate != null) {
                val fmtId = audioCandidate.formatId ?: "bestaudio"
                return RecoveredFormatInfo(
                    formatId = fmtId,
                    resolution = "Audio",
                    hasVideo = false,
                    hasAudio = true,
                    mergeRequired = false,
                    selector = fmtId
                )
            }
        }

        throw FormatRecoveryException("The selected quality is no longer available. Please analyze the link again and choose one of the currently available formats.")
    }

    private fun discoverOutputFile(
        response: com.yausername.youtubedl_android.YoutubeDLResponse?,
        intendedFile: File,
        downloadsDir: File,
        filename: String
    ): File? {
        if (response != null) {
            val fileFromResponse = parseOutputFileFromResponse(response, downloadsDir)
            if (fileFromResponse != null && fileFromResponse.exists() && fileFromResponse.isFile && fileFromResponse.length() > 0) {
                Log.i(TAG, "Discovered output file from YoutubeDLResponse: ${fileFromResponse.absolutePath} (${fileFromResponse.length()} bytes)")
                return fileFromResponse
            }
        }

        if (intendedFile.exists() && intendedFile.isFile && intendedFile.length() > 0) {
            Log.i(TAG, "Discovered output file at intended path: ${intendedFile.absolutePath} (${intendedFile.length()} bytes)")
            return intendedFile
        }

        val baseName = filename.substringBeforeLast(".")
        val candidates = downloadsDir.listFiles()?.filter { f ->
            f.isFile &&
            f.length() > 0 &&
            f.name.startsWith(baseName) &&
            !f.name.endsWith(".part", ignoreCase = true) &&
            !f.name.endsWith(".ytdl", ignoreCase = true) &&
            !f.name.endsWith(".temp", ignoreCase = true)
        } ?: emptyList()

        if (candidates.isNotEmpty()) {
            val bestCandidate = candidates.maxByOrNull { it.lastModified() }
            if (bestCandidate != null && bestCandidate.exists() && bestCandidate.length() > 0) {
                Log.i(TAG, "Discovered output file via directory prefix search: ${bestCandidate.absolutePath} (${bestCandidate.length()} bytes)")
                return bestCandidate
            }
        }

        return null
    }

    private fun parseOutputFileFromResponse(
        response: com.yausername.youtubedl_android.YoutubeDLResponse,
        downloadsDir: File
    ): File? {
        val lines = (response.out.lines() + response.err.lines()).map { it.trim() }

        val mergerRegex = Regex("""\[Merger\]\s+Merging formats into\s+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE)
        for (line in lines.reversed()) {
            val match = mergerRegex.find(line)
            if (match != null) {
                val path = match.groupValues[1].trim()
                val file = resolveFile(path, downloadsDir)
                if (file != null && file.exists() && file.isFile && file.length() > 0) {
                    return file
                }
            }
        }

        val fixupRegex = Regex("""\[(?:Fixup[A-Za-z0-9]+|VideoConvertor)\]\s+.*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        for (line in lines.reversed()) {
            val match = fixupRegex.find(line)
            if (match != null) {
                val path = match.groupValues[1].trim()
                val file = resolveFile(path, downloadsDir)
                if (file != null && file.exists() && file.isFile && file.length() > 0) {
                    return file
                }
            }
        }

        val downloadDestRegex = Regex("""\[download\]\s+Destination:\s+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE)
        for (line in lines.reversed()) {
            val match = downloadDestRegex.find(line)
            if (match != null) {
                val path = match.groupValues[1].trim()
                val file = resolveFile(path, downloadsDir)
                if (file != null && file.exists() && file.isFile && file.length() > 0 &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    !file.name.endsWith(".ytdl", ignoreCase = true)) {
                    return file
                }
            }
        }

        val alreadyDownloadedRegex = Regex("""\[download\]\s+["']?([^"']+)["']?\s+has already been downloaded""", RegexOption.IGNORE_CASE)
        for (line in lines.reversed()) {
            val match = alreadyDownloadedRegex.find(line)
            if (match != null) {
                val path = match.groupValues[1].trim()
                val file = resolveFile(path, downloadsDir)
                if (file != null && file.exists() && file.isFile && file.length() > 0) {
                    return file
                }
            }
        }

        return null
    }

    private fun resolveFile(path: String, downloadsDir: File): File? {
        val f = File(path)
        if (f.isAbsolute) return f
        return File(downloadsDir, path)
    }

    private fun hasVideoStream(file: File): Boolean {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            retriever.release()
            hasVideo == "yes"
        } catch (_: Exception) {
            false
        }
    }

    private fun hasAudioStream(file: File): Boolean {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val hasAudio = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            retriever.release()
            hasAudio == "yes"
        } catch (_: Exception) {
            false
        }
    }
}
