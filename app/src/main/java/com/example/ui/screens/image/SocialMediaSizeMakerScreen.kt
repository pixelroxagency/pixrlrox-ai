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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.image.OutputFormat
import com.example.data.util.ImageBitmapHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaSizeMakerScreen(
    viewModel: SocialMediaSizeMakerViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Social Media Size Maker", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is SocialMediaSizeUiState.Loaded || uiState is SocialMediaSizeUiState.Success) {
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
                is SocialMediaSizeUiState.Idle -> {
                    SocialPickerPrompt(
                        onPick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is SocialMediaSizeUiState.Loaded -> {
                    SocialEditor(
                        state = state,
                        onSelectPreset = { viewModel.selectPreset(it) },
                        onCustomDimensions = { w, h -> viewModel.updateCustomDimensions(w, h) },
                        onSetFitMode = { viewModel.setFitMode(it) },
                        onSetColor = { viewModel.setBackgroundColor(it) },
                        onSetFormat = { viewModel.setOutputFormat(it) },
                        onExport = { viewModel.exportImage() },
                        onChangeImage = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is SocialMediaSizeUiState.Processing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(state.progressMessage, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is SocialMediaSizeUiState.Success -> {
                    SocialSuccess(
                        state = state,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = state.format.mimeType
                                putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Image"))
                        },
                        onEditAnother = { viewModel.reset() }
                    )
                }
                is SocialMediaSizeUiState.Error -> {
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
private fun SocialPickerPrompt(onPick: () -> Unit) {
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
                    Icons.Default.AspectRatio,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Social Media Resizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Quickly scale and format photos for Instagram, TikTok, YouTube, and X with pixel-perfect ratios.",
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
                .testTag("select_social_image_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Photo")
        }
    }
}

@Composable
private fun SocialEditor(
    state: SocialMediaSizeUiState.Loaded,
    onSelectPreset: (SocialPreset) -> Unit,
    onCustomDimensions: (Int, Int) -> Unit,
    onSetFitMode: (ImageBitmapHelper.FitMode) -> Unit,
    onSetColor: (Int) -> Unit,
    onSetFormat: (OutputFormat) -> Unit,
    onExport: () -> Unit,
    onChangeImage: () -> Unit
) {
    val scrollState = rememberScrollState()
    var customWidthStr by remember { mutableStateOf(state.targetWidth.toString()) }
    var customHeightStr by remember { mutableStateOf(state.targetHeight.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preview Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (state.previewBitmap != null) {
                    Image(
                        bitmap = state.previewBitmap.asImageBitmap(),
                        contentDescription = "Framing Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    CircularProgressIndicator()
                }

                // Dimension Overlay Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${state.targetWidth} × ${state.targetHeight} px",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Platform Presets Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Platform Preset", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SocialPreset.entries) { preset ->
                        FilterChip(
                            selected = state.selectedPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(preset.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${preset.width}×${preset.height} (${preset.aspectLabel})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                // Custom Dimensions Inputs
                if (state.selectedPreset == SocialPreset.CUSTOM) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = customWidthStr,
                            onValueChange = {
                                customWidthStr = it.filter { ch -> ch.isDigit() }
                                val w = customWidthStr.toIntOrNull() ?: 1080
                                val h = customHeightStr.toIntOrNull() ?: 1080
                                onCustomDimensions(w, h)
                            },
                            label = { Text("Width (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeightStr,
                            onValueChange = {
                                customHeightStr = it.filter { ch -> ch.isDigit() }
                                val w = customWidthStr.toIntOrNull() ?: 1080
                                val h = customHeightStr.toIntOrNull() ?: 1080
                                onCustomDimensions(w, h)
                            },
                            label = { Text("Height (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Fit Mode Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Fit Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.fitMode == ImageBitmapHelper.FitMode.CROP_TO_FILL,
                        onClick = { onSetFitMode(ImageBitmapHelper.FitMode.CROP_TO_FILL) },
                        label = { Text("Crop to Fill") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.fitMode == ImageBitmapHelper.FitMode.FIT_INSIDE,
                        onClick = { onSetFitMode(ImageBitmapHelper.FitMode.FIT_INSIDE) },
                        label = { Text("Fit Inside (Padded)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.fitMode == ImageBitmapHelper.FitMode.FIT_INSIDE) {
                    Spacer(Modifier.height(12.dp))
                    Text("Padding Background Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    val colorOptions = listOf(
                        AndroidColor.WHITE to Color.White,
                        AndroidColor.BLACK to Color.Black,
                        AndroidColor.DKGRAY to Color.DarkGray,
                        AndroidColor.LTGRAY to Color.LightGray,
                        AndroidColor.rgb(245, 240, 230) to Color(0xFFF5F0E6)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorOptions.forEach { (intColor, composeColor) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(composeColor)
                                    .border(
                                        width = if (state.backgroundColor == intColor) 3.dp else 1.dp,
                                        color = if (state.backgroundColor == intColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { onSetColor(intColor) }
                            )
                        }
                    }
                }
            }
        }

        // Format Selection
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
                        onClick = { onSetFormat(OutputFormat.JPEG) },
                        label = { Text("JPEG") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.PNG,
                        onClick = { onSetFormat(OutputFormat.PNG) },
                        label = { Text("PNG") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.WEBP,
                        onClick = { onSetFormat(OutputFormat.WEBP) },
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
                .testTag("export_social_size_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Resize & Save to Pictures", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onChangeImage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Another Image")
        }
    }
}

@Composable
private fun SocialSuccess(
    state: SocialMediaSizeUiState.Success,
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

        // Thumbnail Preview
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
                    Text("Dimensions", style = MaterialTheme.typography.labelSmall)
                    Text("${state.outputWidth} × ${state.outputHeight}", fontWeight = FontWeight.Bold)
                }
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
                .testTag("share_social_image_button")
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
