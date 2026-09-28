package com.example.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class VolumePreset(val title: String, val gainFactor: Float, val description: String) {
    HALF("0.5x (-6 dB)", 0.5f, "Reduce loudness by half"),
    NORMAL("1.0x (Original)", 1.0f, "Standard original level"),
    BOOST_25("1.25x (+2 dB)", 1.25f, "Slight volume increase"),
    BOOST_50("1.5x (+3.5 dB)", 1.5f, "Moderate volume boost"),
    DOUBLE("2.0x (+6 dB)", 2.0f, "Maximum high gain boost")
}

sealed interface AudioVolumeResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val gainFactor: Float
    ) : AudioVolumeResult

    data class Failure(val errorMessage: String, val cause: Throwable? = null) : AudioVolumeResult
    data object Cancelled : AudioVolumeResult
}

class AudioVolumeEngine(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    suspend fun boostVolume(
        sourceUri: Uri,
        gainFactor: Float,
        targetFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        onProgress: (Int) -> Unit
    ): AudioVolumeResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)

        val metadata = try {
            AudioMetadata.extract(context, sourceUri)
        } catch (e: Exception) {
            null
        }

        if (metadata == null || !metadata.hasAudioTrack || metadata.durationMs <= 0L) {
            return@withContext AudioVolumeResult.Failure("Source file does not contain a valid readable audio track.")
        }

        val tempDir = File(context.cacheDir, "boosted_audio").apply { mkdirs() }
        val tempOutputFile = File(tempDir, "volume_${UUID.randomUUID().toString().take(8)}${targetFormat.extension}")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val success = processPcmWithGain(
            sourceUri = sourceUri,
            outputFile = tempOutputFile,
            gainFactor = gainFactor,
            targetFormat = targetFormat,
            totalDurationMs = metadata.durationMs,
            onProgress = onProgress
        )

        if (isCancelled.get()) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioVolumeResult.Cancelled
        }

        if (!success || !tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioVolumeResult.Failure("Failed to apply volume gain to audio track")
        }

        val finalDurationMs = getAudioDurationMs(tempOutputFile).takeIf { it > 0L } ?: metadata.durationMs

        onProgress(100)
        return@withContext AudioVolumeResult.Success(
            tempFile = tempOutputFile,
            durationMs = finalDurationMs,
            fileSizeBytes = tempOutputFile.length(),
            format = targetFormat,
            gainFactor = gainFactor
        )
    }

    private fun processPcmWithGain(
        sourceUri: Uri,
        outputFile: File,
        gainFactor: Float,
        targetFormat: AudioOutputFormat,
        totalDurationMs: Long,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var pfd: ParcelFileDescriptor? = null
        var fos: FileOutputStream? = null
        var mp3Encoder: LameMp3Encoder? = null

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

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            if (targetFormat == AudioOutputFormat.MP3) {
                mp3Encoder = LameMp3Encoder(sampleRate, channelCount, 192)
            } else if (targetFormat == AudioOutputFormat.WAV) {
                // Write placeholder WAV header
                fos.write(ByteArray(44))
            }

            val mp3Buffer = ByteArray(1024 * 64)
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

                        // Real sample gain modification & soft-limiting
                        val modifiedPcm = applyGainToPcm(pcmData, pcmData.size, gainFactor)
                        totalPcmBytes += modifiedPcm.size

                        if (targetFormat == AudioOutputFormat.MP3 && mp3Encoder != null) {
                            val encoded = mp3Encoder.encodeBuffer(modifiedPcm, 0, modifiedPcm.size, mp3Buffer)
                            if (encoded > 0) {
                                fos.write(mp3Buffer, 0, encoded)
                            }
                        } else {
                            fos.write(modifiedPcm)
                        }

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
                    if (targetFormat == AudioOutputFormat.MP3) {
                        try { mp3Encoder?.close() } catch (_: Exception) {}
                        mp3Encoder = LameMp3Encoder(sampleRate, channelCount, 192)
                    }
                }
            }

            if (isCancelled.get() || totalPcmBytes == 0L) {
                return false
            }

            if (targetFormat == AudioOutputFormat.MP3 && mp3Encoder != null) {
                val flushed = mp3Encoder.encodeFinish(mp3Buffer)
                if (flushed > 0) {
                    fos.write(mp3Buffer, 0, flushed)
                }
            }

            fos.flush()
            fos.close()
            fos = null

            if (targetFormat == AudioOutputFormat.WAV) {
                AudioConverterEngine.writeWavHeader(outputFile, totalPcmBytes, sampleRate, channelCount)
            }

            return true

        } catch (_: Exception) {
            return false
        } finally {
            try { mp3Encoder?.close() } catch (_: Exception) {}
            try { fos?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        fun applyGainToPcm(pcmBytes: ByteArray, length: Int, gainFactor: Float): ByteArray {
            val byteBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            val shortBuffer = byteBuffer.asShortBuffer()
            val numSamples = length / 2
            val outBytes = ByteArray(length)
            val outByteBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
            val outShortBuffer = outByteBuffer.asShortBuffer()

            for (i in 0 until numSamples) {
                val sample = shortBuffer.get(i).toFloat()
                val gained = sample * gainFactor

                // Limiter & Clamping
                val clamped = when {
                    gained > 32767f -> 32767
                    gained < -32768f -> -32768
                    else -> gained.toInt()
                }
                outShortBuffer.put(i, clamped.toShort())
            }
            return outBytes
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
