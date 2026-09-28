package com.example.ui.screens.image

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.image.OutputFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoResizerCropperScreen(
    viewModel: PhotoResizerCropperViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImage(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Resizer & Cropper") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Select Photo")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading || uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.isLoading) "Loading image..." else "Processing crop & resize...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (viewModel.loadedBitmap == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Crop & Resize Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select a photo from your device. You can crop to exact aspect ratios, resize with aspect-ratio locking and no-upscale protection, and export to JPEG, PNG, or WebP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Photo")
                        }
                    }
                }
            } else if (viewModel.processedBitmap != null) {
                // Processed Preview Mode
                val processed = viewModel.processedBitmap!!
                Text(
                    text = "Processed Result Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = processed.asImageBitmap(),
                        contentDescription = "Processed photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Image Specifications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Original: ${uiState.originalWidth} × ${uiState.originalHeight} px")
                        Text("• Cropped: ${uiState.croppedWidth} × ${uiState.croppedHeight} px")
                        Text("• Output: ${processed.width} × ${processed.height} px")
                        Text("• Format: ${uiState.outputFormat.title} (${uiState.outputFormat.extension})")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.exportResult != null) {
                    val export = uiState.exportResult!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved to Pictures/PixelRox",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "File: ${export.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val kb = export.fileSizeBytes / 1024
                            Text(
                                text = "Size: $kb KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = export.mimeType
                                    putExtra(Intent.EXTRA_STREAM, export.contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        OutlinedButton(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Another")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { viewModel.clearProcessed() }
                    ) {
                        Text("Back to Adjustments")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportProcessedImage(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export to Device")
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearProcessed() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back to Adjust")
                        }
                    }
                }
            } else {
                // Editing & Crop Adjustments Mode
                val bmp = viewModel.loadedBitmap!!

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Original: ${uiState.originalWidth} × ${uiState.originalHeight} px",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text("Change Photo")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Crop Canvas Area
                CropEditorCanvas(
                    bitmap = bmp,
                    cropRectNorm = uiState.cropRectNorm,
                    onCropRectChanged = { l, t, r, b ->
                        viewModel.updateCropRectNorm(l, t, r, b)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Crop Ratio Selector Chips
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = uiState.cropRatio == ratio,
                            onClick = { viewModel.setCropRatio(ratio) },
                            label = { Text(ratio.title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resize Parameters
                Text(
                    text = "Target Output Dimensions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = if (uiState.targetWidth > 0) uiState.targetWidth.toString() else "",
                        onValueChange = { viewModel.updateTargetWidth(it) },
                        label = { Text("Width (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = if (uiState.targetHeight > 0) uiState.targetHeight.toString() else "",
                        onValueChange = { viewModel.updateTargetHeight(it) },
                        label = { Text("Height (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggles: Lock Aspect Ratio & No Upscale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock Aspect Ratio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Maintain proportional width and height", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.lockAspectRatio,
                        onCheckedChange = { viewModel.setLockAspectRatio(it) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("No Upscale", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Prevent stretching beyond cropped source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.noUpscale,
                        onCheckedChange = { viewModel.setNoUpscale(it) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format & Quality
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutputFormat.entries.forEach { format ->
                        FilterChip(
                            selected = uiState.outputFormat == format,
                            onClick = { viewModel.setOutputFormat(format) },
                            label = { Text(format.title) }
                        )
                    }
                }

                if (uiState.outputFormat.supportsQuality) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quality: ${uiState.quality}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Slider(
                        value = uiState.quality.toFloat(),
                        onValueChange = { viewModel.setQuality(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 18
                    )
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.applyCropAndResize() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.targetWidth > 0 && uiState.targetHeight > 0
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Crop & Resize")
                }
            }
        }
    }
}

@Composable
fun CropEditorCanvas(
    bitmap: Bitmap,
    cropRectNorm: RectF,
    onCropRectChanged: (left: Float, top: Float, right: Float, bottom: Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerAspect = containerWidth / containerHeight

        val (displayedW, displayedH) = if (imgAspect > containerAspect) {
            Pair(containerWidth, containerWidth / imgAspect)
        } else {
            Pair(containerHeight * imgAspect, containerHeight)
        }

        val offsetX = (containerWidth - displayedW) / 2f
        val offsetY = (containerHeight - displayedH) / 2f

        // Display base image
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image for cropping",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Overlay Interactive Scrim & Crop Box
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayedW, displayedH) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaNormX = dragAmount.x / displayedW
                        val deltaNormY = dragAmount.y / displayedH

                        val touchX = (change.position.x - offsetX) / displayedW
                        val touchY = (change.position.y - offsetY) / displayedH

                        // Adjust crop rect based on drag location
                        val cL = cropRectNorm.left
                        val cT = cropRectNorm.top
                        val cR = cropRectNorm.right
                        val cB = cropRectNorm.bottom

                        val nearLeft = kotlin.math.abs(touchX - cL) < 0.15f
                        val nearRight = kotlin.math.abs(touchX - cR) < 0.15f
                        val nearTop = kotlin.math.abs(touchY - cT) < 0.15f
                        val nearBottom = kotlin.math.abs(touchY - cB) < 0.15f

                        if (nearLeft && nearTop) {
                            onCropRectChanged(cL + deltaNormX, cT + deltaNormY, cR, cB)
                        } else if (nearRight && nearBottom) {
                            onCropRectChanged(cL, cT, cR + deltaNormX, cB + deltaNormY)
                        } else if (nearLeft) {
                            onCropRectChanged(cL + deltaNormX, cT, cR, cB)
                        } else if (nearRight) {
                            onCropRectChanged(cL, cT, cR + deltaNormX, cB)
                        } else if (nearTop) {
                            onCropRectChanged(cL, cT + deltaNormY, cR, cB)
                        } else if (nearBottom) {
                            onCropRectChanged(cL, cT, cR, cB + deltaNormY)
                        } else {
                            // Translate whole box
                            val width = cR - cL
                            val height = cB - cT
                            val newL = (cL + deltaNormX).coerceIn(0f, 1f - width)
                            val newT = (cT + deltaNormY).coerceIn(0f, 1f - height)
                            onCropRectChanged(newL, newT, newL + width, newT + height)
                        }
                    }
                }
        ) {
            val cropPixelLeft = offsetX + cropRectNorm.left * displayedW
            val cropPixelTop = offsetY + cropRectNorm.top * displayedH
            val cropPixelWidth = (cropRectNorm.right - cropRectNorm.left) * displayedW
            val cropPixelHeight = (cropRectNorm.bottom - cropRectNorm.top) * displayedH

            // 1. Draw darkened scrim outside crop
            // Top rect
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, cropPixelTop)
            )
            // Bottom rect
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, cropPixelTop + cropPixelHeight),
                size = Size(size.width, size.height - (cropPixelTop + cropPixelHeight))
            )
            // Left rect
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, cropPixelTop),
                size = Size(cropPixelLeft, cropPixelHeight)
            )
            // Right rect
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(cropPixelLeft + cropPixelWidth, cropPixelTop),
                size = Size(size.width - (cropPixelLeft + cropPixelWidth), cropPixelHeight)
            )

            // 2. Draw Crop Border
            drawRect(
                color = Color.White,
                topLeft = Offset(cropPixelLeft, cropPixelTop),
                size = Size(cropPixelWidth, cropPixelHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Rule of Thirds grid lines
            val thirdW = cropPixelWidth / 3f
            val thirdH = cropPixelHeight / 3f

            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cropPixelLeft + thirdW, cropPixelTop),
                end = Offset(cropPixelLeft + thirdW, cropPixelTop + cropPixelHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cropPixelLeft + 2 * thirdW, cropPixelTop),
                end = Offset(cropPixelLeft + 2 * thirdW, cropPixelTop + cropPixelHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cropPixelLeft, cropPixelTop + thirdH),
                end = Offset(cropPixelLeft + cropPixelWidth, cropPixelTop + thirdH),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cropPixelLeft, cropPixelTop + 2 * thirdH),
                end = Offset(cropPixelLeft + cropPixelWidth, cropPixelTop + 2 * thirdH),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
