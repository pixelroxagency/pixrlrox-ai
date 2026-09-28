package com.example.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AudioCutterResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat
    ) : AudioCutterResult

    data class Failure(val errorMessage: String) : AudioCutterResult
    data object Cancelled : AudioCutterResult
}

class AudioCutterEngine(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    suspend fun trimAudio(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: AudioOutputFormat,
        onProgress: (Int) -> Unit
    ): AudioCutterResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)

        if (endMs <= startMs) {
            return@withContext AudioCutterResult.Failure("End time must be greater than start time")
        }

        val tempDir = File(context.cacheDir, "trimmed_audio").apply { mkdirs() }
        val tempOutputFile = File(tempDir, "trim_${UUID.randomUUID().toString().take(8)}${targetFormat.extension}")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val metadata = try {
            AudioMetadata.extract(context, sourceUri)
        } catch (e: Exception) {
            null
        }

        if (metadata == null || !metadata.hasAudioTrack) {
            return@withContext AudioCutterResult.Failure("Could not detect valid audio track from source")
        }

        val success = if (targetFormat == AudioOutputFormat.MP3) {
            trimToMp3(sourceUri, tempOutputFile, startMs, endMs, onProgress)
        } else {
            // Try direct M4A remux first
            val directRemuxOk = trimDirectM4a(sourceUri, tempOutputFile, startMs, endMs, onProgress)
            if (!directRemuxOk) {
                // Fallback to MP3 if direct M4A remux is not supported for this stream
                val mp3Temp = File(tempDir, "trim_${UUID.randomUUID().toString().take(8)}.mp3")
                val fallbackOk = trimToMp3(sourceUri, mp3Temp, startMs, endMs, onProgress)
                if (fallbackOk && mp3Temp.exists() && mp3Temp.length() > 0) {
                    return@withContext AudioCutterResult.Success(
                        tempFile = mp3Temp,
                        durationMs = endMs - startMs,
                        fileSizeBytes = mp3Temp.length(),
                        format = AudioOutputFormat.MP3
                    )
                }
                false
            } else {
                true
            }
        }

        if (isCancelled.get()) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioCutterResult.Cancelled
        }

        if (!success || !tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext AudioCutterResult.Failure("Failed to trim audio file")
        }

        onProgress(100)
        return@withContext AudioCutterResult.Success(
            tempFile = tempOutputFile,
            durationMs = endMs - startMs,
            fileSizeBytes = tempOutputFile.length(),
            format = targetFormat
        )
    }

    private fun trimDirectM4a(
        sourceUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
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

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val clipSpanUs = (endUs - startUs).coerceAtLeast(1L)

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else 0
            val bufferSize = if (maxInputSize > 0) maxInputSize.coerceAtLeast(1024 * 64) else 1024 * 128

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var firstSampleTimeUs = -1L

            while (!isCancelled.get()) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstSampleTimeUs < 0) {
                        firstSampleTimeUs = sampleTimeUs
                    }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                    val progress = (((sampleTimeUs - startUs) * 100) / clipSpanUs).toInt().coerceIn(0, 99)
                    onProgress(progress)
                }

                extractor.advance()
            }

            return !isCancelled.get() && firstSampleTimeUs >= 0
        } catch (_: Exception) {
            return false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun trimToMp3(
        sourceUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var pfd: ParcelFileDescriptor? = null
        var mp3Encoder: LameMp3Encoder? = null
        var outputStream: FileOutputStream? = null

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

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val clipSpanUs = (endUs - startUs).coerceAtLeast(1L)

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            outputStream = FileOutputStream(outputFile)

            var inputEos = false
            var outputEos = false
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10000L

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            fun initLame(sRate: Int, channels: Int) {
                try { mp3Encoder?.close() } catch (_: Exception) {}
                mp3Encoder = LameMp3Encoder(sRate, channels, 192)
            }

            initLame(sampleRate, channelCount)

            val mp3Buffer = ByteArray(1024 * 64)

            while (!outputEos && !isCancelled.get()) {
                if (!inputEos) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || sampleTimeUs > endUs) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val presentationTimeUs = bufferInfo.presentationTimeUs

                        // Only write samples within [startUs, endUs]
                        if (presentationTimeUs >= startUs && presentationTimeUs <= endUs) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val pcmData = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmData)

                            val encodedBytes = mp3Encoder?.encodeBuffer(pcmData, 0, pcmData.size, mp3Buffer) ?: 0
                            if (encodedBytes > 0) {
                                outputStream.write(mp3Buffer, 0, encodedBytes)
                            }

                            val progress = (((presentationTimeUs - startUs) * 100) / clipSpanUs).toInt().coerceIn(0, 99)
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
                    initLame(sampleRate, channelCount)
                }
            }

            if (isCancelled.get()) return false

            val flushedBytes = mp3Encoder?.encodeFinish(mp3Buffer) ?: 0
            if (flushedBytes > 0) {
                outputStream.write(mp3Buffer, 0, flushedBytes)
            }
            outputStream.flush()
            return true

        } catch (_: Exception) {
            return false
        } finally {
            try { mp3Encoder?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
