package com.example.core.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

interface IVideoCompressorEngine {
    suspend fun compressVideo(
        sourceUri: Uri,
        preset: VideoCompressionPreset,
        onProgress: (Int) -> Unit
    ): VideoCompressionResult

    fun cancel()
}

@OptIn(UnstableApi::class)
class VideoCompressorEngine(private val context: Context) : IVideoCompressorEngine {

    private val activeTransformer = AtomicReference<Transformer?>()

    private sealed interface ExportSignal {
        data class Completed(val exportResult: ExportResult) : ExportSignal
        data class Error(val exception: ExportException) : ExportSignal
    }

    /**
     * Compresses a video from a content:// URI using Media3 Transformer 1.3.1.
     * All thread-sensitive Transformer operations (construction, start, progress querying,
     * callbacks, and cancellation) execute strictly on the Main thread/Looper as required by Media3.
     * Heavy transformation/encoding is processed asynchronously by Media3 internal threads.
     * File I/O and metadata operations are handled on Dispatchers.IO.
     */
    override suspend fun compressVideo(
        sourceUri: Uri,
        preset: VideoCompressionPreset,
        onProgress: (Int) -> Unit
    ): VideoCompressionResult {
        // Step 1: Extract source metadata and prepare temporary output file on Dispatchers.IO
        val (sourceMetadata, tempOutputFile) = withContext(Dispatchers.IO) {
            val meta = try {
                VideoMetadata.extract(context, sourceUri)
            } catch (e: Exception) {
                null
            }
            val file = try {
                val f = File(context.cacheDir, "compressed_vid_${System.currentTimeMillis()}.mp4")
                if (f.exists()) f.delete()
                f
            } catch (_: Exception) {
                null
            }
            Pair(meta, file)
        }

        if (tempOutputFile == null) {
            return VideoCompressionResult.Failure("Failed to create temporary output file")
        }

        var isSuccessfulExport = false

        try {
            // Step 2: Configure and execute Transformer strictly on Dispatchers.Main.immediate
            val exportResultSignal = withContext(Dispatchers.Main.immediate) {
                // Calculate target dimensions preserving aspect ratio without upscaling
                val (srcW, srcH) = if (sourceMetadata != null && sourceMetadata.width > 0 && sourceMetadata.height > 0) {
                    Pair(sourceMetadata.width, sourceMetadata.height)
                } else {
                    Pair(1920, 1080)
                }
                val (targetWidth, targetHeight) = preset.calculateTargetDimensions(srcW, srcH)

                // Build Video Effects: Attach Presentation to scale video to target dimensions
                val videoEffects = ArrayList<Effect>()
                val isDownscaling = targetWidth < srcW || targetHeight < srcH
                if (isDownscaling) {
                    val presentation = Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                    videoEffects.add(presentation)
                }

                // Build EditedMediaItem with attached effects
                val mediaItem = MediaItem.fromUri(sourceUri)
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(ImmutableList.of(), ImmutableList.copyOf(videoEffects)))
                    .build()

                // Explicitly disable transmuxing: force video and audio decoding & re-encoding
                val composition = Composition.Builder(EditedMediaItemSequence(editedMediaItem))
                    .setTransmuxVideo(false)
                    .setTransmuxAudio(false)
                    .build()

                // Configure VideoEncoderSettings with target bitrate
                val requestedBitrate = if (sourceMetadata != null && sourceMetadata.bitrate > 0) {
                    minOf(preset.targetVideoBitrate, sourceMetadata.bitrate).toInt()
                } else {
                    preset.targetVideoBitrate.toInt()
                }

                val videoEncoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(requestedBitrate)
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(videoEncoderSettings)
                    .setEnableFallback(true)
                    .build()

                // Set target codecs: H.264 video, AAC audio
                val transformationRequest = TransformationRequest.Builder()
                    .setVideoMimeType(preset.videoMimeType)
                    .setAudioMimeType(preset.audioMimeType)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setTransformationRequest(transformationRequest)
                    .setEncoderFactory(encoderFactory)
                    .build()

                activeTransformer.set(transformer)

                val signalDeferred = CompletableDeferred<ExportSignal>()

                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        signalDeferred.complete(ExportSignal.Completed(exportResult))
                    }

                    override fun onError(
                        comp: Composition,
                        exportResult: ExportResult,
                        exception: ExportException
                    ) {
                        signalDeferred.complete(ExportSignal.Error(exception))
                    }
                }

                transformer.addListener(listener)

                try {
                    transformer.start(composition, tempOutputFile.absolutePath)
                } catch (e: Exception) {
                    activeTransformer.set(null)
                    transformer.removeListener(listener)
                    throw e
                }

                val progressHolder = ProgressHolder()

