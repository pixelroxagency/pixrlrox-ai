package com.example.core.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

data class AudioMetadata(
    val contentUri: String,
    val displayName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasAudioTrack: Boolean,
    val audioCodec: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Long,
    val canDirectRemux: Boolean
) {
    companion object {
        fun extract(context: Context, uri: Uri): AudioMetadata {
            val contentResolver = context.contentResolver
            var displayName = "video_${System.currentTimeMillis()}.mp4"
            var fileSizeBytes = 0L

            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) displayName = name
                        }
                        if (sizeIndex != -1) {
                            fileSizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (fileSizeBytes == 0L) {
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSizeBytes = pfd.statSize
                    }
                } catch (_: Exception) {}
            }

            var durationMs = 0L
            var width = 0
            var height = 0
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L

                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                width = wStr?.toIntOrNull() ?: 0
                height = hStr?.toIntOrNull() ?: 0

                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotation = rotStr?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val temp = width
                    width = height
                    height = temp
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }

            var hasAudioTrack = false
            var audioCodec = "None"
            var sampleRate = 0
            var channelCount = 0
            var bitrate = 0L
            var canDirectRemux = false

            val extractor = MediaExtractor()
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                    val trackCount = extractor.trackCount
                    for (i in 0 until trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            hasAudioTrack = true
                            audioCodec = formatAudioCodecName(mime)

                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                                bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                            }

                            // AAC or AMR tracks in MP4 container can usually be directly remuxed into M4A
                            canDirectRemux = mime.contains("mp4a") || mime.contains("aac") || mime.contains("3gpp") || mime.contains("amr")
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

            return AudioMetadata(
                contentUri = uri.toString(),
                displayName = displayName,
                fileSizeBytes = fileSizeBytes,
                durationMs = durationMs,
                width = width,
                height = height,
                hasAudioTrack = hasAudioTrack,
                audioCodec = audioCodec,
                sampleRate = sampleRate,
                channelCount = channelCount,
                bitrate = bitrate,
                canDirectRemux = canDirectRemux
            )
        }

        private fun formatAudioCodecName(mime: String): String {
            return when {
                mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
                mime.contains("opus") -> "Opus"
                mime.contains("vorbis") -> "Vorbis"
                mime.contains("flac") -> "FLAC"
                mime.contains("ac3") -> "AC3"
                mime.contains("3gpp") || mime.contains("amr") -> "AMR"
                else -> mime.removePrefix("audio/")
            }
        }
    }
}
