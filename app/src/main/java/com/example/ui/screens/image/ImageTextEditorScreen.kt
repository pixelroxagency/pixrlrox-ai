package com.example.ui.screens.image

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.util.RecognizedTextRegion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTextEditorScreen(
    viewModel: ImageTextEditorViewModel,
    onBack: () -> Unit,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImageAndScanOcr(context, uri)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Image Text Remover & Editor") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (viewModel.currentBitmap != null) {
                            IconButton(
                                onClick = { viewModel.undoLastEdit() },
                                enabled = uiState.undoAvailable
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo Last Edit"
                                )
                            }
                            IconButton(onClick = { viewModel.resetToOriginal() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset to Original"
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Select Image"
                            )
                        }
                    }
                )
            }
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
            if (uiState.isLoading || uiState.isOcrRunning || uiState.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        val label = when {
                            uiState.isOcrRunning -> "Detecting text regions on-device..."
                            uiState.isExporting -> "Exporting edited image to device..."
                            else -> "Processing image..."
                        }
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (viewModel.currentBitmap == null) {
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
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Remove & Edit Text in Images",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select any picture with text. Detected text regions will be outlined with interactive boxes. Tap any box to erase the text using surrounding pixels or replace it with custom text.",
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
                            Text("Select Image")
                        }
                    }
                }
            } else {
                // Active Editor Canvas
                val bmp = viewModel.currentBitmap!!

                // Status notice
                if (uiState.statusMessage != null) {
                    Text(
                        text = uiState.statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Interactive Image & Bounding Box Overlay
                ImageTextEditorCanvas(
                    bitmap = bmp,
                    regions = uiState.regions,
                    selectedRegionId = uiState.selectedRegionId,
                    onRegionSelected = { viewModel.selectRegion(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Limitation note
                Text(
                    text = "Note: Text removal blends surrounding border pixels to in-paint the region. Complex textures may leave slight artifacts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Region controls if selected
                val selectedRegion = uiState.regions.firstOrNull { it.id == uiState.selectedRegionId }
                if (selectedRegion != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Selected Text: \"${selectedRegion.text}\"",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Remove Button
                            Button(
                                onClick = { viewModel.removeSelectedText() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove Text (In-paint Fill)")
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            // Replace Section
                            Text(
                                text = "Or Replace Text",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = uiState.replacementText,
                                onValueChange = { viewModel.updateReplacementText(it) },
                                label = { Text("Replacement Text") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Font Size: ${uiState.replacementTextSize.roundToInt()} sp",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = uiState.replacementTextSize,
                                onValueChange = { viewModel.updateReplacementTextSize(it) },
                                valueRange = 14f..64f,
                                steps = 25
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Text Color",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val colorOptions = listOf(
                                    Pair("Black", AndroidColor.BLACK),
                                    Pair("White", AndroidColor.WHITE),
                                    Pair("Red", AndroidColor.RED),
                                    Pair("Blue", AndroidColor.BLUE),
                                    Pair("Green", AndroidColor.rgb(0, 128, 0)),
                                    Pair("Yellow", AndroidColor.YELLOW)
                                )
                                colorOptions.forEach { (name, colorInt) ->
                                    val isChosen = uiState.replacementTextColor == colorInt
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorInt))
                                            .border(
                                                width = if (isChosen) 3.dp else 1.dp,
                                                color = if (isChosen) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.updateReplacementTextColor(colorInt) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { viewModel.replaceSelectedText() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.replacementText.isNotBlank()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply Replacement Text")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Export & Actions
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
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                } else {
                    Button(
                        onClick = { viewModel.exportImage(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Edited Image to Device")
                    }
                }
            }
        }
    }
}

@Composable
fun ImageTextEditorCanvas(
    bitmap: Bitmap,
    regions: List<RecognizedTextRegion>,
    selectedRegionId: String?,
    onRegionSelected: (String?) -> Unit
) {
    var userScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val baseFit = remember(bitmap.width, bitmap.height, containerWidth, containerHeight) {
            ImageTextEditorTransform.computeBaseFit(
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                containerWidth = containerWidth,
                containerHeight = containerHeight
            )
        }

        // Parent Gesture Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(baseFit, userScale, panOffset) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = userScale
                        val newScale = (oldScale * zoom).coerceIn(1f, 8f)
                        userScale = newScale

                        if (newScale > 1f) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val zoomRatio = newScale / oldScale
                            val newPanX = panOffset.x + pan.x + (centroid.x - cx - panOffset.x) * (1f - zoomRatio)
                            val newPanY = panOffset.y + pan.y + (centroid.y - cy - panOffset.y) * (1f - zoomRatio)
                            panOffset = ImageTextEditorTransform.clampPan(
                                panX = newPanX,
                                panY = newPanY,
                                userScale = newScale,
                                baseFit = baseFit,
                                containerWidth = size.width.toFloat(),
                                containerHeight = size.height.toFloat()
                            )
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
                .pointerInput(regions, baseFit, userScale, panOffset) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (userScale > 1.2f) {
                                userScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                userScale = targetScale
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val targetPanX = -(tapOffset.x - cx) * (targetScale - 1f)
                                val targetPanY = -(tapOffset.y - cy) * (targetScale - 1f)
                                panOffset = ImageTextEditorTransform.clampPan(
                                    panX = targetPanX,
                                    panY = targetPanY,
                                    userScale = targetScale,
                                    baseFit = baseFit,
                                    containerWidth = size.width.toFloat(),
                                    containerHeight = size.height.toFloat()
                                )
                            }
                        },
                        onTap = { tapOffset ->
                            val density = this.density
                            val hitSlopPx = with(density) { 14.dp.toPx() }
                            val maxFallbackPx = with(density) { 32.dp.toPx() }
                            val selectedId = ImageTextEditorTransform.findSelectedRegion(
                                touchX = tapOffset.x,
                                touchY = tapOffset.y,
                                regions = regions,
                                baseFit = baseFit,
                                containerWidth = size.width.toFloat(),
                                containerHeight = size.height.toFloat(),
                                userScale = userScale,
                                panX = panOffset.x,
                                panY = panOffset.y,
                                hitSlopPx = hitSlopPx,
                                maxFallbackDistancePx = maxFallbackPx
                            )
                            onRegionSelected(selectedId)
                        }
                    )
                }
        ) {
            // Scaled & Panned Content Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = userScale
                        scaleY = userScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                // Base Image
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Image for text editing",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Overlay detected text boxes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (region in regions) {
                        val box = region.boundingBox
                        val rLeft = baseFit.baseOffsetX + box.left * baseFit.baseScale
                        val rTop = baseFit.baseOffsetY + box.top * baseFit.baseScale
                        val rWidth = box.width() * baseFit.baseScale
                        val rHeight = box.height() * baseFit.baseScale

                        val isSelected = region.id == selectedRegionId

                        if (isSelected) {
                            // Highlight selected region
                            drawRect(
                                color = Color.Yellow.copy(alpha = 0.35f),
                                topLeft = Offset(rLeft, rTop),
                                size = Size(rWidth, rHeight)
                            )
                            drawRect(
                                color = Color.Yellow,
                                topLeft = Offset(rLeft, rTop),
                                size = Size(rWidth, rHeight),
                                style = Stroke(width = (2.5.dp / userScale.coerceAtLeast(1f)).toPx())
                            )
                        } else {
                            // Outlined selectable region
                            drawRect(
                                color = Color.Cyan.copy(alpha = 0.85f),
                                topLeft = Offset(rLeft, rTop),
                                size = Size(rWidth, rHeight),
                                style = Stroke(width = (1.5.dp / userScale.coerceAtLeast(1f)).toPx())
                            )
                        }
                    }
                }
            }

            // Floating Controls: Reset Zoom & Zoom Indicator
            if (userScale > 1.05f) {
                Surface(
                    onClick = {
                        userScale = 1f
                        panOffset = Offset.Zero
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Zoom",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", userScale)}x • Reset",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            } else if (regions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Pinch to zoom • Tap box to select",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
