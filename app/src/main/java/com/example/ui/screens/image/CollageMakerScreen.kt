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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.image.OutputFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageMakerScreen(
    viewModel: CollageMakerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectImages(uris)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Collage Maker", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState is CollageMakerUiState.Loaded || uiState is CollageMakerUiState.Success) {
                            IconButton(onClick = { viewModel.reset() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is CollageMakerUiState.Idle -> {
                    CollagePickerPrompt(
                        onPick = {
                            multiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is CollageMakerUiState.Loaded -> {
                    CollageEditor(
                        state = state,
                        onAspectChange = { viewModel.setAspect(it) },
                        onVariantChange = { viewModel.setLayoutVariant(it) },
                        onSpacingChange = { viewModel.setSpacing(it) },
                        onMarginChange = { viewModel.setMargin(it) },
                        onColorChange = { viewModel.setBackgroundColor(it) },
                        onFormatChange = { viewModel.setOutputFormat(it) },
                        onExport = { viewModel.exportCollage() },
                        onChangePhotos = {
                            multiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
                is CollageMakerUiState.Processing -> {
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
                is CollageMakerUiState.Success -> {
                    CollageSuccess(
                        state = state,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = state.format.mimeType
                                putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Collage"))
                        },
                        onMakeAnother = { viewModel.reset() }
                    )
                }
                is CollageMakerUiState.Error -> {
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
private fun CollagePickerPrompt(onPick: () -> Unit) {
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
                    Icons.Default.Dashboard,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Photo Collage Maker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Combine 2 to 9 photos into stylish, high-resolution collage grids.",
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
                .testTag("select_collage_photos_button")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Photos (2–9)")
        }
    }
}

@Composable
private fun CollageEditor(
    state: CollageMakerUiState.Loaded,
    onAspectChange: (CollageAspect) -> Unit,
    onVariantChange: (Int) -> Unit,
    onSpacingChange: (Int) -> Unit,
    onMarginChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onExport: () -> Unit,
    onChangePhotos: () -> Unit
) {
    val scrollState = rememberScrollState()

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
                .height(290.dp),
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
                        contentDescription = "Collage Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    CircularProgressIndicator()
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${state.sourceBitmaps.size} Photos • ${state.aspect.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Aspect Ratio Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Canvas Aspect Ratio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CollageAspect.entries.forEach { aspect ->
                        FilterChip(
                            selected = state.aspect == aspect,
                            onClick = { onAspectChange(aspect) },
                            label = { Text(aspect.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Layout Variants Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Grid Layout Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Layout 1", "Layout 2", "Layout 3").forEachIndexed { idx, title ->
                        FilterChip(
                            selected = state.layoutVariant == idx,
                            onClick = { onVariantChange(idx) },
                            label = { Text(title) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Spacing & Margin
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Borders & Spacing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Text("Cell Spacing: ${state.spacingPx} px", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.spacingPx.toFloat(),
                    onValueChange = { onSpacingChange(it.toInt()) },
                    valueRange = 0f..32f
                )

                Text("Outer Margin: ${state.marginPx} px", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.marginPx.toFloat(),
                    onValueChange = { onMarginChange(it.toInt()) },
                    valueRange = 0f..32f
                )

                Text("Border Background Color", style = MaterialTheme.typography.labelMedium)
                val colorOptions = listOf(
                    AndroidColor.WHITE to Color.White,
                    AndroidColor.BLACK to Color.Black,
                    AndroidColor.DKGRAY to Color.DarkGray,
                    AndroidColor.LTGRAY to Color.LightGray,
                    AndroidColor.rgb(250, 240, 230) to Color(0xFFFAF0E6),
                    AndroidColor.rgb(220, 235, 252) to Color(0xFFDCEBFC)
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
                                .clickable { onColorChange(intColor) }
                        )
                    }
                }
            }
        }

        // Output Format
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
                .testTag("export_collage_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Compose & Save to Pictures", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onChangePhotos,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Different Photos")
        }
    }
}

@Composable
private fun CollageSuccess(
    state: CollageMakerUiState.Success,
    onShare: () -> Unit,
    onMakeAnother: () -> Unit
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
        Text("Collage Saved to Pictures!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(state.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Image(
            bitmap = state.resultBitmap.asImageBitmap(),
            contentDescription = "Saved Collage",
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
                .testTag("share_collage_button")
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share Collage")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onMakeAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Create Another Collage")
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
