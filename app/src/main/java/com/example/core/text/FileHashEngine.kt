package com.example.core.text

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class FileHashOutcome(
    val fileName: String,
    val fileSizeBytes: Long,
    val algorithm: HashAlgorithm,
    val calculatedHex: String,
    val expectedHexInput: String? = null,
    val isMatch: Boolean? = null
)

object FileHashEngine {

    suspend fun computeFileHash(
        context: Context,
        uri: Uri,
        algorithm: HashAlgorithm,
        expectedHash: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): FileHashOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = "file"
        var totalSize = -1L

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx != -1) totalSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val digest = MessageDigest.getInstance(algorithm.algorithmName)
        var bytesReadTotal = 0L

        resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(16384)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                coroutineContext.ensureActive()
                digest.update(buffer, 0, read)
                bytesReadTotal += read
                if (totalSize > 0) {
                    onProgress(bytesReadTotal, totalSize)
                }
            }
        } ?: throw IllegalStateException("Could not open file stream")

        val actualSizeBytes = if (totalSize > 0) totalSize else bytesReadTotal
        val calculatedHex = HashEngine.bytesToHex(digest.digest())

        val match = if (!expectedHash.isNullOrBlank()) {
            normalizeHash(expectedHash) == calculatedHex
        } else {
            null
        }

        FileHashOutcome(
            fileName = fileName,
            fileSizeBytes = actualSizeBytes,
            algorithm = algorithm,
            calculatedHex = calculatedHex,
            expectedHexInput = expectedHash,
            isMatch = match
        )
    }

    fun normalizeHash(raw: String): String {
        return raw.trim().lowercase().replace(Regex("[^0-9a-f]"), "")
    }
}
