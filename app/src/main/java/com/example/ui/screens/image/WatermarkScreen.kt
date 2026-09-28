package com.example.ui.screens.image

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.image.OutputFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    viewModel: WatermarkViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val sourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectSourceImage(uri)
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectLogoImage(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermark Tool", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is WatermarkUiState.Loaded || uiState is WatermarkUiState.Success) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is WatermarkUiState.Idle -> {
                    WatermarkPickerPrompt(
                        onPick = {
                            sourcePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is WatermarkUiState.Loaded -> {
                    WatermarkEditor(
                        state = state,
                        onTypeChange = { viewModel.setWatermarkType(it) },
                        onTextChange = { viewModel.setText(it) },
                        onTextSizeChange = { viewModel.setTextSize(it) },
                        onColorChange = { viewModel.setTextColor(it) },
                        onOpacityChange = { viewModel.setOpacity(it) },
                        onImageScaleChange = { viewModel.setImageScale(it) },
                        onPickLogo = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPositionPreset = { viewModel.applyPositionPreset(it) },
                        onTapPosition = { nx, ny -> viewModel.setPosition(nx, ny) },
                        onFormatChange = { viewModel.setOutputFormat(it) },
                        onExport = { viewModel.exportImage() },
                        onChangeSource = {
                            sourcePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is WatermarkUiState.Processing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is WatermarkUiState.Success -> {
                    WatermarkSuccess(
                        state = state,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = state.format.mimeType
                                putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Watermarked Image"))
                        },
                        onEditAnother = { viewModel.reset() }
                    )
                }
                is WatermarkUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.reset() }) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatermarkPickerPrompt(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.BrandingWatermark,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Add Watermark", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Protect your photos with customizable text or logo overlays.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPick,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
                .testTag("select_watermark_source_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Photo")
        }
    }
}

@Composable
private fun WatermarkEditor(
    state: WatermarkUiState.Loaded,
    onTypeChange: (WatermarkType) -> Unit,
    onTextChange: (String) -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onColorChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onImageScaleChange: (Float) -> Unit,
    onPickLogo: () -> Unit,
    onPositionPreset: (WatermarkPositionPreset) -> Unit,
    onTapPosition: (Float, Float) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onExport: () -> Unit,
    onChangeSource: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Interactive Preview Card (tap to place watermark!)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val normX = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                            val normY = (offset.y / size.height).coerceIn(0.05f, 0.95f)
                            onTapPosition(normX, normY)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state.previewBitmap != null) {
                    Image(
                        bitmap = state.previewBitmap.asImageBitmap(),
                        contentDescription = "Watermark Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    CircularProgressIndicator()
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "Tap preview or choose preset below to position",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Watermark Type Selector
        TabRow(
            selectedTabIndex = if (state.watermarkType == WatermarkType.TEXT) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = state.watermarkType == WatermarkType.TEXT,
                onClick = { onTypeChange(WatermarkType.TEXT) },
                text = { Text("Text Watermark", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = state.watermarkType == WatermarkType.IMAGE,
                onClick = { onTypeChange(WatermarkType.IMAGE) },
                text = { Text("Logo Image", fontWeight = FontWeight.SemiBold) }
            )
        }

        // Settings based on type
        if (state.watermarkType == WatermarkType.TEXT) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Text Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Font Size Slider
                    Text("Font Size: ${state.textSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.textSizeSp,
                        onValueChange = onTextSizeChange,
                        valueRange = 14f..72f
                    )

                    // Color options
                    Text("Text Color", style = MaterialTheme.typography.labelMedium)
                    val colorList = listOf(
                        AndroidColor.WHITE to Color.White,
                        AndroidColor.BLACK to Color.Black,
                        AndroidColor.RED to Color.Red,
                        AndroidColor.YELLOW to Color.Yellow,
                        AndroidColor.CYAN to Color.Cyan,
                        AndroidColor.GREEN to Color.Green
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorList.forEach { (intColor, composeColor) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (state.textColor == intColor) 3.dp else 1.dp,
                                        color = if (state.textColor == intColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { onColorChange(intColor) }
                            )
                        }
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Logo Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onPickLogo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.logoBitmap != null) "Change Logo Image" else "Choose Logo Image")
                    }

                    if (state.logoBitmap != null) {
                        Text("Logo Scale: ${(state.imageScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.imageScale,
                            onValueChange = onImageScaleChange,
                            valueRange = 0.1f..1.0f
                        )
                    }
                }
            }
        }

        // Position Presets & Opacity Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Position Presets", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WatermarkPositionPreset.entries.forEach { preset ->
                        FilledTonalButton(
                            onClick = { onPositionPreset(preset) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Opacity: ${(state.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.1f..1.0f
                )
            }
        }

        // Export Format
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Export Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.JPEG,
                        onClick = { onFormatChange(OutputFormat.JPEG) },
                        label = { Text("JPEG") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.PNG,
                        onClick = { onFormatChange(OutputFormat.PNG) },
                        label = { Text("PNG") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.WEBP,
                        onClick = { onFormatChange(OutputFormat.WEBP) },
                        label = { Text("WebP") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Export Button
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("export_watermark_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Apply & Save to Pictures", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onChangeSource,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Another Photo")
        }
    }
}

@Composable
private fun WatermarkSuccess(
    state: WatermarkUiState.Success,
    onShare: () -> Unit,
    onEditAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Saved to Pictures/PixelRox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(state.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Image(
            bitmap = state.resultBitmap.asImageBitmap(),
            contentDescription = "Saved Result",
            modifier = Modifier
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        )

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Format", style = MaterialTheme.typography.labelSmall)
                    Text(state.format.name, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("File Size", style = MaterialTheme.typography.labelSmall)
                    Text(formatFileSize(state.fileSizeBytes), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("share_watermarked_image_button")
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share Image")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onEditAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Edit Another")
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}
