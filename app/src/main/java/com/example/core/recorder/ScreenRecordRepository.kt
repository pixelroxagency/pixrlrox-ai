package com.example.core.recorder

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScreenRecordItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val dateModified: Long,
    val size: Long
)

object ScreenRecordRepository {

    suspend fun queryRecordings(context: Context): List<ScreenRecordItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreenRecordItem>()
        try {
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.RELATIVE_PATH
            )
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            }
            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%Screen Recordings%")
            } else {
                arrayOf("%ScreenRecord_%")
            }

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val name = cursor.getString(nameCol) ?: "ScreenRecord.mp4"
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    list.add(
                        ScreenRecordItem(
                            id = id,
                            uri = uri,
                            name = name,
                            duration = duration,
                            dateModified = dateModified,
                            size = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenRecordRepository", "Error querying screen recordings", e)
        }
        list
    }

    fun deleteRecording(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e("ScreenRecordRepository", "Error deleting recording", e)
            false
        }
    }

    fun renameRecording(context: Context, uri: Uri, newName: String): Boolean {
        return try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            Log.e("ScreenRecordRepository", "Error renaming recording", e)
            false
        }
    }
}
