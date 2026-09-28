package com.example.core.trimmer

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class VideoTrimmerEngine(private val context: Context) : IVideoTrimmerEngine {

    private val activeTransformer = AtomicReference<Transformer?>(null)

    private sealed interface ExportSignal {
        data class Completed(val exportResult: ExportResult) : ExportSignal
        data class Error(val exception: ExportException) : ExportSignal
    }

    override suspend fun trimVideo(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        onProgress: (Int) -> Unit
    ): VideoTrimmerResult = withContext(Dispatchers.Main.immediate) {
        val tempDir = File(context.cacheDir, "trimmed_videos").apply { mkdirs() }
        val tempFile = File(tempDir, "trim_${UUID.randomUUID().toString().take(8)}.mp4")

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .setStartsAtKeyFrame(false)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        val composition = Composition.Builder(listOf(androidx.media3.transformer.EditedMediaItemSequence(listOf(editedMediaItem)))).build()

        // Attempt 1: Transmux / Passthrough
        val transmuxResult = runExport(composition, tempFile, transmux = true, onProgress = onProgress)
        if (transmuxResult is VideoTrimmerResult.Success) {
            return@withContext transmuxResult
        } else if (transmuxResult is VideoTrimmerResult.Cancelled) {
            return@withContext transmuxResult
        }

        // Attempt 2: Re-encode fallback if transmuxing fails
        if (tempFile.exists()) tempFile.delete()
        runExport(composition, tempFile, transmux = false, onProgress = onProgress)
    }

    private suspend fun runExport(
        composition: Composition,
        outputFile: File,
        transmux: Boolean,
        onProgress: (Int) -> Unit
    ): VideoTrimmerResult = withContext(Dispatchers.Main.immediate) {
        val builder = Transformer.Builder(context)
        if (transmux) {
            builder.setVideoMimeType(MimeTypes.VIDEO_H264)
        }

        val transformer = builder.build()
        activeTransformer.set(transformer)

        val signalDeferred = CompletableDeferred<ExportSignal>()
        val listener = object : Transformer.Listener {
            override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                activeTransformer.set(null)
                signalDeferred.complete(ExportSignal.Completed(exportResult))
            }

            override fun onError(comp: Composition, exportResult: ExportResult, exception: ExportException) {
                activeTransformer.set(null)
                signalDeferred.complete(ExportSignal.Error(exception))
            }
        }

        transformer.addListener(listener)

        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            activeTransformer.set(null)
            transformer.removeListener(listener)
            return@withContext VideoTrimmerResult.Failure("Failed to start trimming process", e)
        }

        val progressHolder = ProgressHolder()
        while (!signalDeferred.isCompleted) {
            if (!isActive) {
                transformer.cancel()
                activeTransformer.set(null)
                transformer.removeListener(listener)
                if (outputFile.exists()) outputFile.delete()
                return@withContext VideoTrimmerResult.Cancelled
            }

            val state = transformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(progressHolder.progress)
            }
            delay(100)
        }

        transformer.removeListener(listener)

        when (val signal = signalDeferred.await()) {
            is ExportSignal.Completed -> {
                if (outputFile.exists() && outputFile.length() > 0) {
                    VideoTrimmerResult.Success(
                        tempFile = outputFile,
                        durationMs = signal.exportResult.durationMs,
                        fileSizeBytes = outputFile.length(),
                        wasTransmuxed = transmux
                    )
                } else {
                    VideoTrimmerResult.Failure("Export finished but output file is empty")
                }
            }
            is ExportSignal.Error -> {
                VideoTrimmerResult.Failure(
                    message = signal.exception.localizedMessage ?: "Export error occurred",
                    cause = signal.exception
                )
            }
        }
    }

    override fun cancel() {
        activeTransformer.getAndSet(null)?.cancel()
    }
}
