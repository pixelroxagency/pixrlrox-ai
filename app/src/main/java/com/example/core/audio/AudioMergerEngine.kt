package com.example.core.audio

import android.content.Context
import android.net.Uri
import android.os.Looper
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class AudioMergeTrack(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioCodec: String,
    val sampleRate: Int,
    val channelCount: Int
)

sealed interface AudioMergerResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val trackCount: Int
    ) : AudioMergerResult

    data class Failure(val errorMessage: String, val cause: Throwable? = null) : AudioMergerResult
    data object Cancelled : AudioMergerResult
}

@OptIn(UnstableApi::class)
class AudioMergerEngine(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)
    private val activeTransformer = AtomicReference<Transformer?>(null)
    private val mp3Encoder = Mp3AudioEncoder(context)

    fun cancel() {
        isCancelled.set(true)
        activeTransformer.getAndSet(null)?.cancel()
    }

    suspend fun inspectTracks(uris: List<Uri>): List<AudioMergeTrack> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            try {
                val metadata = AudioMetadata.extract(context, uri)
                if (metadata.hasAudioTrack && metadata.durationMs > 0L) {
                    AudioMergeTrack(
                        uri = uri,
                        displayName = metadata.displayName,
                        durationMs = metadata.durationMs,
                        fileSizeBytes = metadata.fileSizeBytes,
                        audioCodec = metadata.audioCodec,
                        sampleRate = metadata.sampleRate,
                        channelCount = metadata.channelCount
                    )
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun mergeAudio(
        tracks: List<AudioMergeTrack>,
        targetFormat: AudioOutputFormat = AudioOutputFormat.M4A,
        bitrateKbps: Int = 192,
        onProgress: (Int) -> Unit
    ): AudioMergerResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)

        if (tracks.size < 2) {
            return@withContext AudioMergerResult.Failure("At least 2 valid audio tracks are required to merge.")
        }

        // Validate all tracks
        for ((index, track) in tracks.withIndex()) {
            if (track.durationMs <= 0L) {
                return@withContext AudioMergerResult.Failure("Track #${index + 1} (${track.displayName}) is corrupted or has 0 duration.")
            }
        }

        val totalExpectedDurationMs = tracks.sumOf { it.durationMs }
        val tempDir = File(context.cacheDir, "merged_audio").apply { mkdirs() }
        val tempM4aFile = File(tempDir, "merge_seq_${UUID.randomUUID().toString().take(8)}.m4a")
        if (tempM4aFile.exists()) tempM4aFile.delete()

        val m4aSuccess = mergeSequenceToM4a(tracks.map { it.uri }, tempM4aFile, onProgress)

        if (isCancelled.get()) {
            if (tempM4aFile.exists()) tempM4aFile.delete()
            return@withContext AudioMergerResult.Cancelled
        }

        if (!m4aSuccess || !tempM4aFile.exists() || tempM4aFile.length() <= 0L) {
            if (tempM4aFile.exists()) tempM4aFile.delete()
            return@withContext AudioMergerResult.Failure("Failed to sequence and merge audio tracks.")
        }

        if (targetFormat == AudioOutputFormat.M4A) {
            onProgress(100)
            return@withContext AudioMergerResult.Success(
                tempFile = tempM4aFile,
                durationMs = totalExpectedDurationMs,
                fileSizeBytes = tempM4aFile.length(),
                format = AudioOutputFormat.M4A,
                trackCount = tracks.size
            )
        }

        // Transcode intermediate merged M4A to MP3 or WAV
        val finalOutputFile = File(tempDir, "merge_${UUID.randomUUID().toString().take(8)}${targetFormat.extension}")
        val transcodeSuccess = if (targetFormat == AudioOutputFormat.MP3) {
            mp3Encoder.encodeVideoToMp3(
                sourceUri = Uri.fromFile(tempM4aFile),
                outputFile = finalOutputFile,
                bitrateKbps = bitrateKbps,
                isCancelled = { isCancelled.get() },
                onProgress = { p -> onProgress((90 + (p * 0.1f)).toInt().coerceIn(90, 99)) }
            )
        } else {
            // WAV
            val converterEngine = AudioConverterEngine(context)
            val convResult = converterEngine.convertAudio(
                sourceUri = Uri.fromFile(tempM4aFile),
                targetFormat = AudioOutputFormat.WAV,
                onProgress = { p -> onProgress((90 + (p * 0.1f)).toInt().coerceIn(90, 99)) }
            )
            if (convResult is AudioConverterResult.Success) {
                convResult.tempFile.copyTo(finalOutputFile, overwrite = true)
                try { convResult.tempFile.delete() } catch (_: Exception) {}
                true
            } else false
        }

        try { tempM4aFile.delete() } catch (_: Exception) {}

        if (isCancelled.get()) {
            if (finalOutputFile.exists()) finalOutputFile.delete()
            return@withContext AudioMergerResult.Cancelled
        }

        if (!transcodeSuccess || !finalOutputFile.exists() || finalOutputFile.length() <= 0L) {
            if (finalOutputFile.exists()) finalOutputFile.delete()
            return@withContext AudioMergerResult.Failure("Failed to transcode merged audio to ${targetFormat.title}")
        }

        onProgress(100)
        return@withContext AudioMergerResult.Success(
            tempFile = finalOutputFile,
            durationMs = totalExpectedDurationMs,
            fileSizeBytes = finalOutputFile.length(),
            format = targetFormat,
            trackCount = tracks.size
        )
    }

    private suspend fun mergeSequenceToM4a(
        sourceUris: List<Uri>,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Boolean {
        return try {
            val signal = withContext(Dispatchers.Main.immediate) {
                val editedItems = sourceUris.map { uri ->
                    val mediaItem = MediaItem.fromUri(uri)
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .build()
                }

                val sequence = EditedMediaItemSequence(editedItems)
                val composition = Composition.Builder(sequence).build()

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
            signal
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                activeTransformer.getAndSet(null)?.cancel()
            }
            false
        } catch (_: Exception) {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                activeTransformer.getAndSet(null)?.cancel()
            }
            false
        }
    }
}
