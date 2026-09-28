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
import com.example.data.util.ImageBitmapHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostQuoteMakerScreen(
    viewModel: PostQuoteMakerViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val bgPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBackgroundImage(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post & Quote Maker", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            val success = uiState.successResult
            if (success != null) {
                QuoteSuccessView(
                    state = success,
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = success.format.mimeType
                            putExtra(Intent.EXTRA_STREAM, success.contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Quote Graphic"))
                    },
                    onCreateAnother = { viewModel.dismissSuccess() }
                )
            } else if (uiState.isProcessingExport) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(uiState.exportProgressMessage, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                QuoteEditorView(
                    state = uiState,
                    onQuoteChange = { viewModel.setQuoteText(it) },
                    onAuthorChange = { viewModel.setAuthorText(it) },
                    onCanvasChange = { viewModel.setCanvasFormat(it) },
                    onPickBgImage = {
                        bgPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClearBgImage = { viewModel.clearBackgroundImage() },
                    onScrimChange = { viewModel.setScrimOpacity(it) },
                    onColorChange = { viewModel.setBackgroundColor(it) },
                    onQuoteSizeChange = { viewModel.setQuoteTextSize(it) },
                    onQuoteColorChange = { viewModel.setQuoteTextColor(it) },
                    onAlignChange = { viewModel.setAlignment(it) },
                    onVerticalChange = { viewModel.setVerticalPosition(it) },
                    onShadowChange = { viewModel.setShadow(it) },
                    onFormatChange = { viewModel.setOutputFormat(it) },
                    onExport = { viewModel.exportGraphic() }
                )
            }

            uiState.errorMessage?.let { errorMsg ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissError() },
                    title = { Text("Error") },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuoteEditorView(
    state: PostQuoteUiState,
    onQuoteChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onCanvasChange: (QuoteCanvasFormat) -> Unit,
    onPickBgImage: () -> Unit,
    onClearBgImage: () -> Unit,
    onScrimChange: (Float) -> Unit,
    onColorChange: (Int) -> Unit,
    onQuoteSizeChange: (Float) -> Unit,
    onQuoteColorChange: (Int) -> Unit,
    onAlignChange: (ImageBitmapHelper.TextAlignmentOption) -> Unit,
    onVerticalChange: (ImageBitmapHelper.VerticalPositionOption) -> Unit,
    onShadowChange: (Boolean) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onExport: () -> Unit
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
                        contentDescription = "Graphic Preview",
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
                        text = state.canvasFormat.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Text Content Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Content", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.quoteText,
                    onValueChange = onQuoteChange,
                    label = { Text("Quote / Message") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.authorText,
                    onValueChange = onAuthorChange,
                    label = { Text("Author / Signature (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Canvas Format Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Canvas Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuoteCanvasFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.canvasFormat == format,
                            onClick = { onCanvasChange(format) },
                            label = { Text(format.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Background Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Background", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickBgImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.backgroundImage != null) "Change Photo" else "Photo Background")
                    }

                    if (state.backgroundImage != null) {
                        OutlinedButton(onClick = onClearBgImage) {
                            Text("Remove")
                        }
                    }
                }

                if (state.backgroundImage != null) {
                    Text("Dark Overlay (Scrim): ${(state.scrimOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.scrimOpacity,
                        onValueChange = onScrimChange,
                        valueRange = 0f..0.9f
                    )
                } else {
                    Text("Solid Color Palette", style = MaterialTheme.typography.labelMedium)
                    val colorPalette = listOf(
                        AndroidColor.rgb(20, 24, 34) to Color(0xFF141822),
                        AndroidColor.rgb(40, 44, 52) to Color(0xFF282C34),
                        AndroidColor.rgb(88, 28, 44) to Color(0xFF581C2C),
                        AndroidColor.rgb(24, 60, 48) to Color(0xFF183C30),
                        AndroidColor.rgb(180, 80, 40) to Color(0xFFB45028),
                        AndroidColor.rgb(245, 245, 247) to Color(0xFFF5F5F7)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorPalette.forEach { (intColor, composeColor) ->
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
        }

        // Typography & Layout Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Typography & Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                Text("Font Size: ${state.quoteTextSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.quoteTextSizeSp,
                    onValueChange = onQuoteSizeChange,
                    valueRange = 20f..64f
                )

                Text("Text Color", style = MaterialTheme.typography.labelMedium)
                val textColorPalette = listOf(
                    AndroidColor.WHITE to Color.White,
                    AndroidColor.rgb(255, 215, 0) to Color(0xFFFFD700),
                    AndroidColor.rgb(160, 230, 255) to Color(0xFFA0E6FF),
                    AndroidColor.rgb(255, 182, 193) to Color(0xFFFFB6C1),
                    AndroidColor.rgb(40, 40, 40) to Color(0xFF282828)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    textColorPalette.forEach { (intColor, composeColor) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(
                                    width = if (state.quoteTextColor == intColor) 3.dp else 1.dp,
                                    color = if (state.quoteTextColor == intColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { onQuoteColorChange(intColor) }
                        )
                    }
                }

                // Alignment
                Text("Text Alignment", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ImageBitmapHelper.TextAlignmentOption.LEFT to "Left",
                        ImageBitmapHelper.TextAlignmentOption.CENTER to "Center",
                        ImageBitmapHelper.TextAlignmentOption.RIGHT to "Right"
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = state.alignment == opt,
                            onClick = { onAlignChange(opt) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Vertical Position
                Text("Vertical Position", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ImageBitmapHelper.VerticalPositionOption.TOP to "Top",
                        ImageBitmapHelper.VerticalPositionOption.CENTER to "Center",
                        ImageBitmapHelper.VerticalPositionOption.BOTTOM to "Bottom"
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = state.verticalPosition == opt,
                            onClick = { onVerticalChange(opt) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Drop Shadow Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Text Drop Shadow", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.hasShadow,
                        onCheckedChange = onShadowChange
                    )
                }
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
                .testTag("export_quote_graphic_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Render & Save Graphic", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuoteSuccessView(
    state: PostQuoteUiState.ExportSuccessData,
    onShare: () -> Unit,
    onCreateAnother: () -> Unit
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
        Text("Graphic Saved to Pictures!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(state.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Image(
            bitmap = state.resultBitmap.asImageBitmap(),
            contentDescription = "Saved Graphic",
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
                .testTag("share_quote_graphic_button")
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share Graphic")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCreateAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Create Another")
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
