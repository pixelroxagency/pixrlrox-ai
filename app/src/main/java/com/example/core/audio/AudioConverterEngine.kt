package com.example.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface AudioConverterResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val bitrateKbps: Int
    ) : AudioConverterResult

    data class Failure(val errorMessage: String, val cause: Throwable? = null) : AudioConverterResult
    data object Cancelled : AudioConverterResult
}

@OptIn(UnstableApi::class)
class AudioConverterEngine(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)
    private val activeTransformer = AtomicReference<Transformer?>(null)
    private val mp3Encoder = Mp3AudioEncoder(context)

    fun cancel() {
        isCancelled.set(true)
        activeTransformer.getAndSet(null)?.cancel()
    }

    suspend fun convertAudio(
        sourceUri: Uri,
        targetFormat: AudioOutputFormat,
        bitrateKbps: Int = 192,
        onProgress: (Int) -> Unit
    ): AudioConverterResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)

        val metadata = try {
            AudioMetadata.extract(context, sourceUri)
        } catch (e: Exception) {
            null
        }

        if (metadata == null || !metadata.hasAudioTrack || metadata.durationMs <= 0L) {
            return@withContext AudioConverterResult.Failure("Source file does not contain a valid readable audio track.")
        }

        val tempDir = File(context.cacheDir, "converted_audio").apply { mkdirs() }
        val tempOutputFile = File(tempDir, "conv_${UUID.randomUUID().toString().take(8)}${targetFormat.extension}")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val success = when (targetFormat) {
            AudioOutputFormat.MP3 -> {
                mp3Encoder.encodeVideoToMp3(
                    sourceUri = sourceUri,
                    outputFile = tempOutputFile,
                    bitrateKbps = bitrateKbps,
                    isCancelled = { isCancelled.get() },
                    onProgress = onProgress
                )
            }
            AudioOutputFormat.WAV -> {
                convertToWav(sourceUri, tempOutputFile, metadata.durationMs, onProgress)
            }
            AudioOutputFormat.M4A -> {
                convertToM4a(sourceUri, tempOutputFile, onProgress)
            }
        }

        if (isCancelled.get()) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioConverterResult.Cancelled
        }

        if (!success || !tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioConverterResult.Failure("Failed to convert audio file to ${targetFormat.title}")
        }

        val finalDurationMs = getAudioDurationMs(tempOutputFile).takeIf { it > 0L } ?: metadata.durationMs

        onProgress(100)
        return@withContext AudioConverterResult.Success(
            tempFile = tempOutputFile,
            durationMs = finalDurationMs,
            fileSizeBytes = tempOutputFile.length(),
            format = targetFormat,
            bitrateKbps = if (targetFormat == AudioOutputFormat.WAV) 1411 else bitrateKbps
        )
    }

    private suspend fun convertToM4a(
        sourceUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Boolean {
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

                val signalDeferred = CompletableDeferred<Boolean>()
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        signalDeferred.complete(true)
                    }

                    override fun onError(comp: Composition, exportResult: ExportResult, exception: ExportException) {
                        signalDeferred.complete(false)
                    }
                }

                transformer.addListener(listener)

                try {
                    transformer.start(composition, outputFile.absolutePath)
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
                    false
                } else {
                    signalDeferred.await()
                }

                transformer.removeListener(listener)
                activeTransformer.set(null)
                result
            }

            isSuccess = exportResultSignal
            return isSuccess
        } catch (_: Exception) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                activeTransformer.getAndSet(null)?.cancel()
            }
            return false
        }
    }

    private fun convertToWav(
        sourceUri: Uri,
        outputFile: File,
        totalDurationMs: Long,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var pfd: ParcelFileDescriptor? = null
        var fos: FileOutputStream? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return false
            extractor.setDataSource(pfd.fileDescriptor)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) return false

            extractor.selectTrack(audioTrackIndex)

            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            fos = FileOutputStream(outputFile)
            // Write placeholder 44-byte WAV header
            fos.write(ByteArray(44))

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            var totalPcmBytes = 0L
            val timeoutUs = 10000L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            val totalDurationUs = totalDurationMs * 1000L

            while (!outputEos && !isCancelled.get()) {
                if (!inputEos) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val timeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, timeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)
                        fos.write(pcmData)
                        totalPcmBytes += pcmData.size

                        if (totalDurationUs > 0) {
                            val progress = ((bufferInfo.presentationTimeUs * 100) / totalDurationUs).toInt().coerceIn(0, 99)
                            onProgress(progress)
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            fos.flush()
            fos.close()
            fos = null

            if (isCancelled.get() || totalPcmBytes == 0L) {
                return false
            }

            // Write real WAV RIFF Header
            writeWavHeader(outputFile, totalPcmBytes, sampleRate, channelCount)
            return true

        } catch (_: Exception) {
            return false
        } finally {
            try { fos?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        fun writeWavHeader(file: File, totalPcmBytes: Long, sampleRate: Int, channels: Int) {
            val totalDataLen = totalPcmBytes + 36
            val byteRate = sampleRate * channels * 2
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray())
                putInt(totalDataLen.toInt())
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // Subchunk1Size (16 for PCM)
                putShort(1.toShort()) // AudioFormat (1 for PCM)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort((channels * 2).toShort()) // BlockAlign
                putShort(16.toShort()) // BitsPerSample
                put("data".toByteArray())
                putInt(totalPcmBytes.toInt())
            }.array()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        }

        fun getAudioDurationMs(file: File): Long {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
}
