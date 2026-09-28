package com.example.core.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
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
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

interface IVideoStudioEngine {
    suspend fun muteVideo(
        sourceUri: Uri,
        onProgress: (Int) -> Unit
    ): VideoStudioResult

    suspend fun rotateVideo(
        sourceUri: Uri,
        angle: VideoRotationAngle,
        onProgress: (Int) -> Unit
    ): VideoStudioResult

    suspend fun cropVideo(
        sourceUri: Uri,
        preset: VideoCropPreset,
        customLeft: Float = -1f,
        customRight: Float = 1f,
        customBottom: Float = -1f,
        customTop: Float = 1f,
        onProgress: (Int) -> Unit
    ): VideoStudioResult

    suspend fun changeSpeed(
        sourceUri: Uri,
        speedPreset: VideoSpeedPreset,
        onProgress: (Int) -> Unit
    ): VideoStudioResult

    suspend fun extractFrame(
        sourceUri: Uri,
        timestampMs: Long,
        format: FrameImageFormat,
        quality: Int = 95
    ): FrameExtractResult

    fun cancel()
}

@OptIn(UnstableApi::class)
class VideoStudioEngine(
    private val context: Context,
    private val publisher: IVideoOutputPublisher = VideoOutputPublisher(context)
) : IVideoStudioEngine {

    private val activeTransformer = AtomicReference<Transformer?>()

    private sealed interface ExportSignal {
        data class Completed(val exportResult: ExportResult) : ExportSignal
        data class Error(val exception: ExportException) : ExportSignal
    }

    override fun cancel() {
        activeTransformer.get()?.cancel()
        activeTransformer.set(null)
    }

    override suspend fun muteVideo(
        sourceUri: Uri,
        onProgress: (Int) -> Unit
    ): VideoStudioResult = withContext(Dispatchers.IO) {
        val metadata = VideoMetadata.extract(context, sourceUri)
        val tempDir = File(context.cacheDir, "studio_muted").apply { mkdirs() }
        val tempFile = File(tempDir, "muted_${UUID.randomUUID().toString().take(8)}.mp4")

        onProgress(10)

        // Lossless remux approach: Extract only video track with MediaExtractor + MediaMuxer
        val losslessSuccess = try {
            remuxMuteLossless(sourceUri, tempFile, onProgress)
        } catch (e: Exception) {
            false
        }

        if (losslessSuccess && tempFile.exists() && tempFile.length() > 0) {
            onProgress(90)
            return@withContext publishExportedVideo(tempFile, "muted_${metadata.displayName}", metadata.durationMs)
        }

        // Fallback: Media3 Transformer with setRemoveAudio(true)
        if (tempFile.exists()) tempFile.delete()
        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .build()
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(listOf(editedMediaItem)))).build()

        val exportResult = runTransformerExport(composition, tempFile, onProgress)
        if (exportResult is VideoStudioResult.Success) {
            return@withContext publishExportedVideo(tempFile, "muted_${metadata.displayName}", metadata.durationMs)
        }
        exportResult
    }

    private fun remuxMuteLossless(sourceUri: Uri, outputFile: File, onProgress: (Int) -> Unit): Boolean {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return false
        try {
            extractor.setDataSource(pfd.fileDescriptor)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                return false
            }

            val retriever = MediaMetadataRetriever()
            var rotation = 0
            try {
                retriever.setDataSource(context, sourceUri)
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotStr?.toIntOrNull() ?: 0
            } catch (_: Exception) {} finally {
                try { retriever.release() } catch (_: Exception) {}
            }

            extractor.selectTrack(videoTrackIndex)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }
            val muxerTrackIndex = muxer.addTrack(videoFormat)
            muxer.start()

            val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize.coerceAtLeast(64 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            var samplesRead = 0
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                samplesRead++
                if (samplesRead % 50 == 0) {
                    onProgress(20 + (samplesRead % 60))
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            return true
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    override suspend fun rotateVideo(
        sourceUri: Uri,
        angle: VideoRotationAngle,
        onProgress: (Int) -> Unit
    ): VideoStudioResult = withContext(Dispatchers.IO) {
        val metadata = VideoMetadata.extract(context, sourceUri)
        val tempDir = File(context.cacheDir, "studio_rotated").apply { mkdirs() }
        val tempFile = File(tempDir, "rotated_${UUID.randomUUID().toString().take(8)}.mp4")

        onProgress(10)

        // Real frame rotation with Media3 Transformer ScaleAndRotateTransformation
        val rotateEffect = ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(angle.degrees.toFloat())
            .build()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(), listOf(rotateEffect)))
            .build()
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(listOf(editedMediaItem)))).build()

        val exportResult = runTransformerExport(composition, tempFile, onProgress)
        if (exportResult is VideoStudioResult.Success) {
            return@withContext publishExportedVideo(
                tempFile,
                "rotated_${angle.degrees}_${metadata.displayName}",
                metadata.durationMs
            )
        }
        exportResult
    }

    override suspend fun cropVideo(
        sourceUri: Uri,
        preset: VideoCropPreset,
        customLeft: Float,
        customRight: Float,
        customBottom: Float,
        customTop: Float,
        onProgress: (Int) -> Unit
    ): VideoStudioResult = withContext(Dispatchers.IO) {
        val metadata = VideoMetadata.extract(context, sourceUri)
        val tempDir = File(context.cacheDir, "studio_cropped").apply { mkdirs() }
        val tempFile = File(tempDir, "cropped_${UUID.randomUUID().toString().take(8)}.mp4")

        onProgress(10)

        val srcW = if (metadata.width > 0) metadata.width.toFloat() else 1920f
        val srcH = if (metadata.height > 0) metadata.height.toFloat() else 1080f
        val srcRatio = srcW / srcH

        val (left, right, bottom, top) = when (preset) {
            VideoCropPreset.ORIGINAL -> {
                listOf(-1f, 1f, -1f, 1f)
            }
            VideoCropPreset.CUSTOM -> {
                listOf(
                    customLeft.coerceIn(-1f, 1f),
                    customRight.coerceIn(-1f, 1f),
                    customBottom.coerceIn(-1f, 1f),
                    customTop.coerceIn(-1f, 1f)
                )
            }
            else -> {
                val targetRatio = preset.aspectRatio
                calculateNormalizedCropBounds(srcRatio, targetRatio)
            }
        }

        val cropEffect: Effect = Crop(left, right, bottom, top)
        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(), listOf(cropEffect)))
            .build()
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(listOf(editedMediaItem)))).build()

        val exportResult = runTransformerExport(composition, tempFile, onProgress)
        if (exportResult is VideoStudioResult.Success) {
            return@withContext publishExportedVideo(
                tempFile,
                "cropped_${preset.name.lowercase()}_${metadata.displayName}",
                metadata.durationMs
            )
        }
        exportResult
    }

    override suspend fun changeSpeed(
        sourceUri: Uri,
        speedPreset: VideoSpeedPreset,
        onProgress: (Int) -> Unit
    ): VideoStudioResult = withContext(Dispatchers.IO) {
        val metadata = VideoMetadata.extract(context, sourceUri)
        val tempDir = File(context.cacheDir, "studio_speed").apply { mkdirs() }
        val tempFile = File(tempDir, "speed_${UUID.randomUUID().toString().take(8)}.mp4")

        onProgress(10)

        val speedFactor = speedPreset.multiplier
        val expectedNewDurationMs = (metadata.durationMs / speedFactor).toLong()

        // Real speed adjustment via MediaExtractor + MediaMuxer timestamp recalculation
        val remuxSuccess = try {
            remuxSpeedAdjusted(sourceUri, tempFile, speedFactor, onProgress)
        } catch (e: Exception) {
            false
        }

        if (remuxSuccess && tempFile.exists() && tempFile.length() > 0) {
            onProgress(90)
            return@withContext publishExportedVideo(
                tempFile,
                "speed_${(speedFactor * 100).toInt()}pct_${metadata.displayName}",
                expectedNewDurationMs
            )
        }

        VideoStudioResult.Failure("Failed to modify video playback speed.")
    }

    private fun remuxSpeedAdjusted(
        sourceUri: Uri,
        outputFile: File,
        speedFactor: Float,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return false
        try {
            extractor.setDataSource(pfd.fileDescriptor)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) return false

            val retriever = MediaMetadataRetriever()
            var rotation = 0
            try {
                retriever.setDataSource(context, sourceUri)
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotStr?.toIntOrNull() ?: 0
            } catch (_: Exception) {} finally {
                try { retriever.release() } catch (_: Exception) {}
            }

            extractor.selectTrack(videoTrackIndex)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }
            val muxerTrackIndex = muxer.addTrack(videoFormat)
            muxer.start()

            val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize.coerceAtLeast(64 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            var samplesCount = 0
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val originalPts = extractor.sampleTime
                bufferInfo.presentationTimeUs = (originalPts / speedFactor).toLong()
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                samplesCount++
                if (samplesCount % 60 == 0) {
                    onProgress(20 + (samplesCount % 60))
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            return true
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    override suspend fun extractFrame(
        sourceUri: Uri,
        timestampMs: Long,
        format: FrameImageFormat,
        quality: Int
    ): FrameExtractResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sourceUri)
            val timeUs = timestampMs * 1000L
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 0, 0)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }

            if (bitmap == null) {
                return@withContext FrameExtractResult.Failure("Could not extract frame at ${timestampMs}ms")
            }

            val displayName = "frame_${timestampMs}ms_${System.currentTimeMillis()}${format.extension}"
            val publishResult = publishFrameToPictures(bitmap, displayName, format, quality)
            if (publishResult is FrameExtractResult.Success) {
                return@withContext publishResult
            }
            FrameExtractResult.Failure("Failed to save extracted frame to MediaStore")
        } catch (e: Exception) {
            FrameExtractResult.Failure("Frame extraction failed: ${e.message}", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun publishFrameToPictures(
        bitmap: Bitmap,
        displayName: String,
        format: FrameImageFormat,
        quality: Int
    ): FrameExtractResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelRox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            return FrameExtractResult.Failure("Failed to create MediaStore entry: ${e.message}", e)
        } ?: return FrameExtractResult.Failure("MediaStore returned null URI")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                val compressFormat = if (format == FrameImageFormat.PNG) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(compressFormat, quality.coerceIn(10, 100), out)
                out.flush()
            } ?: return FrameExtractResult.Failure("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return FrameExtractResult.Success(
                outputUri = uri,
                displayName = displayName,
                width = bitmap.width,
                height = bitmap.height,
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return FrameExtractResult.Failure("Failed writing image bitmap: ${e.message}", e)
        }
    }

    private suspend fun runTransformerExport(
        composition: Composition,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): VideoStudioResult = withContext(Dispatchers.Main.immediate) {
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()
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
            return@withContext VideoStudioResult.Failure("Failed to initiate export: ${e.localizedMessage ?: "Unknown error"}", e)
        }

        // Progress polling loop
        val progressHolder = ProgressHolder()
        while (signalDeferred.isActive) {
            try {
                val progressState = transformer.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 100))
                }
            } catch (_: Exception) {}
            delay(100)
        }

        transformer.removeListener(listener)

        when (val signal = signalDeferred.await()) {
            is ExportSignal.Completed -> {
                VideoStudioResult.Success(
                    outputUri = Uri.fromFile(outputFile),
                    displayName = outputFile.name,
                    fileSizeBytes = outputFile.length(),
                    durationMs = signal.exportResult.durationMs
                )
            }
            is ExportSignal.Error -> {
                VideoStudioResult.Failure(
                    signal.exception.localizedMessage ?: "Transformer failed with error code: ${signal.exception.errorCode}",
                    signal.exception
                )
            }
        }
    }

    private suspend fun publishExportedVideo(
        tempFile: File,
        displayName: String,
        durationMs: Long
    ): VideoStudioResult = withContext(Dispatchers.IO) {
        val publishResult = publisher.publishVideo(tempFile, displayName)
        if (tempFile.exists()) tempFile.delete()

        when (publishResult) {
            is VideoPublishResult.Success -> {
                VideoStudioResult.Success(
                    outputUri = publishResult.contentUri,
                    displayName = publishResult.displayName,
                    fileSizeBytes = publishResult.fileSizeBytes,
                    durationMs = durationMs
                )
            }
            is VideoPublishResult.Failure -> {
                VideoStudioResult.Failure(publishResult.errorMessage, publishResult.throwable)
            }
        }
    }

    companion object {
        fun calculateNormalizedCropBounds(srcRatio: Float, targetRatio: Float): List<Float> {
            if (targetRatio <= 0f || srcRatio <= 0f) {
                return listOf(-1f, 1f, -1f, 1f)
            }
            return if (srcRatio > targetRatio) {
                // Video is wider than target ratio -> crop left & right
                val r = (targetRatio / srcRatio).coerceIn(0.01f, 1f)
                listOf(-r, r, -1f, 1f)
            } else if (srcRatio < targetRatio) {
                // Video is taller than target ratio -> crop top & bottom
                val r = (srcRatio / targetRatio).coerceIn(0.01f, 1f)
                listOf(-1f, 1f, -r, r)
            } else {
                listOf(-1f, 1f, -1f, 1f)
            }
        }
    }
}
