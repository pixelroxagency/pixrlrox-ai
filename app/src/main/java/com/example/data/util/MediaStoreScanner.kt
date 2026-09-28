package com.example.data.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.core.database.entity.media.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"

    suspend fun queryDeviceVideos(context: Context): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItemEntity>()
        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                videoProjection.add(MediaStore.Video.Media.WIDTH)
                videoProjection.add(MediaStore.Video.Media.HEIGHT)
            }

            val videoSortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC, ${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                videoUri,
                videoProjection.toTypedArray(),
                null,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dateModCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dateAddCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri: Uri = ContentUris.withAppendedId(videoUri, id)
                    val name = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val rawName = title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Video $id"
                    val displayName = cleanMediaDisplayName(rawName)
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateModified = if (dateModCol != -1 && cursor.getLong(dateModCol) > 0) {
                        cursor.getLong(dateModCol) * 1000L
                    } else if (dateAddCol != -1 && cursor.getLong(dateAddCol) > 0) {
                        cursor.getLong(dateAddCol) * 1000L
                    } else {
                        System.currentTimeMillis()
                    }

                    result.add(
                        MediaItemEntity(
                            id = "mediastore_video_$id",
                            uri = contentUri.toString(),
                            title = displayName,
                            duration = duration,
                            lastPlayed = dateModified,
                            lastPosition = 0L,
                            completed = false,
                            mediaType = "VIDEO"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore videos", e)
        }
        result
    }

    suspend fun queryDeviceAudio(context: Context): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItemEntity>()
        try {
            val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE
            )
            val audioSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_PODCAST} != 0 OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} != 0"
            val audioSortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC, ${MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                audioUri,
                audioProjection,
                audioSelection,
                null,
                audioSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dateModCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val dateAddCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri: Uri = ContentUris.withAppendedId(audioUri, id)
                    val name = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val artist = if (artistCol != -1) cursor.getString(artistCol) else null

                    val rawTitle = title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Audio $id"
                    val cleanTitle = cleanMediaDisplayName(rawTitle)
                    val displayName = if (!artist.isNullOrBlank() && artist != "<unknown>") {
                        "$cleanTitle - $artist"
                    } else {
                        cleanTitle
                    }

                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateModified = if (dateModCol != -1 && cursor.getLong(dateModCol) > 0) {
                        cursor.getLong(dateModCol) * 1000L
                    } else if (dateAddCol != -1 && cursor.getLong(dateAddCol) > 0) {
                        cursor.getLong(dateAddCol) * 1000L
                    } else {
                        System.currentTimeMillis()
                    }

                    result.add(
                        MediaItemEntity(
                            id = "mediastore_audio_$id",
                            uri = contentUri.toString(),
                            title = displayName,
                            duration = duration,
                            lastPlayed = dateModified,
                            lastPosition = 0L,
                            completed = false,
                            mediaType = "AUDIO"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore audio", e)
        }
        result
    }

    suspend fun queryDeviceImages(context: Context): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItemEntity>()
        try {
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageProjection = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageProjection.add(MediaStore.Images.Media.WIDTH)
                imageProjection.add(MediaStore.Images.Media.HEIGHT)
            }

            val imageSortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                imageUri,
                imageProjection.toTypedArray(),
                null,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateModCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dateAddCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri: Uri = ContentUris.withAppendedId(imageUri, id)
                    val name = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = if (titleCol != -1) cursor.getString(titleCol) else null
                    val rawName = title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Image $id"
                    val displayName = cleanMediaDisplayName(rawName)
                    val sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dateModified = if (dateModCol != -1 && cursor.getLong(dateModCol) > 0) {
                        cursor.getLong(dateModCol) * 1000L
                    } else if (dateAddCol != -1 && cursor.getLong(dateAddCol) > 0) {
                        cursor.getLong(dateAddCol) * 1000L
                    } else {
                        System.currentTimeMillis()
                    }

                    result.add(
                        MediaItemEntity(
                            id = "mediastore_image_$id",
                            uri = contentUri.toString(),
                            title = displayName,
                            duration = sizeBytes,
                            lastPlayed = dateModified,
                            lastPosition = 0L,
                            completed = false,
                            mediaType = "IMAGE"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore images", e)
        }
        result
    }

    suspend fun queryDeviceMedia(context: Context): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val videos = queryDeviceVideos(context)
        val audios = queryDeviceAudio(context)
        val images = queryDeviceImages(context)
        val combined = mutableListOf<MediaItemEntity>()
        combined.addAll(videos)
        combined.addAll(audios)
        combined.addAll(images)
        combined.sortedByDescending { it.lastPlayed }
    }

    private fun cleanMediaDisplayName(name: String): String {
        return name.substringBeforeLast(".")
    }
}
