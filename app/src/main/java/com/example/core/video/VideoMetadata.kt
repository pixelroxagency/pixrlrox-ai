package com.example.core.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

data class VideoMetadata(
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrate: Long
) {
    companion object {
        fun extract(context: Context, uri: Uri): VideoMetadata {
            val contentResolver = context.contentResolver
            var displayName = "video_" + System.currentTimeMillis() + ".mp4"
            var mimeType = contentResolver.getType(uri) ?: "video/mp4"
            var fileSizeBytes = 0L

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
            var bitrate = 0L

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

                val bitStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                bitrate = bitStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                // fallback defaults
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            return VideoMetadata(
                contentUri = uri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                durationMs = durationMs,
                width = width,
                height = height,
                bitrate = bitrate
            )
        }
    }
}
