package com.example.ui.screens.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.ImageExportResult
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfPublishResult
import com.example.core.pdf.PdfSignatureEngine
import com.example.core.pdf.SignatureStampConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SignatureStroke(
    val points: List<Pair<Float, Float>>,
    val color: Int,
    val strokeWidth: Float
)

data class PdfSignatureUiState(
    val strokes: List<SignatureStroke> = emptyList(),
    val currentColor: Int = Color.BLACK,
    val currentStrokeWidth: Float = 6f,
    val isPdfPlacementMode: Boolean = false,
    val targetPdfUri: Uri? = null,
    val targetPdfName: String? = null,
    val targetPdfPageCount: Int = 0,
    val selectedPageIndex: Int = 0,
    val pagePreviewBitmap: Bitmap? = null,
    val stampNormalizedX: Float = 0.5f,
    val stampNormalizedY: Float = 0.7f,
    val stampNormalizedWidth: Float = 0.4f,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val signaturePngResult: ImageExportResult.Success? = null,
    val signedPdfResult: PdfPublishResult.Success? = null,
    val errorMessage: String? = null
)

class PdfSignatureViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfSignatureEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfSignatureUiState())
    val uiState: StateFlow<PdfSignatureUiState> = _uiState.asStateFlow()

    fun addStroke(stroke: SignatureStroke) {
        _uiState.update {
            it.copy(
                strokes = it.strokes + stroke,
                signaturePngResult = null,
                signedPdfResult = null,
                errorMessage = null
            )
        }
    }

    fun undoStroke() {
        if (_uiState.value.strokes.isNotEmpty()) {
            _uiState.update {
                it.copy(strokes = it.strokes.dropLast(1))
            }
        }
    }

    fun clearSignature() {
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                signaturePngResult = null,
                signedPdfResult = null
            )
        }
    }

    fun setStrokeColor(color: Int) {
        _uiState.update { it.copy(currentColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(currentStrokeWidth = width) }
    }

    fun togglePlacementMode(enabled: Boolean) {
        _uiState.update { it.copy(isPdfPlacementMode = enabled) }
    }

    fun selectTargetPdf(uri: Uri) {
        val name = uri.lastPathSegment ?: "document.pdf"
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Loading PDF preview...",
                    errorMessage = null
                )
            }

            try {
                val pfd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Could not open PDF")

                var pageCount = 0
                var previewBmp: Bitmap? = null

                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try {
                        pageCount = renderer.pageCount
                        if (pageCount > 0) {
                            val page = renderer.openPage(0)
                            try {
                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                previewBmp = bmp
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                }

                _uiState.update {
                    it.copy(
                        targetPdfUri = uri,
                        targetPdfName = name,
                        targetPdfPageCount = pageCount,
                        selectedPageIndex = 0,
                        pagePreviewBitmap = previewBmp,
                        isPdfPlacementMode = true,
                        isProcessing = false,
                        statusMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = "Failed to load PDF: ${e.message}"
                    )
                }
            }
        }
    }

    fun setPlacementPage(pageIndex: Int) {
        val uri = _uiState.value.targetPdfUri ?: return
        if (pageIndex !in 0 until _uiState.value.targetPdfPageCount) return

        viewModelScope.launch {
            try {
                val pfd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                var previewBmp: Bitmap? = null
                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try {
                        val page = renderer.openPage(pageIndex)
                        try {
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            previewBmp = bmp
                        } finally {
                            page.close()
                        }
                    } finally {
                        renderer.close()
                    }
                }
                _uiState.update {
                    it.copy(
                        selectedPageIndex = pageIndex,
                        pagePreviewBitmap = previewBmp
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateStampPosition(normX: Float, normY: Float, normWidth: Float) {
        _uiState.update {
            it.copy(
                stampNormalizedX = normX.coerceIn(0f, 0.9f),
                stampNormalizedY = normY.coerceIn(0f, 0.9f),
                stampNormalizedWidth = normWidth.coerceIn(0.1f, 0.8f)
            )
        }
    }

    fun generateSignatureBitmap(width: Int = 800, height: Int = 400): Bitmap? {
        val strokes = _uiState.value.strokes
        if (strokes.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)

        for (stroke in strokes) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                strokeWidth = stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = Path()
            if (stroke.points.isNotEmpty()) {
                path.moveTo(stroke.points[0].first, stroke.points[0].second)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].first, stroke.points[i].second)
                }
                canvas.drawPath(path, paint)
            }
        }

        return bitmap
    }

    fun exportSignaturePng() {
        val bmp = generateSignatureBitmap()
        if (bmp == null) {
            _uiState.update { it.copy(errorMessage = "Please draw your signature first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Exporting signature PNG to Pictures/PixelRox...",
                    errorMessage = null,
                    signaturePngResult = null
                )
            }

            try {
                val pubResult = publisher.publishImage(
                    bitmap = bmp,
                    desiredDisplayName = "Signature",
                    format = Bitmap.CompressFormat.PNG
                )
                bmp.recycle()

                when (pubResult) {
                    is ImageExportResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                signaturePngResult = pubResult
                            )
                        }
                    }
                    is ImageExportResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                errorMessage = pubResult.errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to save signature"
                    )
                }
            }
        }
    }

    fun applySignatureToPdf() {
        val pdfUri = _uiState.value.targetPdfUri ?: return
        val bmp = generateSignatureBitmap()
        if (bmp == null) {
            _uiState.update { it.copy(errorMessage = "Please draw a signature before applying.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Stamping signature onto PDF page...",
                    errorMessage = null,
                    signedPdfResult = null
                )
            }

            try {
                val config = SignatureStampConfig(
                    pageIndex = _uiState.value.selectedPageIndex,
                    normalizedX = _uiState.value.stampNormalizedX,
                    normalizedY = _uiState.value.stampNormalizedY,
                    normalizedWidth = _uiState.value.stampNormalizedWidth
                )

                val signedFile = engine.applySignatureToPdf(
                    pdfUri = pdfUri,
                    signatureBitmap = bmp,
                    config = config
                )
                bmp.recycle()

                _uiState.update { it.copy(statusMessage = "Saving signed PDF to Documents/PixelRox...") }
                val baseName = (_uiState.value.targetPdfName ?: "document").substringBeforeLast(".")
                val pubResult = publisher.publishPdf(signedFile, "${baseName}_signed")
                signedFile.delete()

                when (pubResult) {
                    is PdfPublishResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                signedPdfResult = pubResult
                            )
                        }
                    }
                    is PdfPublishResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                errorMessage = pubResult.errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to apply signature to PDF"
                    )
                }
            }
        }
    }
}
