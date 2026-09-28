package com.example.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class Mp3AudioEncoder(private val context: Context) {

    suspend fun encodeVideoToMp3(
        sourceUri: Uri,
        outputFile: File,
        bitrateKbps: Int,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var pfd: ParcelFileDescriptor? = null
        var mp3Encoder: LameMp3Encoder? = null
        var outputStream: FileOutputStream? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return@withContext false
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

            if (audioTrackIndex < 0 || inputFormat == null) return@withContext false

            extractor.selectTrack(audioTrackIndex)

            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

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

            fun initLameEncoder(sRate: Int, channels: Int) {
                try { mp3Encoder?.close() } catch (_: Exception) {}
                mp3Encoder = LameMp3Encoder(sRate, channels, bitrateKbps)
            }

            initLameEncoder(sampleRate, channelCount)

            val mp3Buffer = ByteArray(1024 * 64)

            while (!outputEos && !isCancelled()) {
                // 1. Feed compressed audio from MediaExtractor to MediaCodec decoder
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
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Read decoded PCM samples from MediaCodec and pass to LameEncoder
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)

                        val encodedBytes = mp3Encoder?.encodeBuffer(pcmData, 0, pcmData.size, mp3Buffer) ?: 0
                        if (encodedBytes > 0) {
                            outputStream.write(mp3Buffer, 0, encodedBytes)
                        }

                        if (durationUs > 0) {
                            val progress = ((bufferInfo.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 99)
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
                    initLameEncoder(sampleRate, channelCount)
                }
            }

            if (isCancelled()) {
                return@withContext false
            }

            // Flush final MP3 frames
            val flushedBytes = mp3Encoder?.encodeFinish(mp3Buffer) ?: 0
            if (flushedBytes > 0) {
                outputStream.write(mp3Buffer, 0, flushedBytes)
            }
            outputStream.flush()

            return@withContext true

        } catch (e: Exception) {
            return@withContext false
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
