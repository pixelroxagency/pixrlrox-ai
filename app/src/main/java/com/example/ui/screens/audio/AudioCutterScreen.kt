package com.example.ui.screens.audio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.audio.AudioOutputFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCutterScreen(
    viewModel: AudioCutterViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectAudio(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Cutter", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is AudioCutterUiState.Loaded || uiState is AudioCutterUiState.Success) {
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
                is AudioCutterUiState.Idle -> {
                    AudioPickerPrompt(onPickAudio = { audioPickerLauncher.launch("audio/*") })
                }
                is AudioCutterUiState.Loaded -> {
                    AudioCutterEditor(
                        state = state,
                        onRangeChange = { start, end -> viewModel.updateRange(start, end) },
                        onFormatChange = { fmt -> viewModel.setTargetFormat(fmt) },
                        onTogglePreview = { viewModel.togglePreviewPlayback() },
                        onTrim = { viewModel.trimAndExport() },
                        onChangeFile = { audioPickerLauncher.launch("audio/*") }
                    )
                }
                is AudioCutterUiState.Processing -> {
                    AudioProcessingView(progress = state.progress, status = state.statusMessage)
                }
                is AudioCutterUiState.Success -> {
                    AudioSuccessView(
                        state = state,
                        onTogglePlay = { viewModel.toggleResultPlayback() },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = state.mimeType
                                putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Trimmed Audio"))
                        },
                        onTrimAnother = { viewModel.reset() }
                    )
                }
                is AudioCutterUiState.Error -> {
                    AudioErrorView(message = state.message, onRetry = { viewModel.reset() })
                }
            }
        }
    }
}

@Composable
private fun AudioPickerPrompt(onPickAudio: () -> Unit) {
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
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ContentCut,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Trim & Cut Audio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select any audio track to cut custom clips with millisecond precision.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPickAudio,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
                .testTag("select_audio_button")
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Audio File")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioCutterEditor(
    state: AudioCutterUiState.Loaded,
    onRangeChange: (Long, Long) -> Unit,
    onFormatChange: (AudioOutputFormat) -> Unit,
    onTogglePreview: () -> Unit,
    onTrim: () -> Unit,
    onChangeFile: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(
                        text = "Total: ${formatDuration(state.durationMs)} • ${state.audioCodec} • ${formatFileSize(state.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onChangeFile) {
                    Text("Change")
                }
            }
        }

        // Timeline Range Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clip Selection",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "Duration: ${formatDuration(state.endMs - state.startMs)}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Start & End Timestamps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Start Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(state.startMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("End Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(state.endMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // RangeSlider
                val durationFloat = state.durationMs.toFloat().coerceAtLeast(1000f)
                RangeSlider(
                    value = state.startMs.toFloat()..state.endMs.toFloat(),
                    onValueChange = { range ->
                        onRangeChange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..durationFloat,
                    modifier = Modifier.fillMaxWidth()
                )

                // Fine Nudge Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { onRangeChange(state.startMs - 1000L, state.endMs) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("-1s")
                        }
                        FilledTonalButton(
                            onClick = { onRangeChange(state.startMs + 1000L, state.endMs) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+1s")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { onRangeChange(state.startMs, state.endMs - 1000L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("-1s")
                        }
                        FilledTonalButton(
                            onClick = { onRangeChange(state.startMs, state.endMs + 1000L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+1s")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Preview Playback Button
                OutlinedButton(
                    onClick = onTogglePreview,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (state.isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isPreviewPlaying) "Pause Preview" else "Play Selected Range")
                }
            }
        }

        // Format Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Output Format",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.targetFormat == AudioOutputFormat.MP3,
                        onClick = { onFormatChange(AudioOutputFormat.MP3) },
                        label = { Text("MP3 (Universal)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.targetFormat == AudioOutputFormat.M4A,
                        onClick = { onFormatChange(AudioOutputFormat.M4A) },
                        label = { Text("M4A (AAC Fast)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Export Button
        Button(
            onClick = onTrim,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("trim_export_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Trim & Save to Music", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AudioProcessingView(progress: Int, status: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudioSuccessView(
    state: AudioCutterUiState.Success,
    onTogglePlay: () -> Unit,
    onShare: () -> Unit,
    onTrimAnother: () -> Unit
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
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Audio Trimmed Successfully!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Saved to Music/PixelRox",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.displayName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Clip Duration: ${formatDuration(state.durationMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "File Size: ${formatFileSize(state.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Play Result
        FilledTonalButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                if (state.isResultPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.isResultPlaying) "Pause Result" else "Play Result")
        }

        Spacer(Modifier.height(12.dp))

        // Share
        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("share_trimmed_audio_button")
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share Audio")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onTrimAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Trim Another")
        }
    }
}

@Composable
private fun AudioErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Trimming Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val millis = (ms % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, millis)
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
