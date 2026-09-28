package com.example.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
class VideoToAudioEngine(private val context: Context) : IVideoToAudioEngine {

    private val activeTransformer = AtomicReference<Transformer?>(null)
    private val isCancelled = AtomicBoolean(false)
    private val mp3Encoder = Mp3AudioEncoder(context)

    private sealed interface ExportSignal {
        data class Completed(val exportResult: ExportResult) : ExportSignal
        data class Error(val exception: ExportException) : ExportSignal
    }

    override suspend fun extractAudio(
        sourceUri: Uri,
        format: AudioOutputFormat,
        mp3Bitrate: Mp3Bitrate,
        m4aPreset: AudioQualityPreset,
        onProgress: (Int) -> Unit
    ): VideoToAudioResult {
        isCancelled.set(false)

        // Step 1: Validate metadata on Dispatchers.IO
        val (metadata, tempOutputFile) = withContext(Dispatchers.IO) {
            val meta = try {
                AudioMetadata.extract(context, sourceUri)
            } catch (e: Exception) {
                null
            }

            val tempDir = File(context.cacheDir, "extracted_audio").apply { mkdirs() }
            val file = File(tempDir, "audio_${UUID.randomUUID().toString().take(8)}${format.extension}")
            if (file.exists()) file.delete()

            Pair(meta, file)
        }

        if (metadata == null) {
            return VideoToAudioResult.Failure("Unable to read source video metadata")
        }

        if (!metadata.hasAudioTrack) {
            return VideoToAudioResult.Failure("No audio track was found in this video.")
        }

        // Branch 1: Real MP3 Encoding
        if (format == AudioOutputFormat.MP3) {
            val mp3Success = withContext(Dispatchers.IO) {
                mp3Encoder.encodeVideoToMp3(
                    sourceUri = sourceUri,
                    outputFile = tempOutputFile,
                    bitrateKbps = mp3Bitrate.bitrateKbps,
                    isCancelled = { isCancelled.get() },
                    onProgress = onProgress
                )
            }

            if (isCancelled.get()) {
                withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                return VideoToAudioResult.Cancelled
            }

            if (!mp3Success) {
                withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                return VideoToAudioResult.Failure("Failed to decode and encode video audio to MP3")
            }

            val validation = withContext(Dispatchers.IO) {
                validateAudioOutput(tempOutputFile, format)
            }

            return when (validation) {
                is ValidationResult.Valid -> {
                    onProgress(100)
                    VideoToAudioResult.Success(
                        tempFile = tempOutputFile,
                        durationMs = validation.durationMs,
                        fileSizeBytes = validation.fileSizeBytes,
                        format = AudioOutputFormat.MP3,
                        bitrateKbps = mp3Bitrate.bitrateKbps,
                        isDirectRemux = false
                    )
                }
                is ValidationResult.Invalid -> {
                    withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                    VideoToAudioResult.Failure("MP3 output validation failed: ${validation.reason}")
                }
            }
        }

        // Branch 2: M4A AAC Extraction (Preserved Path)
        val shouldTryDirectRemux = (m4aPreset == AudioQualityPreset.DIRECT_REMUX && metadata.canDirectRemux)
        if (shouldTryDirectRemux) {
            val directSuccess = withContext(Dispatchers.IO) {
                extractAudioDirect(sourceUri, tempOutputFile, onProgress)
            }

            if (isCancelled.get()) {
                withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                return VideoToAudioResult.Cancelled
            }

            if (directSuccess) {
                val validation = withContext(Dispatchers.IO) {
                    validateAudioOutput(tempOutputFile, AudioOutputFormat.M4A)
                }
                if (validation is ValidationResult.Valid) {
                    return VideoToAudioResult.Success(
                        tempFile = tempOutputFile,
                        durationMs = validation.durationMs,
                        fileSizeBytes = validation.fileSizeBytes,
                        format = AudioOutputFormat.M4A,
                        bitrateKbps = (metadata.bitrate / 1000).toInt().coerceAtLeast(128),
                        isDirectRemux = true
                    )
                }
                withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
            }
        }

        if (isCancelled.get()) {
            withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
            return VideoToAudioResult.Cancelled
        }

        // Fallback M4A encoding using Media3 Transformer strictly on Dispatchers.Main.immediate
        var isSuccess = false
        try {
            val exportResultSignal = withContext(Dispatchers.Main.immediate) {
                val mediaItem = MediaItem.fromUri(sourceUri)
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                val composition = Composition.Builder(EditedMediaItemSequence(editedMediaItem)).build()

                val transformationRequest = TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setTransformationRequest(transformationRequest)
                    .build()

                activeTransformer.set(transformer)

                val signalDeferred = CompletableDeferred<ExportSignal>()
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        signalDeferred.complete(ExportSignal.Completed(exportResult))
                    }

                    override fun onError(comp: Composition, exportResult: ExportResult, exception: ExportException) {
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
                while (!signalDeferred.isCompleted) {
                    delay(100)
                    if (isCancelled.get() || activeTransformer.get() == null) {
                        break
                    }
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    }
                }

                val result = if (activeTransformer.get() == null && !signalDeferred.isCompleted) {
                    null
                } else {
                    signalDeferred.await()
                }

                transformer.removeListener(listener)
                activeTransformer.set(null)
                result
            }

            if (exportResultSignal == null || isCancelled.get()) {
                withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                return VideoToAudioResult.Cancelled
            }

            when (exportResultSignal) {
                is ExportSignal.Error -> {
                    withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                    return VideoToAudioResult.Failure(
                        message = exportResultSignal.exception.localizedMessage ?: "Audio extraction failed",
                        cause = exportResultSignal.exception
                    )
                }
                is ExportSignal.Completed -> {
                    val validation = withContext(Dispatchers.IO) {
                        validateAudioOutput(tempOutputFile, AudioOutputFormat.M4A)
                    }

                    when (validation) {
                        is ValidationResult.Valid -> {
                            isSuccess = true
                            onProgress(100)
                            val bitrateVal = m4aPreset.targetBitrate?.div(1000) ?: 128
                            return VideoToAudioResult.Success(
                                tempFile = tempOutputFile,
                                durationMs = validation.durationMs,
                                fileSizeBytes = validation.fileSizeBytes,
                                format = AudioOutputFormat.M4A,
                                bitrateKbps = bitrateVal,
                                isDirectRemux = false
                            )
                        }
                        is ValidationResult.Invalid -> {
                            withContext(Dispatchers.IO) { if (tempOutputFile.exists()) tempOutputFile.delete() }
                            return VideoToAudioResult.Failure("Extracted audio validation failed: ${validation.reason}")
                        }
                    }
                }
            }

        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                activeTransformer.getAndSet(null)?.cancel()
            }
            withContext(NonCancellable + Dispatchers.IO) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            }
            return VideoToAudioResult.Cancelled
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                activeTransformer.getAndSet(null)?.cancel()
            }
            withContext(NonCancellable + Dispatchers.IO) {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            }
            return VideoToAudioResult.Failure(e.localizedMessage ?: "Audio extraction error", e)
        } finally {
            if (!isSuccess) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                }
            }
        }
    }

    private fun extractAudioDirect(
        sourceUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return false
            extractor.setDataSource(pfd.fileDescriptor)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 0
            val bufferSize = if (maxInputSize > 0) maxInputSize.coerceAtLeast(1024 * 64) else 1024 * 128

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (!isCancelled.get()) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                if (durationUs > 0) {
                    val progress = ((bufferInfo.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 99)
                    onProgress(progress)
                }

                extractor.advance()
            }

            if (isCancelled.get()) {
                return false
            }

            return true
        } catch (_: Exception) {
            return false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private sealed interface ValidationResult {
        data class Valid(val durationMs: Long, val fileSizeBytes: Long, val actualMime: String) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    private fun validateAudioOutput(outputFile: File, expectedFormat: AudioOutputFormat): ValidationResult {
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            return ValidationResult.Invalid("Output file does not exist or is empty")
        }

        val extractor = MediaExtractor()
        var hasAudio = false
        var hasVideo = false
        var audioMime = ""

        try {
            extractor.setDataSource(outputFile.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioMime = mime
                } else if (mime.startsWith("video/")) {
                    hasVideo = true
                }
            }
        } catch (e: Exception) {
            return ValidationResult.Invalid("Failed to inspect extracted media tracks: ${e.localizedMessage}")
        } finally {
            extractor.release()
        }

        if (!hasAudio) {
            return ValidationResult.Invalid("Output contains no audio track")
        }
        if (hasVideo) {
            return ValidationResult.Invalid("Output unexpectedly contains a video track")
        }

        // Strict validation: Verify MP3 output contains genuine audio/mpeg codec
        if (expectedFormat == AudioOutputFormat.MP3) {
            if (!audioMime.contains("mpeg", ignoreCase = true) && !audioMime.contains("mp3", ignoreCase = true)) {
                return ValidationResult.Invalid("Output file is not genuine MP3 audio (Actual track codec MIME: $audioMime)")
            }
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (durationMs <= 0L) {
            return ValidationResult.Invalid("Output audio duration is invalid")
        }

        return ValidationResult.Valid(
            durationMs = durationMs,
            fileSizeBytes = outputFile.length(),
            actualMime = audioMime
        )
    }

    override fun cancel() {
        isCancelled.set(true)
        activeTransformer.getAndSet(null)?.cancel()
    }
}