                // Poll progress strictly on the application Main thread as required by Transformer.getProgress()
                while (!signalDeferred.isCompleted) {
                    delay(150)
                    if (signalDeferred.isCompleted) break

                    if (activeTransformer.get() == null) {
                        // Cancelled externally
                        break
                    }

                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    }
                }

                val result = if (activeTransformer.get() == null && !signalDeferred.isCompleted) {
                    null // Cancelled
                } else {
                    signalDeferred.await()
                }

                transformer.removeListener(listener)
                activeTransformer.set(null)
                result
            }

            // Step 3: Handle result / errors / cancellation
            if (exportResultSignal == null) {
                withContext(Dispatchers.IO) {
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                }
                return VideoCompressionResult.Cancelled
            }

            when (exportResultSignal) {
                is ExportSignal.Error -> {
                    val ex = exportResultSignal.exception
                    withContext(Dispatchers.IO) {
                        if (tempOutputFile.exists()) tempOutputFile.delete()
                    }
                    return VideoCompressionResult.Failure(
                        ex.message ?: "Unknown compression error",
                        ex
                    )
                }
                is ExportSignal.Completed -> {
                    val exportResult = exportResultSignal.exportResult

                    // Export reported completed by Media3. Verify output file on Dispatchers.IO.
                    val (isValid, outputSizeBytes, outputMetadata) = withContext(Dispatchers.IO) {
                        if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                            if (tempOutputFile.exists()) tempOutputFile.delete()
                            Triple(false, 0L, null)
                        } else {
                            val size = tempOutputFile.length()
                            val meta = try {
                                VideoMetadata.extract(context, Uri.fromFile(tempOutputFile))
                            } catch (_: Exception) {
                                VideoMetadata(
                                    contentUri = Uri.fromFile(tempOutputFile).toString(),
                                    displayName = tempOutputFile.name,
                                    mimeType = "video/mp4",
                                    fileSizeBytes = size,
                                    durationMs = exportResult.durationMs.takeIf { it > 0 } ?: 0L,
                                    width = exportResult.width,
                                    height = exportResult.height,
                                    bitrate = exportResult.averageVideoBitrate.toLong()
                                )
                            }

                            // Refine width/height from exportResult if extractor returned 0
                            val finalMeta = if (meta.width <= 0 && exportResult.width > 0) {
                                meta.copy(
                                    width = exportResult.width,
                                    height = exportResult.height,
                                    durationMs = if (meta.durationMs <= 0) exportResult.durationMs else meta.durationMs
                                )
                            } else {
                                meta
                            }

                            Triple(true, size, finalMeta)
                        }
                    }

                    if (!isValid || outputMetadata == null) {
                        return VideoCompressionResult.Failure("Compression produced missing or empty output file")
                    }

                    // Strict Post-Export Verification:
                    // If the source was significantly larger than the preset max dimension (e.g. 1080p source compressed with Small File 854x480),
                    // verify that the output resolution was actually resized and did not passthrough unchanged.
                    if (sourceMetadata != null && sourceMetadata.width > 0 && sourceMetadata.height > 0) {
                        val sourceMax = maxOf(sourceMetadata.width, sourceMetadata.height)
                        if (sourceMax > preset.maxDimension + 50) {
                            val outputMax = maxOf(outputMetadata.width, outputMetadata.height)
                            // Allow a small margin of tolerance (+/- 32px for codec macroblock alignments)
                            if (outputMax > preset.maxDimension + 32) {
                                withContext(Dispatchers.IO) {
                                    if (tempOutputFile.exists()) tempOutputFile.delete()
                                }
                                return VideoCompressionResult.Failure(
                                    "Target resolution was not applied by encoder (source was ${sourceMetadata.width}×${sourceMetadata.height}, output is ${outputMetadata.width}×${outputMetadata.height}, requested limit ${preset.maxDimension})"
                                )
                            }
                        }
                    }

                    isSuccessfulExport = true
                    onProgress(100)

                    return VideoCompressionResult.Success(
                        outputFile = tempOutputFile,
                        outputSizeBytes = outputSizeBytes,
                        outputMetadata = outputMetadata
                    )
                }
            }

        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                val transformer = activeTransformer.getAndSet(null)
                try {
                    transformer?.cancel()
                } catch (_: Exception) {}
            }
            withContext(NonCancellable + Dispatchers.IO) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            }
            return VideoCompressionResult.Cancelled
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                val transformer = activeTransformer.getAndSet(null)
                try {
                    transformer?.cancel()
                } catch (_: Exception) {}
            }
            withContext(NonCancellable + Dispatchers.IO) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            }
            return VideoCompressionResult.Failure(e.message ?: "Compression failed", e)
        } finally {
            if (!isSuccessfulExport) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                }
            }
        }
    }

    override fun cancel() {
        val transformer = activeTransformer.getAndSet(null) ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                transformer.cancel()
            } catch (_: Exception) {}
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    transformer.cancel()
                } catch (_: Exception) {}
            }
        }
    }
}

