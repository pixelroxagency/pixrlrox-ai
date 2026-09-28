package com.example.ui.screens.image

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.image.OutputFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResizerContent(
    viewModel: BatchResizerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.selectImages(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("batch_resizer_content")
    ) {
        when (val state = uiState) {
            is BatchResizerUiState.Idle -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .clickable {
                            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .testTag("batch_resizer_picker_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PhotoSizeSelectSmall,
                            contentDescription = "Select Multiple Images",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Select Images to Batch Resize",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pick multiple photos to resize sequentially without memory exhaustion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("btn_select_images_batch_resize")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Images")
                        }
                    }
                }
            }

            is BatchResizerUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Selected ${state.items.size} Images",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = state.mode == BatchResizeMode.LONG_EDGE,
                                        onClick = { viewModel.setMode(BatchResizeMode.LONG_EDGE) },
                                        label = { Text("Long Edge") }
                                    )
                                    FilterChip(
                                        selected = state.mode == BatchResizeMode.DIMENSIONS,
                                        onClick = { viewModel.setMode(BatchResizeMode.DIMENSIONS) },
                                        label = { Text("Target Bounds") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                if (state.mode == BatchResizeMode.LONG_EDGE) {
                                    Text("Long Edge: ${state.targetLongEdge} px", style = MaterialTheme.typography.bodyMedium)
                                    Slider(
                                        value = state.targetLongEdge.toFloat(),
                                        onValueChange = { viewModel.setTargetLongEdge(it.toInt()) },
                                        valueRange = 480f..3840f,
                                        steps = 6
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = state.targetWidth.toString(),
                                            onValueChange = { w -> w.toIntOrNull()?.let { viewModel.setTargetDimensions(it, state.targetHeight) } },
                                            label = { Text("Width") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = state.targetHeight.toString(),
                                            onValueChange = { h -> h.toIntOrNull()?.let { viewModel.setTargetDimensions(state.targetWidth, it) } },
                                            label = { Text("Height") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = state.noUpscale,
                                        onCheckedChange = { viewModel.setNoUpscale(it) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Do not upscale smaller images", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    items(state.items) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(item.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.weight(1f)) {
                        Text("Clear")
                    }
                    Button(
                        onClick = { viewModel.startBatchProcessing() },
                        modifier = Modifier.weight(1f).testTag("btn_start_batch_resize")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resize All (${state.items.size})")
                    }
                }
            }

            is BatchResizerUiState.Processing -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Processing ${state.currentIndex} of ${state.totalCount} images...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Success: ${state.successCount} • Failed: ${state.failedCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.cancelBatch() }) {
                        Text("Cancel")
                    }
                }
            }

            is BatchResizerUiState.Completed -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Batch Resize Finished", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Saved to Pictures/PixelRox. Success: ${state.successCount}, Failed: ${state.failedCount}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.items) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.status == BatchItemStatus.SUCCESS)
                                        MaterialTheme.colorScheme.surface
                                    else
                                        MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (item.status == BatchItemStatus.SUCCESS) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (item.status == BatchItemStatus.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (item.status == BatchItemStatus.SUCCESS) {
                                            Text("${item.outputWidth} x ${item.outputHeight} px", style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            Text(item.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
