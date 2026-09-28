package com.example.ui.screens.image

import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarMakerContent(
    viewModel: AvatarMakerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectSourceImage(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("avatar_maker_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is AvatarMakerUiState.Idle -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .testTag("avatar_picker_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Select Profile Photo",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Create Profile Avatar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Circular avatar maker with custom borders, backgrounds, and transparent PNG / JPEG export",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("btn_select_avatar_photo")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo")
                        }
                    }
                }
            }

            is AvatarMakerUiState.Loaded -> {
                // Circular Preview
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    state.previewBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Avatar Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } ?: CircularProgressIndicator()
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Background Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.backgroundMode == AvatarBackgroundMode.TRANSPARENT,
                                onClick = { viewModel.setBackgroundMode(AvatarBackgroundMode.TRANSPARENT) },
                                label = { Text("Transparent") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.backgroundMode == AvatarBackgroundMode.SOLID_WHITE,
                                onClick = { viewModel.setBackgroundMode(AvatarBackgroundMode.SOLID_WHITE) },
                                label = { Text("White") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.backgroundMode == AvatarBackgroundMode.SOLID_DARK,
                                onClick = { viewModel.setBackgroundMode(AvatarBackgroundMode.SOLID_DARK) },
                                label = { Text("Dark") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Border Ring Thickness: ${state.borderWidthDp.toInt()} dp", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = state.borderWidthDp,
                            onValueChange = { viewModel.setBorderWidthDp(it) },
                            valueRange = 0f..24f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ring Color", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val ringColors = listOf(
                                AndroidColor.rgb(33, 150, 243),  // Blue
                                AndroidColor.rgb(76, 175, 80),   // Green
                                AndroidColor.rgb(233, 30, 99),   // Pink
                                AndroidColor.rgb(156, 39, 176),  // Purple
                                AndroidColor.rgb(255, 152, 0),   // Orange
                                AndroidColor.WHITE,
                                AndroidColor.BLACK
                            )
                            ringColors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            width = if (state.borderColor == c) 3.dp else 1.dp,
                                            color = if (state.borderColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setBorderColor(c) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Export Resolution", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AvatarExportSize.entries.forEach { size ->
                                FilterChip(
                                    selected = state.exportSize == size,
                                    onClick = { viewModel.setExportSize(size) },
                                    label = { Text(size.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Format: ${state.outputFormat.title}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Photo")
                    }
                    Button(
                        onClick = { viewModel.exportAvatar() },
                        modifier = Modifier.weight(1f).testTag("btn_export_avatar")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Avatar")
                    }
                }
            }

            is AvatarMakerUiState.Processing -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is AvatarMakerUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Avatar Saved!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${state.displayName} (${state.dimension}x${state.dimension} px)", style = MaterialTheme.typography.bodyMedium)
                        Text("${state.outputFormat.title} • ${state.fileSizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = state.outputFormat.mimeType
                                    putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Profile Avatar"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }
                            OutlinedButton(onClick = { viewModel.reset() }) {
                                Text("Create Another")
                            }
                        }
                    }
                }
            }

            is AvatarMakerUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Avatar Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.reset() }) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}
