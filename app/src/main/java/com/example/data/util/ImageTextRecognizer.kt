package com.example.data.util

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class RecognizedTextRegion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val boundingBox: Rect
)

data class RecognizedImageResult(
    val fullText: String,
    val regions: List<RecognizedTextRegion>
)

open class ImageTextRecognizer(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    open suspend fun recognizeText(imageUri: Uri): Text {
        val image = InputImage.fromFilePath(context, imageUri)
        return recognizer.process(image).await()
    }

    open suspend fun recognizeStructuredText(imageUri: Uri): RecognizedImageResult {
        val result = recognizeText(imageUri)
        val regions = mutableListOf<RecognizedTextRegion>()
        for (block in result.textBlocks) {
            val box = block.boundingBox
            if (box != null && block.text.isNotBlank()) {
                regions.add(RecognizedTextRegion(text = block.text, boundingBox = box))
            }
        }
        return RecognizedImageResult(
            fullText = result.text,
            regions = regions
        )
    }
}
