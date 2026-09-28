package com.example.data.repository

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.core.database.dao.download.DownloadDao
import com.example.core.database.entity.download.DownloadEntity
import com.example.data.downloader.analyzer.MediaAnalysisManager
import com.example.data.downloader.extractor.DownloadDiagnostics
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.downloader.worker.YtDlpDownloadWorker
import java.io.File
import java.util.UUID

class DownloadRepository @JvmOverloads constructor(
    private val context: Context,
    private val downloadDao: DownloadDao,
    analysisManager: MediaAnalysisManager? = null
) {
    private val analysisManager: MediaAnalysisManager = analysisManager ?: MediaAnalysisManager(context)

    // Secondary constructor for compatibility
    constructor(
        context: Context,
        downloadDao: DownloadDao,
        authRepository: Any?,
        analysisManager: MediaAnalysisManager? = null
    ) : this(context, downloadDao, analysisManager)

    val downloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()

    private val _downloadEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)
    val downloadEvents: kotlinx.coroutines.flow.Flow<String> = _downloadEvents

    private val customJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var progressJob: Job? = null
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    init {
        startProgressTrackingLoop()
        registerDownloadCompleteReceiver()
    }

    suspend fun analyzeUrl(url: String): MediaAnalysisResult {
        return analysisManager.analyze(url)
    }

    suspend fun enqueueDownload(url: String, filename: String): String {
        val ext = filename.substringAfterLast(".", "bin").lowercase()
        val mediaType = when (ext) {
            "mp4", "webm", "mkv", "mov", "3gp", "avi" -> MediaType.VIDEO
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> MediaType.AUDIO
            else -> MediaType.FILE
        }
        val format = DownloadFormat(
            id = UUID.randomUUID().toString(),
            mediaType = mediaType,
            container = ext.uppercase(),
            downloadUrl = url,
            label = filename
        )
        return enqueueDownload(format, filename, url)
    }

    suspend fun enqueueDownload(
        format: DownloadFormat,
        customTitle: String? = null,
        sourceUrl: String = "",
        thumbnailUri: String? = null
    ): String = withContext(Dispatchers.IO) {
        val downloadId = UUID.randomUUID().toString()
        val originalUrl = sourceUrl.ifBlank { format.downloadUrl }
        val title = customTitle?.ifBlank { null } ?: format.resolution.ifBlank { "Download" }

        val ext = when {
            format.container.isNotBlank() && format.container != "DEFAULT" -> format.container.lowercase()
            format.mediaType == MediaType.AUDIO -> "mp3"
            format.mediaType == MediaType.FILE -> {
                val urlExt = originalUrl.substringBefore("?").substringBefore("#").substringAfterLast(".", "").lowercase()
                if (urlExt.isNotBlank() && urlExt.length <= 5) urlExt else "bin"
            }
            else -> "mp4"
        }

        val safeBaseName = title.replace(Regex("[^a-zA-Z0-9.\\-_ ]"), "_").trim()
        val qualityTag = if (format.resolution.isNotBlank() && format.resolution != "Original") "_${format.resolution}" else ""
        val uniqueSuffix = System.currentTimeMillis() % 10000
        val finalFilename = if (safeBaseName.endsWith(".$ext", ignoreCase = true)) {
            safeBaseName
        } else {
            "${safeBaseName}${qualityTag}_${uniqueSuffix}.$ext"
        }

        val mimeType = when (ext) {
            "mp4" -> "video/mp4"
            "webm" -> if (format.mediaType == MediaType.AUDIO) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            else -> when (format.mediaType) {
                MediaType.AUDIO -> "audio/*"
                MediaType.VIDEO -> "video/*"
                MediaType.FILE -> "application/octet-stream"
            }
        }

        try {
            val host = try { Uri.parse(format.downloadUrl).host ?: "N/A" } catch (_: Exception) { "N/A" }
            val scheme = try { Uri.parse(format.downloadUrl).scheme ?: "N/A" } catch (_: Exception) { "N/A" }
            
            val urlLower = format.downloadUrl.lowercase()
            val hasExpiry = if (urlLower.contains("expire=")) "YES" else "NO"
            val hasSig = if (urlLower.contains("sig=") || urlLower.contains("signature=") || urlLower.contains("tk=")) "YES" else "NO"
            
            val uriParsed = try { Uri.parse(format.downloadUrl) } catch (_: Exception) { null }
            val expireParam = uriParsed?.getQueryParameter("expire")
            val expireTime = expireParam?.toLongOrNull() ?: 0L
            val ageMs = if (expireTime > 0) {
                val typicalLifetimeSec = 7200L // 2 hours
                val creationTimeSec = expireTime - typicalLifetimeSec
                val computedAge = System.currentTimeMillis() - (creationTimeSec * 1000)
                if (computedAge > 0) computedAge else 0L
            } else {
                0L
            }
            
            val delayMs = if (com.example.data.downloader.extractor.AnalysisTimestampTracker.lastAnalysisTimeMs > 0 && 
                com.example.data.downloader.extractor.AnalysisTimestampTracker.lastAnalysisUrl == sourceUrl) {
                System.currentTimeMillis() - com.example.data.downloader.extractor.AnalysisTimestampTracker.lastAnalysisTimeMs
            } else {
                0L
            }

            val isExpiredSuspected = if (expireTime > 0) {
                if (System.currentTimeMillis() > (expireTime * 1000)) "YES" else "NO"
            } else {
                "NO"
            }

            val platform = when {
                host.contains("tiktok") -> "TikTok"
                host.contains("instagram") -> "Instagram"
                host.contains("facebook") || host.contains("fbcdn") -> "Facebook"
                host.contains("googlevideo") || host.contains("youtube") -> "YouTube"
                else -> "N/A"
            }

            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    downloadId = downloadId,
                    sourcePlatform = platform,
                    selectedResolution = format.resolution.ifBlank { "N/A" },
                    selectedFormatId = format.id.ifBlank { "N/A" },
                    expectedMimeType = mimeType,
                    expectedFileSize = format.sizeBytes.toString(),
                    resolvedUrlPresent = if (format.downloadUrl.isNotBlank()) "YES" else "NO",
                    resolvedUrlScheme = scheme,
                    resolvedUrlHost = host,
                    resolvedUrlExpiredSuspected = isExpiredSuspected,
                    requestUserAgentPresent = "YES",
                    requestRefererPresent = "YES",
                    requestCookieRequired = if (platform == "TikTok" || platform == "Instagram") "YES" else "NO",
                    requestCookiePresent = "NO",
                    extractorHeadersCount = "0", // Discarded/Unavailable from wrapper
                    headersPassedToDownloadManager = "User-Agent, Referer",
                    missingRequiredHeaders = "Cookie (Discarded/Unavailable from yt-dlp extractor wrapper)",
                    failureStage = "Enqueueing",
                    resolvedUrlHasExpiryParameter = hasExpiry,
                    resolvedUrlHasSignatureParameters = hasSig,
                    resolvedUrlAgeAtEnqueueMs = ageMs.toString(),
                    analyzeToDownloadDelayMs = delayMs.toString(),
                    transferEngine = "DOWNLOAD_MANAGER"
                )
            }

            val request = DownloadManager.Request(Uri.parse(format.downloadUrl))
                .setTitle(finalFilename)
                .setDescription("PixelRox Direct Downloader")
                .setMimeType(mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFilename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            // Pass headers from extractor (yt-dlp)
            var passedHeadersCount = 0
            var cookiePresent = "NO"
            
            format.httpHeaders?.forEach { (key, value) ->
                try {
                    request.addRequestHeader(key, value)
                    passedHeadersCount++
                    if (key.equals("Cookie", ignoreCase = true)) {
                        cookiePresent = "YES"
                    }
                } catch (_: Exception) {}
            }

            // Fallback headers if not provided by extractor
            if (format.httpHeaders?.keys?.none { it.equals("User-Agent", ignoreCase = true) } != false) {
                request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                passedHeadersCount++
            }
            
            if (format.httpHeaders?.keys?.none { it.equals("Referer", ignoreCase = true) } != false) {
                if (originalUrl.startsWith("http://", ignoreCase = true) || originalUrl.startsWith("https://", ignoreCase = true)) {
                    try {
                        request.addRequestHeader("Referer", originalUrl)
                        passedHeadersCount++
                    } catch (_: Exception) {}
                }
            }

            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    extractorHeadersCount = passedHeadersCount.toString(),
                    requestCookiePresent = cookiePresent,
                    headersPassedToDownloadManager = format.httpHeaders?.keys?.joinToString(", ") ?: "User-Agent, Referer",
                    missingRequiredHeaders = if (cookiePresent == "NO" && (platform == "TikTok" || platform == "Instagram")) "Cookie" else "None",
                    separateAudioAvailable = if (format.separateAudioAvailable || format.mergeRequired) "YES" else "NO"
                )
            }

            if (format.transferEngine == com.example.data.downloader.model.TransferEngine.YTDLP) {
                // Native yt-dlp Download Path
                val inputData = Data.Builder()
                    .putString(YtDlpDownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .putString(YtDlpDownloadWorker.KEY_SOURCE_URL, sourceUrl.ifBlank { format.downloadUrl })
                    .putString(YtDlpDownloadWorker.KEY_FORMAT_ID, format.id)
                    .putString(YtDlpDownloadWorker.KEY_FILENAME, finalFilename)
                    .putBoolean(YtDlpDownloadWorker.KEY_MERGE_REQUIRED, format.mergeRequired)
                    .putBoolean(YtDlpDownloadWorker.KEY_SEPARATE_AUDIO_AVAILABLE, format.separateAudioAvailable || format.mergeRequired)
                    .putString(YtDlpDownloadWorker.KEY_VCODEC, format.vcodec)
                    .putString(YtDlpDownloadWorker.KEY_ACODEC, format.acodec)
                    .putString(YtDlpDownloadWorker.KEY_SELECTED_RESOLUTION, format.resolution)
                    .putString("download_intent", if (format.mediaType == com.example.data.downloader.model.MediaType.AUDIO) "AUDIO" else "VIDEO")
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<YtDlpDownloadWorker>()
                    .setInputData(inputData)
                    .addTag("download_$downloadId")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "download_$downloadId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                val entity = DownloadEntity(
                    id = downloadId,
                    filename = finalFilename,
                    url = format.downloadUrl,
                    destinationUri = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), finalFilename).absolutePath,
                    status = "DOWNLOADING",
                    progress = 0,
                    fileSize = format.sizeBytes,
                    timestamp = System.currentTimeMillis(),
                    downloadManagerId = -1L,
                    mediaType = format.mediaType.name,
                    quality = format.resolution.ifBlank { format.getDisplayName() },
                    format = format.container.uppercase(),
                    thumbnailUri = thumbnailUri,
                    downloadedBytes = 0L,
                    totalBytes = format.sizeBytes,
                    sourceUrl = sourceUrl.ifBlank { format.downloadUrl },
                    completedTimestamp = null,
                    transferEngine = "YTDLP"
                )
                downloadDao.insertDownload(entity)
                
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                    current.copy(
                        failureStage = "YTDLP Worker Running",
                        appDownloadId = downloadId,
                        applicationStatus = "DOWNLOADING",
                        applicationStatusSetBy = "enqueueDownload (YTDLP branch)",
                        transferEngine = "YTDLP"
                    )
                }
                
                return@withContext downloadId
            }

            val dmId = downloadManager.enqueue(request)
            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    failureStage = "DownloadManager Running",
                    appDownloadId = downloadId,
                    downloadManagerId = dmId.toString(),
                    downloadManagerIdPresent = "YES",
                    downloadManagerIdPersisted = "PRE_PERSIST",
                    applicationStatus = "DOWNLOADING",
                    applicationStatusSetBy = "enqueueDownload (Success branch)"
                )
            }
            val destinationPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                finalFilename
            ).absolutePath

            val entity = DownloadEntity(
                id = downloadId,
                filename = finalFilename,
                url = format.downloadUrl,
                destinationUri = destinationPath,
                status = "DOWNLOADING",
                progress = 0,
                fileSize = format.sizeBytes,
                timestamp = System.currentTimeMillis(),
                downloadManagerId = dmId,
                mediaType = format.mediaType.name,
                quality = format.resolution.ifBlank { format.getDisplayName() },
                format = format.container.uppercase(),
                thumbnailUri = thumbnailUri,
                downloadedBytes = 0L,
                totalBytes = format.sizeBytes,
                sourceUrl = sourceUrl.ifBlank { format.downloadUrl },
                completedTimestamp = null
            )

            downloadDao.insertDownload(entity)
            
            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(downloadManagerIdPersisted = "YES")
            }
            
            startProgressTrackingLoop()
            downloadId
        } catch (e: Exception) {
            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(downloadId) { current ->
                current.copy(
                    failureStage = "Enqueueing Failed",
                    finalDownloadError = e.message ?: "Exception during enqueueing",
                    applicationStatus = "FAILED",
                    applicationStatusSetBy = "enqueueDownload (Exception branch)",
                    applicationFailureTrigger = "Exception during DownloadManager.enqueue",
                    applicationFailureReason = e.javaClass.simpleName + ": " + e.message
                )
            }
            val entity = DownloadEntity(
                id = downloadId,
                filename = finalFilename,
                url = format.downloadUrl,
                destinationUri = "",
                status = "FAILED",
                progress = 0,
                fileSize = format.sizeBytes,
                timestamp = System.currentTimeMillis(),
                downloadManagerId = -1L,
                mediaType = format.mediaType.name,
                quality = format.resolution,
                format = format.container,
                thumbnailUri = thumbnailUri,
                downloadedBytes = 0L,
                totalBytes = format.sizeBytes,
                sourceUrl = sourceUrl,
                completedTimestamp = null
            )
            downloadDao.insertDownload(entity)
            throw e
        }
    }

    suspend fun cancelDownload(id: String) = withContext(Dispatchers.IO) {
        val job = customJobs.remove(id)
        if (job != null) {
            job.cancel()
        }
        val download = downloadDao.getDownloadById(id)
        if (download != null) {
            if (download.transferEngine == "YTDLP") {
                WorkManager.getInstance(context).cancelUniqueWork("download_$id")
            }
            if (download.downloadManagerId > 0) {
                try {
                    downloadManager.remove(download.downloadManagerId)
                } catch (_: Exception) {}
            }
            downloadDao.updateDownloadStatus(id, "CANCELLED", download.progress)
            if (download.destinationUri.isNotBlank()) {
                try {
                    if (download.destinationUri.startsWith("content://")) {
                        context.contentResolver.delete(Uri.parse(download.destinationUri), null, null)
                    } else {
                        val file = File(download.destinationUri)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadById(id)
        if (download != null) {
            downloadDao.updateDownloadStatus(id, "PAUSED", download.progress)
        }
    }

    suspend fun resumeDownload(id: String) = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadById(id)
        if (download != null) {
            downloadDao.updateDownloadStatus(id, "DOWNLOADING", download.progress)
        }
    }

    suspend fun deleteDownload(id: String, deleteFile: Boolean = true) = withContext(Dispatchers.IO) {
        val job = customJobs.remove(id)
        if (job != null) {
            job.cancel()
        }
        val download = downloadDao.getDownloadById(id)
        if (download != null) {
            if (download.downloadManagerId > 0 && download.status in listOf("DOWNLOADING", "QUEUED")) {
                try {
                    downloadManager.remove(download.downloadManagerId)
                } catch (_: Exception) {}
            }
            if (deleteFile && download.destinationUri.isNotBlank()) {
                try {
                    if (download.destinationUri.startsWith("content://")) {
                        context.contentResolver.delete(Uri.parse(download.destinationUri), null, null)
                    } else {
                        val file = File(download.destinationUri)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
            downloadDao.deleteDownload(id)
        }
    }

    private fun startProgressTrackingLoop() {
        if (progressJob?.isActive == true) return

        progressJob = coroutineScope.launch {
            while (isActive) {
                val activeList = downloadDao.getActiveDownloads().firstOrNull() ?: emptyList()
                if (activeList.isEmpty()) {
                    delay(3000)
                    continue
                }

                for (item in activeList) {
                    if (item.downloadManagerId <= 0) continue

                    try {
                        val query = DownloadManager.Query().setFilterById(item.downloadManagerId)
                        val cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalBytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                            val bytesDownloaded = if (bytesDownloadedIdx != -1) cursor.getLong(bytesDownloadedIdx) else 0L
                            val totalBytes = if (totalBytesIdx != -1) cursor.getLong(totalBytesIdx) else item.totalBytes
                            val dmStatus = if (statusIdx != -1) cursor.getInt(statusIdx) else -1
                            val localUri = if (localUriIdx != -1) cursor.getString(localUriIdx) else null

                            val progress = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                item.progress
                            }

                             when (dmStatus) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    processDownloadCompletion(item, item.downloadManagerId)
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reasonCode = if (reasonIdx != -1) cursor.getInt(reasonIdx) else -1
                                    val reasonName = mapReasonCodeToName(reasonCode)
                                    val localUriStr = if (localUriIdx != -1) cursor.getString(localUriIdx) else null
                                    
                                    com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                                        current.copy(
                                            downloadManagerStatus = "STATUS_FAILED",
                                            downloadManagerReasonCode = reasonCode.toString(),
                                            downloadManagerReasonName = reasonName,
                                            localUriPresent = if (!localUriStr.isNullOrBlank()) "YES" else "NO",
                                            downloadedBytes = bytesDownloaded.toString(),
                                            totalExpectedBytes = totalBytes.toString(),
                                            failureStage = "DownloadManager Failure Detected",
                                            finalDownloadError = "DownloadManager failed with reason: $reasonName ($reasonCode)",
                                            applicationStatus = "FAILED",
                                            applicationStatusSetBy = "startProgressTrackingLoop (dmStatus == STATUS_FAILED branch)",
                                            applicationStatusSetAt = System.currentTimeMillis().toString(),
                                            applicationFailureTrigger = "DownloadManager.STATUS_FAILED in Polling",
                                            applicationFailureReason = reasonName
                                        )
                                    }
                                    downloadDao.updateDownloadStatus(item.id, "FAILED", progress)
                                }
                                DownloadManager.STATUS_PAUSED -> {
                                    com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                                        current.copy(
                                            downloadManagerStatus = "STATUS_PAUSED",
                                            downloadedBytes = bytesDownloaded.toString(),
                                            totalExpectedBytes = totalBytes.toString()
                                        )
                                    }
                                    downloadDao.updateDownloadStatus(item.id, "PAUSED", progress)
                                }
                                DownloadManager.STATUS_RUNNING -> {
                                    val elapsed = (System.currentTimeMillis() - item.timestamp) / 1000
                                    val isStuck = bytesDownloaded == 0L && elapsed > 30
                                    
                                    com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                                        current.copy(
                                            downloadManagerStatus = "STATUS_RUNNING",
                                            localUriPresent = if (!localUri.isNullOrBlank()) "YES" else "NO",
                                            downloadedBytes = bytesDownloaded.toString(),
                                            totalExpectedBytes = totalBytes.toString(),
                                            failureStage = "Downloading",
                                            elapsedSinceEnqueueSeconds = elapsed.toString(),
                                            stuckAtZeroBytes = if (isStuck) "YES" else "NO"
                                        )
                                    }
                                    downloadDao.updateDownloadProgress(
                                        id = item.id,
                                        status = "DOWNLOADING",
                                        progress = progress,
                                        downloadedBytes = bytesDownloaded,
                                        totalBytes = if (totalBytes > 0) totalBytes else item.totalBytes
                                    )
                                }
                                DownloadManager.STATUS_PENDING -> {
                                    val elapsed = (System.currentTimeMillis() - item.timestamp) / 1000
                                    com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                                        current.copy(
                                            downloadManagerStatus = "STATUS_PENDING",
                                            downloadedBytes = bytesDownloaded.toString(),
                                            totalExpectedBytes = totalBytes.toString(),
                                            failureStage = "Queued",
                                            elapsedSinceEnqueueSeconds = elapsed.toString()
                                        )
                                    }
                                    downloadDao.updateDownloadProgress(
                                        id = item.id,
                                        status = "QUEUED",
                                        progress = 0,
                                        downloadedBytes = bytesDownloaded,
                                        totalBytes = totalBytes
                                    )
                                }
                            }
                        } else {
                            // Row missing from DownloadManager
                            val elapsed = (System.currentTimeMillis() - item.timestamp) / 1000
                            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                                current.copy(
                                    downloadManagerRowMissing = "YES",
                                    downloadManagerQueryMatchedRow = "NO",
                                    failureStage = "Polling",
                                    elapsedSinceEnqueueSeconds = elapsed.toString()
                                )
                            }
                        }
                        cursor?.close()
                    } catch (_: Exception) {}
                }
                delay(800)
            }
        }
    }

    private suspend fun processDownloadCompletion(item: DownloadEntity, dmId: Long) {
        if (item.status == "COMPLETED" || item.status == "FAILED") return

        val query = DownloadManager.Query().setFilterById(dmId)
        val cursor = try { downloadManager.query(query) } catch (_: Throwable) { null }

        var localUri: String? = null
        var dmStatus = -1

        if (cursor != null && cursor.moveToFirst()) {
            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (localUriIdx != -1) localUri = cursor.getString(localUriIdx)
            if (statusIdx != -1) dmStatus = cursor.getInt(statusIdx)
            cursor.close()
        }

        if (dmStatus == DownloadManager.STATUS_SUCCESSFUL || (dmStatus == -1 && localUri != null)) {
            val resolvedDest = if (!localUri.isNullOrBlank()) {
                try {
                    Uri.parse(localUri).path ?: item.destinationUri
                } catch (_: Exception) {
                    item.destinationUri
                }
            } else {
                item.destinationUri
            }

            val file = if (resolvedDest.isNotBlank()) File(resolvedDest) else null
            if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                    current.copy(
                        failureStage = "File Validation",
                        finalDownloadError = "File is missing, empty, or not a valid file.",
                        applicationStatus = "FAILED",
                        applicationStatusSetBy = "processDownloadCompletion (File Missing branch)",
                        applicationStatusSetAt = System.currentTimeMillis().toString(),
                        applicationFailureTrigger = "File Validation Failure",
                        applicationFailureReason = if (file == null) "null File object" else if (!file.exists()) "file does not exist" else if (!file.isFile) "not a file" else "file length <= 0"
                    )
                }
                downloadDao.updateDownloadStatus(item.id, "FAILED", 0)
                return
            }

            if (!validateDownloadedFile(file)) {
                com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                    current.copy(
                        failureStage = "File Validation",
                        finalDownloadError = "Downloaded file contains HTML error or access denied text instead of media bytes.",
                        applicationStatus = "FAILED",
                        applicationStatusSetBy = "processDownloadCompletion (Content Validation branch)",
                        applicationStatusSetAt = System.currentTimeMillis().toString(),
                        applicationFailureTrigger = "Content Validation Failure",
                        applicationFailureReason = "HTML or error text detected in binary"
                    )
                }
                downloadDao.updateDownloadStatus(item.id, "FAILED", 0)
                try { file.delete() } catch (_: Exception) {}
                return
            }

            downloadDao.markCompleted(
                id = item.id,
                destinationUri = resolvedDest,
                completedTime = System.currentTimeMillis()
            )
            scanMediaFile(resolvedDest)
        } else if (dmStatus == DownloadManager.STATUS_FAILED) {
            val query = DownloadManager.Query().setFilterById(dmId)
            val cursor = try { downloadManager.query(query) } catch (_: Throwable) { null }
            var reasonCode = -1
            var reasonName = "N/A"
            if (cursor != null && cursor.moveToFirst()) {
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                if (reasonIdx != -1) {
                    reasonCode = cursor.getInt(reasonIdx)
                    reasonName = mapReasonCodeToName(reasonCode)
                }
                cursor.close()
            }
            
            com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(item.id) { current ->
                current.copy(
                    downloadManagerStatus = "STATUS_FAILED",
                    downloadManagerReasonCode = reasonCode.toString(),
                    downloadManagerReasonName = reasonName,
                    applicationStatus = "FAILED",
                    applicationStatusSetBy = "processDownloadCompletion (dmStatus == STATUS_FAILED branch)",
                    applicationStatusSetAt = System.currentTimeMillis().toString(),
                    applicationFailureTrigger = "DownloadManager.STATUS_FAILED in Broadcast/Query",
                    applicationFailureReason = reasonName
                )
            }
            downloadDao.updateDownloadStatus(item.id, "FAILED", item.progress)
        }
    }

    private fun validateDownloadedFile(file: File): Boolean {
        try {
            if (file.length() < 10L) return false
            val sampleSize = minOf(file.length(), 2048L).toInt()
            val bytes = ByteArray(sampleSize)
            file.inputStream().use { it.read(bytes) }
            val sampleText = String(bytes, Charsets.UTF_8).lowercase().trim()

            if (sampleText.startsWith("<!doctype") || sampleText.startsWith("<html") ||
                sampleText.contains("<title>access denied</title>") ||
                sampleText.contains("<title>403 forbidden</title>") ||
                sampleText.contains("error code 403") ||
                (sampleText.startsWith("{") && sampleText.contains("error"))) {
                return false
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    private fun scanMediaFile(path: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null
            ) { _, _ -> }
        } catch (_: Exception) {}
    }

    private fun registerDownloadCompleteReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val dmId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (dmId > 0) {
                        coroutineScope.launch {
                            val entity = downloadDao.getDownloadByManagerId(dmId)
                            if (entity != null) {
                                processDownloadCompletion(entity, dmId)
                            }
                        }
                    }
                }
            }
        }

        try {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {}
    }

    suspend fun getFreshDownloadDiagnostics(id: String): DownloadDiagnostics? = withContext(Dispatchers.IO) {
        val entity = downloadDao.getDownloadById(id) ?: return@withContext null
        val existing = com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.getDiagnostics(id) ?: DownloadDiagnostics(downloadId = id)
        
        var currentStatus = "N/A"
        var reasonCode = "N/A"
        var reasonName = "N/A"
        var rowCount = 0
        var matchedRow = "NO"
        var rowMissing = "YES"
        var bytesDownloaded = 0L
        var totalBytes = entity.totalBytes
        var localUri: String? = null

        if (entity.downloadManagerId > 0) {
            try {
                val query = DownloadManager.Query().setFilterById(entity.downloadManagerId)
                val cursor = downloadManager.query(query)
                if (cursor != null) {
                    rowCount = cursor.count
                    if (cursor.moveToFirst()) {
                        matchedRow = "YES"
                        rowMissing = "NO"
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                        if (statusIdx != -1) {
                            val status = cursor.getInt(statusIdx)
                            currentStatus = when (status) {
                                DownloadManager.STATUS_PENDING -> "STATUS_PENDING"
                                DownloadManager.STATUS_RUNNING -> "STATUS_RUNNING"
                                DownloadManager.STATUS_PAUSED -> "STATUS_PAUSED"
                                DownloadManager.STATUS_SUCCESSFUL -> "STATUS_SUCCESSFUL"
                                DownloadManager.STATUS_FAILED -> "STATUS_FAILED"
                                else -> "UNKNOWN ($status)"
                            }
                        }
                        if (reasonIdx != -1) {
                            val code = cursor.getInt(reasonIdx)
                            reasonCode = code.toString()
                            reasonName = mapReasonCodeToName(code)
                        }
                        if (bytesIdx != -1) bytesDownloaded = cursor.getLong(bytesIdx)
                        if (totalIdx != -1) totalBytes = cursor.getLong(totalIdx)
                        if (uriIdx != -1) localUri = cursor.getString(uriIdx)
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}
        }

        val elapsed = (System.currentTimeMillis() - entity.timestamp) / 1000
        val isStuck = (currentStatus == "STATUS_RUNNING" || currentStatus == "STATUS_PENDING") && 
                      bytesDownloaded == 0L && elapsed > 30

        val updated = existing.copy(
            downloadManagerStatus = currentStatus,
            downloadManagerReasonCode = reasonCode,
            downloadManagerReasonName = reasonName,
            downloadedBytes = bytesDownloaded.toString(),
            totalExpectedBytes = totalBytes.toString(),
            localUriPresent = if (!localUri.isNullOrBlank()) "YES" else "NO",
            
            // V11 additions
            appDownloadId = entity.id,
            downloadManagerId = entity.downloadManagerId.toString(),
            downloadManagerIdPresent = if (entity.downloadManagerId > 0) "YES" else "NO",
            downloadManagerIdPersisted = "YES",
            downloadManagerIdAssociatedWithCorrectAppItem = "YES",
            downloadManagerQueriedId = entity.downloadManagerId.toString(),
            downloadManagerQueryMatchedRow = matchedRow,
            downloadManagerQueryRowCount = rowCount.toString(),
            downloadManagerRowMissing = rowMissing,
            downloadManagerFreshQueryPerformed = "YES",
            downloadManagerCurrentStatus = currentStatus,
            diagnosticStatusTimestamp = System.currentTimeMillis().toString(),
            applicationStatus = entity.status,
            transferEngine = entity.transferEngine,
            elapsedSinceEnqueueSeconds = elapsed.toString(),
            stuckAtZeroBytes = if (isStuck) "YES" else "NO"
        )
        
        com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.updateDiagnostics(id) { updated }
        updated
    }

    private fun mapReasonCodeToName(code: Int): String {
        return when (code) {
            DownloadManager.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ERROR_INSUFFICIENT_SPACE"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "ERROR_DEVICE_NOT_FOUND"
            DownloadManager.ERROR_CANNOT_RESUME -> "ERROR_CANNOT_RESUME"
            in 400..499 -> "HTTP ${code} Client Error"
            in 500..599 -> "HTTP ${code} Server Error"
            else -> "UNKNOWN ($code)"
        }
    }
}
