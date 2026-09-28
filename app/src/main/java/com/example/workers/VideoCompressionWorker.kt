package com.example.workers

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.core.notifications.NotificationChannels
import com.example.core.video.IVideoCompressorEngine
import com.example.core.video.IVideoOutputPublisher
import com.example.core.video.VideoCompressionPreset
import com.example.core.video.VideoCompressionResult
import com.example.core.video.VideoCompressorEngine
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoOutputPublisher
import com.example.core.video.VideoPublishResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class VideoCompressionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "video_compression_unique_work"
        const val NOTIFICATION_ID = 4001
        const val COMPLETION_NOTIFICATION_ID = 4002

        // Input keys
        const val KEY_SOURCE_URI = "key_source_uri"
        const val KEY_PRESET_NAME = "key_preset_name"
        const val KEY_DISPLAY_NAME = "key_display_name"
        const val KEY_SOURCE_SIZE = "key_source_size"
        const val KEY_DURATION_MS = "key_duration_ms"
        const val KEY_WIDTH = "key_width"
        const val KEY_HEIGHT = "key_height"
        const val KEY_BITRATE = "key_bitrate"
        const val KEY_MIME_TYPE = "key_mime_type"

        // Output / Progress keys
        const val KEY_PROGRESS_PERCENT = "key_progress_percent"
        const val KEY_STAGE = "key_stage" // "compressing", "publishing", "success", "error"
        const val KEY_RESULT_URI = "key_result_uri"
        const val KEY_RESULT_DISPLAY_NAME = "key_result_display_name"
        const val KEY_ORIGINAL_SIZE_BYTES = "key_original_size_bytes"
        const val KEY_COMPRESSED_SIZE_BYTES = "key_compressed_size_bytes"
        const val KEY_BYTES_SAVED = "key_bytes_saved"
        const val KEY_PERCENTAGE_SAVED = "key_percentage_saved"
        const val KEY_OUTPUT_WIDTH = "key_output_width"
        const val KEY_OUTPUT_HEIGHT = "key_output_height"
        const val KEY_ERROR_MESSAGE = "key_error_message"

        const val STAGE_COMPRESSING = "compressing"
        const val STAGE_PUBLISHING = "publishing"
    }

    private val compressorEngine: IVideoCompressorEngine = VideoCompressorEngine(applicationContext)
    private val outputPublisher: IVideoOutputPublisher = VideoOutputPublisher(applicationContext)
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastReportedProgress: Int = -1
    private var lastNotificationUpdateTime: Long = 0L

    override suspend fun doWork(): Result {
        val sourceUriStr = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to "Missing source video URI")
        )
        val sourceUri = Uri.parse(sourceUriStr)
        val presetName = inputData.getString(KEY_PRESET_NAME) ?: VideoCompressionPreset.MEDIUM.name
        val preset = try {
            VideoCompressionPreset.valueOf(presetName)
        } catch (_: Exception) {
            VideoCompressionPreset.MEDIUM
        }

        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "video.mp4"
        val sourceSizeBytes = inputData.getLong(KEY_SOURCE_SIZE, 0L)
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val srcWidth = inputData.getInt(KEY_WIDTH, 0)
        val srcHeight = inputData.getInt(KEY_HEIGHT, 0)
        val bitrate = inputData.getLong(KEY_BITRATE, 0L)
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "video/mp4"

        // Initialize foreground notification immediately
        try {
            setForeground(createForegroundInfo(0, displayName, isIndeterminate = true))
        } catch (_: Exception) {
            // Foreground info set might fail if worker was already stopped
        }

        setProgress(
            workDataOf(
                KEY_STAGE to STAGE_COMPRESSING,
                KEY_PROGRESS_PERCENT to 0,
                KEY_DISPLAY_NAME to displayName
            )
        )

        var tempOutputFile: File? = null

        try {
            // Execute compression using existing VideoCompressorEngine
            val compressionResult = compressorEngine.compressVideo(sourceUri, preset) { progress ->
                if (isStopped) return@compressVideo

                val now = System.currentTimeMillis()
                val shouldUpdate = progress != lastReportedProgress &&
                        (progress == 0 || progress == 100 || (progress - lastReportedProgress >= 2) || (now - lastNotificationUpdateTime >= 500))

                if (shouldUpdate) {
                    lastReportedProgress = progress
                    lastNotificationUpdateTime = now

                    // Update WorkManager progress for UI observer
                    kotlinx.coroutines.runBlocking {
                        try {
                            setProgress(
                                workDataOf(
                                    KEY_STAGE to STAGE_COMPRESSING,
                                    KEY_PROGRESS_PERCENT to progress,
                                    KEY_DISPLAY_NAME to displayName
                                )
                            )
                        } catch (_: Exception) {}
                    }

                    // Update persistent foreground notification
                    try {
                        val notification = createNotification(progress, displayName, isIndeterminate = false)
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {}
                }
            }

            if (isStopped) {
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Compression was cancelled"))
            }

            return when (compressionResult) {
                is VideoCompressionResult.Success -> {
                    tempOutputFile = compressionResult.outputFile

                    // Stage: Publishing
                    try {
                        setProgress(
                            workDataOf(
                                KEY_STAGE to STAGE_PUBLISHING,
                                KEY_PROGRESS_PERCENT to 100,
                                KEY_DISPLAY_NAME to displayName,
                                KEY_COMPRESSED_SIZE_BYTES to compressionResult.outputSizeBytes
                            )
                        )
                        val publishingNotification = createPublishingNotification(displayName)
                        notificationManager.notify(NOTIFICATION_ID, publishingNotification)
                    } catch (_: Exception) {}

                    val publishResult = outputPublisher.publishVideo(
                        sourceFile = compressionResult.outputFile,
                        desiredDisplayName = displayName,
                        mimeType = mimeType
                    )

                    // Clean up temp file
                    withContext(Dispatchers.IO) {
                        try {
                            tempOutputFile?.delete()
                        } catch (_: Exception) {}
                    }

                    when (publishResult) {
                        is VideoPublishResult.Success -> {
                            val originalSize = if (sourceSizeBytes > 0) sourceSizeBytes else compressionResult.outputSizeBytes
                            val compressedSize = publishResult.fileSizeBytes
                            val bytesSaved = maxOf(originalSize - compressedSize, 0L)
                            val percentageSaved = if (originalSize > 0) {
                                (bytesSaved.toFloat() / originalSize.toFloat()) * 100f
                            } else {
                                0f
                            }

                            val outW = compressionResult.outputMetadata.width
                            val outH = compressionResult.outputMetadata.height

                            // Post completion notification
                            showCompletionNotification(
                                displayName = publishResult.displayName,
                                compressedSize = compressedSize,
                                percentageSaved = percentageSaved,
                                isSuccess = true,
                                errorMessage = null
                            )

                            Result.success(
                                workDataOf(
                                    KEY_RESULT_URI to publishResult.contentUri.toString(),
                                    KEY_RESULT_DISPLAY_NAME to publishResult.displayName,
                                    KEY_ORIGINAL_SIZE_BYTES to originalSize,
                                    KEY_COMPRESSED_SIZE_BYTES to compressedSize,
                                    KEY_BYTES_SAVED to bytesSaved,
                                    KEY_PERCENTAGE_SAVED to percentageSaved.toDouble(),
                                    KEY_MIME_TYPE to publishResult.mimeType,
                                    KEY_OUTPUT_WIDTH to outW,
                                    KEY_OUTPUT_HEIGHT to outH,
                                    KEY_SOURCE_URI to sourceUriStr
                                )
                            )
                        }

                        is VideoPublishResult.Failure -> {
                            showCompletionNotification(
                                displayName = displayName,
                                compressedSize = 0L,
                                percentageSaved = 0f,
                                isSuccess = false,
                                errorMessage = publishResult.errorMessage
                            )
                            Result.failure(
                                workDataOf(
                                    KEY_ERROR_MESSAGE to "Failed to save compressed video: ${publishResult.errorMessage}",
                                    KEY_SOURCE_URI to sourceUriStr
                                )
                            )
                        }
                    }
                }

                is VideoCompressionResult.Failure -> {
                    withContext(Dispatchers.IO) {
                        try {
                            tempOutputFile?.delete()
                        } catch (_: Exception) {}
                    }
                    showCompletionNotification(
                        displayName = displayName,
                        compressedSize = 0L,
                        percentageSaved = 0f,
                        isSuccess = false,
                        errorMessage = compressionResult.errorMessage
                    )
                    Result.failure(
                        workDataOf(
                            KEY_ERROR_MESSAGE to "Compression failed: ${compressionResult.errorMessage}",
                            KEY_SOURCE_URI to sourceUriStr
                        )
                    )
                }

                is VideoCompressionResult.Cancelled -> {
                    withContext(Dispatchers.IO) {
                        try {
                            tempOutputFile?.delete()
                        } catch (_: Exception) {}
                    }
                    // Cancel notification
                    notificationManager.cancel(NOTIFICATION_ID)
                    Result.failure(
                        workDataOf(
                            KEY_ERROR_MESSAGE to "Compression cancelled",
                            KEY_SOURCE_URI to sourceUriStr
                        )
                    )
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                try {
                    tempOutputFile?.delete()
                } catch (_: Exception) {}
            }
            if (!isStopped) {
                showCompletionNotification(
                    displayName = displayName,
                    compressedSize = 0L,
                    percentageSaved = 0f,
                    isSuccess = false,
                    errorMessage = e.message ?: "Unexpected error"
                )
            }
            return Result.failure(
                workDataOf(
                    KEY_ERROR_MESSAGE to (e.message ?: "Unexpected compression error"),
                    KEY_SOURCE_URI to sourceUriStr
                )
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "video"
        return createForegroundInfo(0, displayName, isIndeterminate = true)
    }

    private fun createForegroundInfo(
        progress: Int,
        displayName: String,
        isIndeterminate: Boolean
    ): ForegroundInfo {
        val notification = createNotification(progress, displayName, isIndeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(
        progress: Int,
        displayName: String,
        isIndeterminate: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = if (isIndeterminate) "Preparing compression…" else "$progress% completed"

        return NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_VIDEO_COMPRESSION)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Compressing video")
            .setContentText("$displayName • $progressText")
            .setSubText(if (!isIndeterminate) "$progress%" else null)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createPublishingNotification(displayName: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_VIDEO_COMPRESSION)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Saving compressed video…")
            .setContentText(displayName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification(
        displayName: String,
        compressedSize: Long,
        percentageSaved: Float,
        isSuccess: Boolean,
        errorMessage: String?
    ) {
        // Dismiss foreground notification
        notificationManager.cancel(NOTIFICATION_ID)

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(
            applicationContext,
            if (isSuccess) NotificationChannels.CHANNEL_VIDEO_COMPRESSION else NotificationChannels.CHANNEL_CRITICAL
        )
            .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (isSuccess) {
            val formattedSize = formatFileSize(compressedSize)
            val savedText = if (percentageSaved > 0) " (${String.format(Locale.US, "%.0f%%", percentageSaved)} saved)" else ""
            builder.setContentTitle("Video compressed successfully")
                .setContentText("$displayName • $formattedSize$savedText")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        } else {
            builder.setContentTitle("Video compression failed")
                .setContentText(errorMessage ?: "An error occurred during compression")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, builder.build())
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
