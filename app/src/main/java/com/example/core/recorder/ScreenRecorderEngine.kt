package com.example.core.recorder

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class ScreenRecorderEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val mode: RecordingMode,
    private val quality: VideoQuality,
    private val frameRate: FrameRate,
    private val orientation: RecordingOrientation
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var isRecording = false
    private var isPaused = false
    private var isFinalizing = false

    private var deviceAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null
    private var audioCaptureJob: Job? = null

    private data class EncodedSample(val byteBuffer: ByteBuffer, val bufferInfo: MediaCodec.BufferInfo)
    private val preMuxerVideoQueue = mutableListOf<EncodedSample>()

    // Diagnostics counters & PTS tracking
    private var recordingStartMs = 0L
    private var firstVideoSourcePtsUs = -1L
    private var videoFirstPtsUs = -1L
    private var videoLastPtsUs = -1L
    private var audioFirstPtsUs = -1L
    private var audioLastPtsUs = -1L
    private var videoFramesEncodedCount = 0
    private var videoSamplesWrittenCount = 0
    private var audioSamplesWrittenCount = 0
    private var totalAudioFramesSubmitted = 0L

    // Device audio diagnostics / silence detector
    private var devicePcmBytesTotal = 0L
    private var deviceNonZeroSamplesTotal = 0L
    private var deviceTotalSamplesCount = 0L
    private var devicePeakSample = 0
    private var consecutiveAllZeroBuffers = 0

    private var tempOutputFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val captureDeviceAudio = (mode == RecordingMode.SCREEN_AUDIO || mode == RecordingMode.SCREEN_AUDIO_MIC)
    private val captureMicrophone = (mode == RecordingMode.SCREEN_AUDIO_MIC)
    private val recordAudio = captureDeviceAudio || captureMicrophone

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("PixelRoxRecorder", "MediaProjection.Callback.onStop triggered")
            save()
        }
    }

    fun start(): File? {
        try {
            recordingStartMs = System.currentTimeMillis()
            firstVideoSourcePtsUs = -1L
            videoFirstPtsUs = -1L
            videoLastPtsUs = -1L
            audioFirstPtsUs = -1L
            audioLastPtsUs = -1L
            videoFramesEncodedCount = 0
            videoSamplesWrittenCount = 0
            audioSamplesWrittenCount = 0
            totalAudioFramesSubmitted = 0L
            devicePcmBytesTotal = 0L
            deviceNonZeroSamplesTotal = 0L
            deviceTotalSamplesCount = 0L
            devicePeakSample = 0
            consecutiveAllZeroBuffers = 0
            preMuxerVideoQueue.clear()

            Log.d("PixelRoxRecorder", "REC_START_01 config received")
            Log.d("PixelRoxRecorder", "AUDIO_01 mode=${mode.name}")
            Log.d("PixelRoxRecorder", "recordAudio=$recordAudio, captureDeviceAudio=$captureDeviceAudio, captureMicrophone=$captureMicrophone")

            // Microphone permission check
            if (captureMicrophone) {
                val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                val granted = perm == PackageManager.PERMISSION_GRANTED
                Log.d("PixelRoxRecorder", "AUDIO_02 microphonePermission=${if (granted) "GRANTED" else "DENIED"}")
            } else {
                Log.d("PixelRoxRecorder", "AUDIO_02 microphonePermission=NOT_REQUIRED")
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(metrics)
            }

            var width = if (metrics.widthPixels > 0) metrics.widthPixels else context.resources.displayMetrics.widthPixels
            var height = if (metrics.heightPixels > 0) metrics.heightPixels else context.resources.displayMetrics.heightPixels
            var density = context.resources.displayMetrics.densityDpi
            if (density <= 0) {
                density = metrics.densityDpi
            }
            if (density <= 0) {
                density = DisplayMetrics.DENSITY_DEFAULT
            }

            Log.d("PixelRoxRecorder", "DISPLAY_METRICS widthPixels=${metrics.widthPixels} heightPixels=${metrics.heightPixels} densityDpi=$density")

            if (quality == VideoQuality.HD_720P) {
                if (width > height) {
                    width = 1280
                    height = 720
                } else {
                    width = 720
                    height = 1280
                }
            } else if (quality == VideoQuality.FULL_HD_1080P) {
                if (width > height) {
                    width = 1920
                    height = 1080
                } else {
                    width = 1080
                    height = 1920
                }
            } else {
                width = if (width > 0) (width / 2) * 2 else 1080
                height = if (height > 0) (height / 2) * 2 else 1920
            }

            if (orientation == RecordingOrientation.PORTRAIT && width > height) {
                val temp = width
                width = height
                height = temp
            } else if (orientation == RecordingOrientation.LANDSCAPE && height > width) {
                val temp = width
                width = height
                height = temp
            }

            if (width <= 0) width = 1080
            if (height <= 0) height = 1920
            if (density <= 0) density = 420

            Log.d("PixelRoxRecorder", "REC_START_02 dimensions resolved: width=$width, height=$height, density=$density")
            Log.d("PixelRoxRecorder", "VD_CONFIG width=$width height=$height densityDpi=$density fps=${frameRate.fps} quality=${quality.name} orientation=${orientation.name}")

            tempOutputFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")

            // Setup Video Encoder
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            Log.d("PixelRoxRecorder", "REC_START_03 encoder configured")
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            // Setup Audio Encoder if recordAudio
            val sampleRate = 44100
            val channelCount = 2
            val channelConfigIn = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            if (recordAudio) {
                try {
                    val audioFormatMuxer = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                    }
                    audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(audioFormatMuxer, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                    }
                    Log.d("PixelRoxRecorder", "AUDIO_09 AAC configured")
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR configuring AAC encoder", e)
                }

                // Initialize Device Audio AudioRecord (AudioPlaybackCapture)
                if (captureDeviceAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build()
                        Log.d("PixelRoxRecorder", "AUDIO_03 playbackCaptureConfigCreated=true")

                        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
                        val bufferSize = maxOf(minBufSize * 2, 4096 * 4)

                        deviceAudioRecord = AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(playbackConfig)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(audioFormat)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channelConfigIn)
                                    .build()
                            )
                            .setBufferSizeInBytes(bufferSize)
                            .build()

                        val state = deviceAudioRecord?.state
                        Log.d("PixelRoxRecorder", "AUDIO_04 device AudioRecord initialized, state=$state")
                    } catch (e: Exception) {
                        Log.e("PixelRoxRecorder", "AUDIO_ERROR creating device AudioRecord", e)
                        Log.d("PixelRoxRecorder", "AUDIO_03 playbackCaptureConfigCreated=false")
                    }
                } else {
                    Log.d("PixelRoxRecorder", "AUDIO_03 playbackCaptureConfigCreated=false (not Q+ or not requested)")
                }

                // Initialize Microphone AudioRecord
                if (captureMicrophone) {
                    try {
                        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
                        val bufferSize = maxOf(minBufSize * 2, 4096 * 4)

                        micAudioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfigIn,
                            audioFormat,
                            bufferSize
                        )
                        val state = micAudioRecord?.state
                        Log.d("PixelRoxRecorder", "AUDIO_05 microphone AudioRecord initialized, state=$state")
                    } catch (e: Exception) {
                        Log.e("PixelRoxRecorder", "AUDIO_ERROR creating mic AudioRecord", e)
                    }
                } else {
                    Log.d("PixelRoxRecorder", "AUDIO_05 microphone AudioRecord initialized=NOT_REQUIRED")
                }
            }

            Log.d("PixelRoxRecorder", "REC_START_04 muxer prepared")
            mediaMuxer = MediaMuxer(tempOutputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            try {
                mediaProjection.registerCallback(projectionCallback, null)
            } catch (_: Exception) {}

            Log.d("PixelRoxRecorder", "REC_START_05 virtual display creating")
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecorderVD",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )
            Log.d("PixelRoxRecorder", "REC_START_06 virtual display created")

            isRecording = true
            isFinalizing = false

            // Start Audio recording / capture if applicable
            if (recordAudio) {
                try {
                    deviceAudioRecord?.let {
                        if (it.state == AudioRecord.STATE_INITIALIZED) {
                            it.startRecording()
                            Log.d("PixelRoxRecorder", "AUDIO_06 device capture started=true")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR starting device capture", e)
                }

                try {
                    micAudioRecord?.let {
                        if (it.state == AudioRecord.STATE_INITIALIZED) {
                            it.startRecording()
                            Log.d("PixelRoxRecorder", "AUDIO_07 microphone capture started=true")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR starting mic capture", e)
                }

                startAudioCaptureAndEncoderLoop(deviceAudioRecord, micAudioRecord, sampleRate)
            }

            startVideoEncoderLoop()

            Log.d("PixelRoxRecorder", "REC_START_07 recording active")
            return tempOutputFile
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "REC_START_ERROR: Failed to start recording", e)
            cancel()
            return null
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    private fun startVideoEncoderLoop() {
        scope.launch {
            val videoBufferInfo = MediaCodec.BufferInfo()
            while (isRecording && !isFinalizing) {
                try {
                    val encoder = videoEncoder ?: break
                    val outputIndex = encoder.dequeueOutputBuffer(videoBufferInfo, 10000)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = encoder.outputFormat
                        synchronized(this@ScreenRecorderEngine) {
                            if (videoTrackIndex == -1 && mediaMuxer != null) {
                                videoTrackIndex = mediaMuxer!!.addTrack(newFormat)
                                Log.d("PixelRoxRecorder", "MUXER videoTrack=$videoTrackIndex audioTrack=$audioTrackIndex started=$isMuxerStarted")
                                tryCheckAndStartMuxer()
                            }
                        }
                    } else if (outputIndex >= 0) {
                        val encodedData = encoder.getOutputBuffer(outputIndex)
                        if (encodedData != null) {
                            if (!isPaused && !isFinalizing) {
                                if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || videoBufferInfo.size <= 0) {
                                    encoder.releaseOutputBuffer(outputIndex, false)
                                    continue
                                }

                                videoFramesEncodedCount++
                                val rawPts = videoBufferInfo.presentationTimeUs
                                if (firstVideoSourcePtsUs < 0L) {
                                    firstVideoSourcePtsUs = rawPts
                                }

                                val clonedBuf = ByteBuffer.allocateDirect(videoBufferInfo.size)
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                clonedBuf.put(encodedData)
                                clonedBuf.flip()

                                val clonedInfo = MediaCodec.BufferInfo().apply {
                                    set(0, videoBufferInfo.size, videoBufferInfo.presentationTimeUs, videoBufferInfo.flags)
                                }

                                synchronized(this@ScreenRecorderEngine) {
                                    if (isMuxerStarted && mediaMuxer != null && videoTrackIndex >= 0) {
                                        flushPreMuxerQueueLocked()
                                        writeNormalizedSample(videoTrackIndex, clonedBuf, clonedInfo, isVideo = true)
                                    } else {
                                        preMuxerVideoQueue.add(EncodedSample(clonedBuf, clonedInfo))
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR in video encoder loop", e)
                    break
                }
            }
        }
    }

    private fun startAudioCaptureAndEncoderLoop(devRecord: AudioRecord?, micRecord: AudioRecord?, sampleRate: Int) {
        audioCaptureJob = scope.launch(Dispatchers.IO) {
            Log.d("PixelRoxRecorder", "AUDIO_08 PCM_CAPTURE_ACTIVE")
            val bufferSizeBytes = 4096
            val devBuffer = ByteArray(bufferSizeBytes)
            val micBuffer = ByteArray(bufferSizeBytes)

            val audioEnc = audioEncoder ?: return@launch

            val audioBufferInfo = MediaCodec.BufferInfo()
            val audioDrainJob = launch {
                while (isRecording && !isFinalizing) {
                    try {
                        val outIndex = audioEnc.dequeueOutputBuffer(audioBufferInfo, 10000)
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val newFormat = audioEnc.outputFormat
                            synchronized(this@ScreenRecorderEngine) {
                                if (audioTrackIndex == -1 && mediaMuxer != null) {
                                    audioTrackIndex = mediaMuxer!!.addTrack(newFormat)
                                    Log.d("PixelRoxRecorder", "AUDIO_12 AUDIO_TRACK_ADDED index=$audioTrackIndex")
                                    Log.d("PixelRoxRecorder", "MUXER videoTrack=$videoTrackIndex audioTrack=$audioTrackIndex started=$isMuxerStarted")
                                    tryCheckAndStartMuxer()
                                }
                            }
                        } else if (outIndex >= 0) {
                            val encodedData = audioEnc.getOutputBuffer(outIndex)
                            if (encodedData != null) {
                                if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || audioBufferInfo.size <= 0) {
                                    audioEnc.releaseOutputBuffer(outIndex, false)
                                    continue
                                }

                                synchronized(this@ScreenRecorderEngine) {
                                    if (isMuxerStarted && mediaMuxer != null && audioTrackIndex >= 0) {
                                        val clonedBuf = ByteBuffer.allocateDirect(audioBufferInfo.size)
                                        encodedData.position(audioBufferInfo.offset)
                                        encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                        clonedBuf.put(encodedData)
                                        clonedBuf.flip()

                                        val clonedInfo = MediaCodec.BufferInfo().apply {
                                            set(0, audioBufferInfo.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                                        }

                                        writeNormalizedSample(audioTrackIndex, clonedBuf, clonedInfo, isVideo = false)
                                        Log.d("PixelRoxRecorder", "AUDIO_13 AUDIO_SAMPLES_WRITTEN bytes=${audioBufferInfo.size}")
                                    }
                                }
                                audioEnc.releaseOutputBuffer(outIndex, false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PixelRoxRecorder", "AUDIO_ERROR in audio drain loop", e)
                        break
                    }
                }
            }

            while (isRecording && !isFinalizing) {
                try {
                    if (isPaused) {
                        delay(100)
                        continue
                    }

                    var devBytesRead = 0
                    var micBytesRead = 0

                    if (devRecord != null && devRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        devBytesRead = devRecord.read(devBuffer, 0, bufferSizeBytes)
                        if (devBytesRead < 0) devBytesRead = 0
                    }

                    if (micRecord != null && micRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        micBytesRead = micRecord.read(micBuffer, 0, bufferSizeBytes)
                        if (micBytesRead < 0) micBytesRead = 0
                    }

                    Log.d("PixelRoxRecorder", "AUDIO_PCM deviceBytes=$devBytesRead micBytes=$micBytesRead")

                    // Device audio silence detector diagnostics
                    if (devBytesRead > 0) {
                        devicePcmBytesTotal += devBytesRead
                        var nonZeroInThisBuffer = 0
                        var totalInThisBuffer = 0
                        var peakInThisBuffer = 0
                        var sumSq = 0L

                        val shortCount = devBytesRead / 2
                        for (i in 0 until shortCount) {
                            val low = devBuffer[i * 2].toInt() and 0xFF
                            val high = devBuffer[i * 2 + 1].toInt() shl 8
                            val s = (high or low).toShort().toInt()
                            val absVal = abs(s)
                            totalInThisBuffer++
                            if (absVal > 0) {
                                nonZeroInThisBuffer++
                            }
                            if (absVal > peakInThisBuffer) {
                                peakInThisBuffer = absVal
                            }
                            sumSq += (s * s).toLong()
                        }

                        deviceNonZeroSamplesTotal += nonZeroInThisBuffer
                        deviceTotalSamplesCount += totalInThisBuffer
                        if (peakInThisBuffer > devicePeakSample) {
                            devicePeakSample = peakInThisBuffer
                        }

                        val rms = if (totalInThisBuffer > 0) sqrt((sumSq / totalInThisBuffer).toDouble()).toInt() else 0
                        Log.d("PixelRoxRecorder", "DEVICE_PCM_DIAG bytesRead=$devBytesRead nonZero=$nonZeroInThisBuffer total=$totalInThisBuffer peak=$peakInThisBuffer rms=$rms")

                        if (nonZeroInThisBuffer == 0) {
                            consecutiveAllZeroBuffers++
                            if (consecutiveAllZeroBuffers >= 5) {
                                Log.d("PixelRoxRecorder", "DEVICE_AUDIO_ALL_ZERO_PCM")
                                Log.d("PixelRoxRecorder", "DEVICE_AUDIO_CAPTURE_SILENT_SOURCE")
                            }
                        } else {
                            consecutiveAllZeroBuffers = 0
                            Log.d("PixelRoxRecorder", "DEVICE_AUDIO_SIGNAL_CONFIRMED")
                        }
                    }

                    val devRms = calculateRms(devBuffer, devBytesRead)
                    val micRms = calculateRms(micBuffer, micBytesRead)
                    Log.d("PixelRoxRecorder", "AUDIO_LEVEL device=$devRms mic=$micRms")

                    val mixedBuffer: ByteArray
                    if (devRecord != null && micRecord != null) {
                        val maxLen = maxOf(devBytesRead, micBytesRead)
                        mixedBuffer = ByteArray(maxLen)
                        mixPcm16Bit(devBuffer, devBytesRead, micBuffer, micBytesRead, mixedBuffer)
                        val mixedRms = calculateRms(mixedBuffer, maxLen)
                        Log.d("PixelRoxRecorder", "AUDIO_MIX deviceFrames=${devBytesRead/4} micFrames=${micBytesRead/4} mixedFrames=${maxLen/4}")
                        Log.d("PixelRoxRecorder", "AUDIO_MIX_LEVEL device=$devRms mic=$micRms mixed=$mixedRms")
                    } else if (devRecord != null) {
                        mixedBuffer = devBuffer
                    } else if (micRecord != null) {
                        mixedBuffer = micBuffer
                    } else {
                        mixedBuffer = ByteArray(0)
                    }

                    if (mixedBuffer.isNotEmpty()) {
                        val framesInBlock = mixedBuffer.size / 4 // 2 channels * 2 bytes/sample = 4 bytes per frame
                        val audioPtsUs = (totalAudioFramesSubmitted * 1_000_000L) / sampleRate
                        totalAudioFramesSubmitted += framesInBlock.toLong()

                        Log.d("PixelRoxRecorder", "MUX_PTS_AUDIO=$audioPtsUs")

                        val inputIndex = audioEnc.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val inputBuffer = audioEnc.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                inputBuffer.put(mixedBuffer, 0, mixedBuffer.size)
                                audioEnc.queueInputBuffer(inputIndex, 0, mixedBuffer.size, audioPtsUs, 0)
                                Log.d("PixelRoxRecorder", "AUDIO_10 AAC input queued, bytes=${mixedBuffer.size}, ptsUs=$audioPtsUs")
                            }
                        }
                    }

                    delay(10)
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR in audio capture loop", e)
                    break
                }
            }

            audioDrainJob.cancel()
        }
    }

    @Synchronized
    private fun writeNormalizedSample(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean) {
        if (mediaMuxer == null || !isMuxerStarted) return

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || bufferInfo.size <= 0) {
            return
        }

        val sourcePts = bufferInfo.presentationTimeUs
        val wallDurationUs = (System.currentTimeMillis() - recordingStartMs) * 1000L

        val normalizedPts: Long
        if (isVideo) {
            val base = if (firstVideoSourcePtsUs >= 0L) firstVideoSourcePtsUs else sourcePts
            normalizedPts = maxOf(0L, sourcePts - base)

            if (videoFirstPtsUs < 0L) videoFirstPtsUs = normalizedPts
            val delta = if (videoLastPtsUs >= 0L) normalizedPts - videoLastPtsUs else 0L
            videoLastPtsUs = normalizedPts
            videoSamplesWrittenCount++
            Log.d("PixelRoxRecorder", "PTS_VIDEO first=$videoFirstPtsUs current=$normalizedPts delta=$delta")
            Log.d("PixelRoxRecorder", "MUX_PTS_VIDEO original=$sourcePts normalized=$normalizedPts")
        } else {
            normalizedPts = maxOf(0L, sourcePts)
            if (audioFirstPtsUs < 0L) audioFirstPtsUs = normalizedPts
            val delta = if (audioLastPtsUs >= 0L) normalizedPts - audioLastPtsUs else 0L
            audioLastPtsUs = normalizedPts
            audioSamplesWrittenCount++
            Log.d("PixelRoxRecorder", "PTS_AUDIO first=$audioFirstPtsUs current=$normalizedPts delta=$delta")
            Log.d("PixelRoxRecorder", "MUX_PTS_AUDIO=$normalizedPts")
        }

        // Timestamp Sanity Guard
        val maxAllowedPts = wallDurationUs + 60_000_000L
        val finalPts = if (normalizedPts > maxAllowedPts || normalizedPts < 0L) {
            Log.e("PixelRoxRecorder", "MUX_PTS_SANITY_ERROR track=${if(isVideo) "VIDEO" else "AUDIO"} sourcePts=$sourcePts normalizedPts=$normalizedPts wallDurationUs=$wallDurationUs size=${bufferInfo.size} flags=${bufferInfo.flags}")
            maxOf(0L, minOf(normalizedPts, wallDurationUs))
        } else {
            normalizedPts
        }

        bufferInfo.presentationTimeUs = finalPts
        mediaMuxer!!.writeSampleData(trackIndex, byteBuffer, bufferInfo)
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Int {
        if (length <= 0) return 0
        var sumSq = 0L
        val shortCount = length / 2
        for (i in 0 until shortCount) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt() shl 8
            val s = (high or low).toShort()
            sumSq += s * s
        }
        return sqrt((sumSq / maxOf(shortCount, 1)).toDouble()).toInt()
    }

    private fun mixPcm16Bit(buf1: ByteArray, len1: Int, buf2: ByteArray, len2: Int, out: ByteArray) {
        val maxLen = maxOf(len1, len2)
        val shortCount = maxLen / 2
        for (i in 0 until shortCount) {
            val idx = i * 2
            val s1 = if (idx + 1 < len1) {
                val low = buf1[idx].toInt() and 0xFF
                val high = buf1[idx + 1].toInt() shl 8
                (high or low).toShort().toInt()
            } else 0

            val s2 = if (idx + 1 < len2) {
                val low = buf2[idx].toInt() and 0xFF
                val high = buf2[idx + 1].toInt() shl 8
                (high or low).toShort().toInt()
            } else 0

            val mixed = (s1 + s2).coerceIn(-32768, 32767).toShort()
            val lowOut = (mixed.toInt() and 0xFF).toByte()
            val highOut = ((mixed.toInt() shr 8) and 0xFF).toByte()

            if (idx < out.size) out[idx] = lowOut
            if (idx + 1 < out.size) out[idx + 1] = highOut
        }
    }

    @Synchronized
    private fun tryCheckAndStartMuxer() {
        if (isMuxerStarted || mediaMuxer == null) return

        val needsAudio = recordAudio
        val videoReady = (videoTrackIndex >= 0)
        val audioReady = (!needsAudio || audioTrackIndex >= 0)

        if (videoReady && audioReady) {
            mediaMuxer?.start()
            isMuxerStarted = true
            Log.d("PixelRoxRecorder", "MUXER started=true (videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex)")
            flushPreMuxerQueueLocked()
        }
    }

    @Synchronized
    private fun flushPreMuxerQueueLocked() {
        if (mediaMuxer == null || videoTrackIndex < 0) return
        for (sample in preMuxerVideoQueue) {
            try {
                writeNormalizedSample(videoTrackIndex, sample.byteBuffer, sample.bufferInfo, isVideo = true)
            } catch (e: Exception) {
                Log.e("PixelRoxRecorder", "Error writing pre-muxer video sample", e)
            }
        }
        preMuxerVideoQueue.clear()
    }

    @Synchronized
    fun save(): Uri? {
        if (isFinalizing) {
            Log.d("PixelRoxRecorder", "save() called but already finalizing")
            return null
        }
        if (!isRecording && virtualDisplay == null && videoEncoder == null) {
            Log.d("PixelRoxRecorder", "save() called but already inactive")
            return null
        }

        Log.d("PixelRoxRecorder", "SAVE_04 engine finalize start")
        Log.d("PixelRoxRecorder", "AUDIO_SAVE_01 capture stopping")
        isFinalizing = true
        isRecording = false

        // Stop audio capture job and audio records
        try {
            audioCaptureJob?.cancel()
        } catch (_: Exception) {}
        try {
            deviceAudioRecord?.stop()
            deviceAudioRecord?.release()
        } catch (_: Exception) {}
        deviceAudioRecord = null
        try {
            micAudioRecord?.stop()
            micAudioRecord?.release()
        } catch (_: Exception) {}
        micAudioRecord = null

        // Signal audio encoder EOS
        try {
            audioEncoder?.let { enc ->
                Log.d("PixelRoxRecorder", "AUDIO_SAVE_02 AAC EOS sent")
                val inputIndex = enc.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    enc.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "AUDIO_ERROR signaling audio EOS", e)
        }

        // Drain audio encoder
        if (recordAudio && audioEncoder != null) {
            val audioBufferInfo = MediaCodec.BufferInfo()
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 3000) {
                try {
                    val outIndex = audioEncoder!!.dequeueOutputBuffer(audioBufferInfo, 10000)
                    if (outIndex >= 0) {
                        val encodedData = audioEncoder!!.getOutputBuffer(outIndex)
                        if (encodedData != null && isMuxerStarted && mediaMuxer != null && audioTrackIndex >= 0) {
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && audioBufferInfo.size > 0) {
                                val clonedBuf = ByteBuffer.allocateDirect(audioBufferInfo.size)
                                encodedData.position(audioBufferInfo.offset)
                                encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                clonedBuf.put(encodedData)
                                clonedBuf.flip()

                                val clonedInfo = MediaCodec.BufferInfo().apply {
                                    set(0, audioBufferInfo.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                                }
                                writeNormalizedSample(audioTrackIndex, clonedBuf, clonedInfo, isVideo = false)
                                Log.d("PixelRoxRecorder", "AUDIO_SAVE_04 final audio sample written bytes=${audioBufferInfo.size}")
                            }
                        }
                        audioEncoder!!.releaseOutputBuffer(outIndex, false)
                        if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d("PixelRoxRecorder", "AUDIO_SAVE_03 AAC EOS observed")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "AUDIO_ERROR draining audio encoder", e)
                    break
                }
            }
        }
        Log.d("PixelRoxRecorder", "AUDIO_SAVE_05 audio finalized")

        try {
            videoEncoder?.signalEndOfInputStream()
            Log.d("PixelRoxRecorder", "SAVE_05 video EOS")
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR signaling video EOS", e)
        }

        // Drain remaining video frames safely with timeout
        val videoBufferInfo = MediaCodec.BufferInfo()
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 4000) {
            try {
                val encoder = videoEncoder ?: break
                val outputIndex = encoder.dequeueOutputBuffer(videoBufferInfo, 10000)
                if (outputIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null && isMuxerStarted && mediaMuxer != null && videoTrackIndex >= 0) {
                        flushPreMuxerQueueLocked()
                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoBufferInfo.size > 0) {
                            val clonedBuf = ByteBuffer.allocateDirect(videoBufferInfo.size)
                            encodedData.position(videoBufferInfo.offset)
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                            clonedBuf.put(encodedData)
                            clonedBuf.flip()

                            val clonedInfo = MediaCodec.BufferInfo().apply {
                                set(0, videoBufferInfo.size, videoBufferInfo.presentationTimeUs, videoBufferInfo.flags)
                            }
                            writeNormalizedSample(videoTrackIndex, clonedBuf, clonedInfo, isVideo = true)
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d("PixelRoxRecorder", "SAVE_06 video drain complete (EOS received)")
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!isMuxerStarted) break
                }
            } catch (e: Exception) {
                Log.e("PixelRoxRecorder", "SAVE_ERROR draining encoder", e)
                break
            }
        }
        Log.d("PixelRoxRecorder", "SAVE_07 audio drain complete/skipped")

        // Final MP4 Timeline & Physical Summary logging
        val wallDurationUs = (System.currentTimeMillis() - recordingStartMs) * 1000L
        val videoFirstWrittenPts = if (videoFirstPtsUs >= 0) videoFirstPtsUs else 0L
        val videoLastWrittenPts = if (videoLastPtsUs >= 0) videoLastPtsUs else 0L
        val videoDurationUs = videoLastWrittenPts - videoFirstWrittenPts

        val audioFirstWrittenPts = if (audioFirstPtsUs >= 0) audioFirstPtsUs else 0L
        val audioLastWrittenPts = if (audioLastPtsUs >= 0) audioLastPtsUs else 0L
        val audioDurationUs = audioLastWrittenPts - audioFirstWrittenPts

        val maxMuxedPtsUs = maxOf(videoLastWrittenPts, audioLastWrittenPts)

        Log.d("PixelRoxRecorder", "FINAL_MP4_TIMELINE wallDurationUs=$wallDurationUs videoFirstWrittenPtsUs=$videoFirstWrittenPts videoLastWrittenPtsUs=$videoLastWrittenPts videoDurationUs=$videoDurationUs audioFirstWrittenPtsUs=$audioFirstWrittenPts audioLastWrittenPtsUs=$audioLastWrittenPts audioDurationUs=$audioDurationUs maxMuxedPtsUs=$maxMuxedPtsUs")

        Log.d("PixelRoxRecorder", "PTS_SUMMARY videoFirstUs=$videoFirstWrittenPts videoLastUs=$videoLastWrittenPts videoDurationUs=$videoDurationUs")
        Log.d("PixelRoxRecorder", "PTS_SUMMARY audioFirstUs=$audioFirstWrittenPts audioLastUs=$audioLastWrittenPts audioDurationUs=$audioDurationUs")
        Log.d("PixelRoxRecorder", "VIDEO framesEncoded=$videoFramesEncodedCount samplesWritten=$videoSamplesWrittenCount")
        Log.d("PixelRoxRecorder", "AUDIO samplesWritten=$audioSamplesWrittenCount")
        Log.d("PixelRoxRecorder", "MUX_SUMMARY recordingWallDurationMs=${wallDurationUs / 1000} videoDurationUs=$videoDurationUs audioDurationUs=$audioDurationUs videoSamples=$videoSamplesWrittenCount audioSamples=$audioSamplesWrittenCount")

        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY wallDurationMs=${wallDurationUs / 1000}")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY videoFirstMuxPtsUs=$videoFirstWrittenPts")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY videoLastMuxPtsUs=$videoLastWrittenPts")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY videoDurationUs=$videoDurationUs")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY audioFirstMuxPtsUs=$audioFirstWrittenPts")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY audioLastMuxPtsUs=$audioLastWrittenPts")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY audioDurationUs=$audioDurationUs")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY devicePcmBytes=$devicePcmBytesTotal")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY deviceNonZeroSamples=$deviceNonZeroSamplesTotal")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY devicePeak=$devicePeakSample")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY videoSamplesWritten=$videoSamplesWrittenCount")
        Log.d("PixelRoxRecorder", "PHYSICAL_SUMMARY audioSamplesWritten=$audioSamplesWrittenCount")

        releaseResources()

        val rawFile = tempOutputFile
        if (rawFile != null && rawFile.exists() && rawFile.length() > 0) {
            Log.d("PixelRoxRecorder", "Raw file exists: ${rawFile.absolutePath}, size: ${rawFile.length()}")
            val savedUri = publishToMediaStore(rawFile)
            if (savedUri != null) {
                // Verify saved MP4 duration via MediaMetadataRetriever
                var retrievedDurationMs = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, savedUri)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retrievedDurationMs = durStr?.toLongOrNull() ?: 0L
                    retriever.release()
                    Log.d("PixelRoxRecorder", "SAVED_MP4_DURATION_MS=$retrievedDurationMs")
                } catch (e: Exception) {
                    Log.e("PixelRoxRecorder", "Error retrieving saved MP4 duration", e)
                    Log.d("PixelRoxRecorder", "SAVED_MP4_DURATION_INVALID")
                }
                if (retrievedDurationMs <= 0 || retrievedDurationMs > 3600_000L) {
                    Log.d("PixelRoxRecorder", "SAVED_MP4_DURATION_INVALID")
                }

                Log.d("PixelRoxRecorder", "SAVE_12 save complete")
                return savedUri
            }
        } else {
            Log.e("PixelRoxRecorder", "SAVE_ERROR: Raw temp file is null or empty")
        }
        return null
    }

    @Synchronized
    fun cancel() {
        if (isFinalizing) return
        isFinalizing = true
        isRecording = false

        try {
            audioCaptureJob?.cancel()
        } catch (_: Exception) {}

        try {
            deviceAudioRecord?.stop()
            deviceAudioRecord?.release()
        } catch (_: Exception) {}
        deviceAudioRecord = null

        try {
            micAudioRecord?.stop()
            micAudioRecord?.release()
        } catch (_: Exception) {}
        micAudioRecord = null

        releaseResources()

        tempOutputFile?.let {
            if (it.exists()) it.delete()
        }
        tempOutputFile = null
        Log.d("PixelRoxRecorder", "Recording cancelled and temp file deleted")
    }

    private fun releaseResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            videoEncoder?.stop()
            videoEncoder?.release()
        } catch (_: Exception) {}
        videoEncoder = null

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (_: Exception) {}
        audioEncoder = null

        try {
            if (isMuxerStarted && mediaMuxer != null) {
                mediaMuxer?.stop()
                Log.d("PixelRoxRecorder", "SAVE_08 muxer stopped")
            }
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR stopping muxer", e)
        }

        try {
            mediaMuxer?.release()
            Log.d("PixelRoxRecorder", "SAVE_09 muxer released")
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR releasing muxer", e)
        }
        mediaMuxer = null

        try {
            mediaProjection.registerCallback(projectionCallback, null)
        } catch (_: Exception) {}

        scope.cancel()
    }

    private fun publishToMediaStore(source: File): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
        val displayName = "ScreenRecord_$dateStr.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, timestamp / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PixelRox/Screen Recordings")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
        if (uri == null) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR: MediaStore insert returned null URI")
            source.delete()
            return null
        }

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Failed to open output stream for MediaStore Uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Log.d("PixelRoxRecorder", "SAVE_10 MediaStore finalized")

            var verified = false
            resolver.query(uri, arrayOf(MediaStore.Video.Media._ID), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    verified = true
                }
            }

            if (verified) {
                Log.d("PixelRoxRecorder", "SAVE_11 URI verified=true, uri=$uri")
            } else {
                Log.e("PixelRoxRecorder", "SAVE_11 URI verified=false, uri=$uri")
            }

            source.delete()
            return uri
        } catch (e: Exception) {
            Log.e("PixelRoxRecorder", "SAVE_ERROR in publishToMediaStore", e)
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            source.delete()
            return null
        }
    }
}
