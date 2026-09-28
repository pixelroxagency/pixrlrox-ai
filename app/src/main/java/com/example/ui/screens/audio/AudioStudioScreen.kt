package com.example.ui.screens.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.audio.AudioMergeTrack
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioQualityPreset
import com.example.core.audio.Mp3Bitrate
import com.example.core.audio.VolumePreset
import com.example.core.trimmer.VideoTrimmerUtils
import java.util.Locale

enum class AudioStudioCapability(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tag: String
) {
    CUT("Audio Cutter", "Cut and trim audio clips precisely", Icons.Default.ContentCut, "studio_cap_cut"),
    VIDEO_TO_AUDIO("Video to Audio", "Extract audio tracks from video files", Icons.Default.Audiotrack, "studio_cap_video_audio"),
    CONVERT("Format Converter", "Convert between MP3, AAC, and WAV", Icons.Default.Transform, "studio_cap_convert"),
    VOLUME("Volume Booster", "Boost volume 0.5x to 2.0x with safe limiter", Icons.Default.VolumeUp, "studio_cap_volume"),
    MERGE("Audio Merger", "Join multiple audio tracks sequentially", Icons.Default.CallMerge, "studio_cap_merge")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioScreen(
    initialCapability: AudioStudioCapability? = null,
    onBack: () -> Unit,
    onPlayAudio: (Uri) -> Unit = {}
) {
    var activeCapability by remember { mutableStateOf(initialCapability) }
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vmFactory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    val cutterVm: AudioCutterViewModel = viewModel(factory = vmFactory)
    val videoToAudioVm: VideoToAudioViewModel = viewModel(factory = vmFactory)
    val converterVm: AudioConverterViewModel = viewModel(factory = vmFactory)
    val volumeVm: AudioVolumeViewModel = viewModel(factory = vmFactory)
    val mergerVm: AudioMergerViewModel = viewModel(factory = vmFactory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCapability?.title ?: "Audio Studio",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeCapability != null) {
                                activeCapability = null
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("audio_studio_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeCapability != null) {
                        IconButton(
                            onClick = { activeCapability = null },
                            modifier = Modifier.testTag("audio_studio_home_button")
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = "Studio Hub")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeCapability) {
                null -> AudioStudioHubView(
                    onSelectCapability = { activeCapability = it }
                )
                AudioStudioCapability.CUT -> AudioCutterScreen(
                    viewModel = cutterVm,
                    onBack = { activeCapability = null }
                )
                AudioStudioCapability.VIDEO_TO_AUDIO -> VideoToAudioScreen(
                    viewModel = videoToAudioVm,
                    onBack = { activeCapability = null },
                    onPlayAudio = onPlayAudio
                )
                AudioStudioCapability.CONVERT -> AudioConverterContent(
                    viewModel = converterVm,
                    onBack = { activeCapability = null }
                )
                AudioStudioCapability.VOLUME -> AudioVolumeContent(
                    viewModel = volumeVm,
                    onBack = { activeCapability = null }
                )
                AudioStudioCapability.MERGE -> AudioMergerContent(
                    viewModel = mergerVm,
                    onBack = { activeCapability = null }
                )
            }
        }
    }
}

@Composable
fun AudioStudioHubView(
    onSelectCapability: (AudioStudioCapability) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Audio Power Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Cut, extract, convert, boost gain, and sequence audio with real DSP processing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Text(
            "Studio Capabilities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(AudioStudioCapability.entries.toTypedArray()) { cap ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clickable { onSelectCapability(cap) }
                        .testTag(cap.tag),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    cap.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                cap.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                cap.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FORMAT CONVERTER CONTENT
// -------------------------------------------------------------
@Composable
fun AudioConverterContent(
    viewModel: AudioConverterViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.selectAudio(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (val state = uiState) {
            is AudioConverterUiState.Idle -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickerLauncher.launch("audio/*") }
                        .testTag("converter_pick_audio"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Select Audio File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Convert between MP3, AAC (M4A), and lossless WAV with bitrate control.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { pickerLauncher.launch("audio/*") },
                            modifier = Modifier.testTag("converter_choose_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Audio")
                        }
                    }
                }
            }

            is AudioConverterUiState.Loaded -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Source Audio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duration: ${VideoTrimmerUtils.formatDuration(state.durationMs)}", style = MaterialTheme.typography.bodySmall)
                            Text("Size: ${VideoTrimmerUtils.formatFileSize(state.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                            Text("Codec: ${state.audioCodec}", style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = { viewModel.togglePreviewPlayback() },
                            modifier = Modifier.fillMaxWidth().testTag("converter_preview_button")
                        ) {
                            Icon(
                                if (state.isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isPreviewPlaying) "Pause Preview" else "Listen to Source")
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Target Output Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioOutputFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = state.targetFormat == format,
                                    onClick = { viewModel.setTargetFormat(format) },
                                    label = { Text(format.title) },
                                    modifier = Modifier.testTag("converter_format_${format.name}")
                                )
                            }
                        }

                        if (state.targetFormat == AudioOutputFormat.MP3) {
                            Text("MP3 Bitrate Preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Mp3Bitrate.entries.forEach { bitrate ->
                                    FilterChip(
                                        selected = state.mp3Bitrate == bitrate,
                                        onClick = { viewModel.setMp3Bitrate(bitrate) },
                                        label = { Text("${bitrate.bitrateKbps}k") }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.convertAndExport() },
                        modifier = Modifier.weight(2f).testTag("converter_start_button")
                    ) {
                        Icon(Icons.Default.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Convert Audio")
                    }
                }
            }

            is AudioConverterUiState.Processing -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            state.statusMessage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is AudioConverterUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conversion Successful!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Saved to Music/PixelRox", style = MaterialTheme.typography.bodySmall)
                        Text("File: ${state.displayName}", fontWeight = FontWeight.SemiBold)
                        Text("Size: ${VideoTrimmerUtils.formatFileSize(state.fileSizeBytes)} | Duration: ${VideoTrimmerUtils.formatDuration(state.durationMs)}")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleResultPlayback() },
                                modifier = Modifier.weight(1f).testTag("converter_play_result")
                            ) {
                                Icon(if (state.isResultPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (state.isResultPlaying) "Pause" else "Play")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = state.format.mimeType
                                        putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Audio"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Convert Another File")
                        }
                    }
                }
            }

            is AudioConverterUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VOLUME BOOSTER CONTENT
// -------------------------------------------------------------
@Composable
fun AudioVolumeContent(
    viewModel: AudioVolumeViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.selectAudio(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (val state = uiState) {
            is AudioVolumeUiState.Idle -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickerLauncher.launch("audio/*") }
                        .testTag("volume_pick_audio"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Select Audio to Boost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Adjust audio gain from 0.5x (-6 dB) to 2.0x (+6 dB) with soft-knee limiting.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { pickerLauncher.launch("audio/*") },
                            modifier = Modifier.testTag("volume_choose_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Audio")
                        }
                    }
                }
            }

            is AudioVolumeUiState.Loaded -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Source Audio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duration: ${VideoTrimmerUtils.formatDuration(state.durationMs)}", style = MaterialTheme.typography.bodySmall)
                            Text("Size: ${VideoTrimmerUtils.formatFileSize(state.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = { viewModel.togglePreviewPlayback() },
                            modifier = Modifier.fillMaxWidth().testTag("volume_preview_button")
                        ) {
                            Icon(
                                if (state.isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isPreviewPlaying) "Pause Preview" else "Listen to Original")
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gain Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                String.format(Locale.US, "%.2fx (%.1f dB)", state.gainFactor, 20.0 * kotlin.math.log10(state.gainFactor.toDouble())),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = state.gainFactor,
                            onValueChange = { viewModel.setGainFactor(it) },
                            valueRange = 0.25f..2.5f,
                            modifier = Modifier.testTag("volume_gain_slider")
                        )

                        Text("Gain Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VolumePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = kotlin.math.abs(state.gainFactor - preset.gainFactor) < 0.05f,
                                    onClick = { viewModel.setGainFactor(preset.gainFactor) },
                                    label = { Text(preset.title.substringBefore(" ")) }
                                )
                            }
                        }

                        Text("Output Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(AudioOutputFormat.MP3, AudioOutputFormat.WAV).forEach { fmt ->
                                FilterChip(
                                    selected = state.targetFormat == fmt,
                                    onClick = { viewModel.setTargetFormat(fmt) },
                                    label = { Text(fmt.title) }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.boostAndExport() },
                        modifier = Modifier.weight(2f).testTag("volume_start_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Process Gain")
                    }
                }
            }

            is AudioVolumeUiState.Processing -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            state.statusMessage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is AudioVolumeUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Volume Gain Applied!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Saved to Music/PixelRox", style = MaterialTheme.typography.bodySmall)
                        Text("File: ${state.displayName}", fontWeight = FontWeight.SemiBold)
                        Text("Gain: ${state.gainFactor}x | Size: ${VideoTrimmerUtils.formatFileSize(state.fileSizeBytes)}")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleResultPlayback() },
                                modifier = Modifier.weight(1f).testTag("volume_play_result")
                            ) {
                                Icon(if (state.isResultPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (state.isResultPlaying) "Pause" else "Play")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = state.format.mimeType
                                        putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Audio"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Boost Another File")
                        }
                    }
                }
            }

            is AudioVolumeUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// AUDIO MERGER CONTENT
// -------------------------------------------------------------
@Composable
fun AudioMergerContent(
    viewModel: AudioMergerViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addTracks(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (val state = uiState) {
            is AudioMergerUiState.Idle -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { multiPickerLauncher.launch("audio/*") }
                        .testTag("merger_pick_audio"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CallMerge,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Select Audio Tracks to Merge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Select 2 or more audio files in any format. Reorder and join into a single continuous track.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { multiPickerLauncher.launch("audio/*") },
                            modifier = Modifier.testTag("merger_choose_button")
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Multiple Tracks")
                        }
                    }
                }
            }

            is AudioMergerUiState.Loaded -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tracks (${state.tracks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Total: ${VideoTrimmerUtils.formatDuration(state.totalDurationMs)}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        state.tracks.forEachIndexed { index, track ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(28.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            track.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "${VideoTrimmerUtils.formatDuration(track.durationMs)} • ${track.audioCodec}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.moveTrackUp(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveTrackDown(index) },
                                        enabled = index < state.tracks.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeTrack(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { multiPickerLauncher.launch("audio/*") },
                            modifier = Modifier.fillMaxWidth().testTag("merger_add_more_tracks")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add More Tracks")
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Output Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(AudioOutputFormat.M4A, AudioOutputFormat.MP3, AudioOutputFormat.WAV).forEach { fmt ->
                                FilterChip(
                                    selected = state.targetFormat == fmt,
                                    onClick = { viewModel.setTargetFormat(fmt) },
                                    label = { Text(fmt.title) }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.mergeAndExport() },
                        enabled = state.tracks.size >= 2,
                        modifier = Modifier.weight(2f).testTag("merger_start_button")
                    ) {
                        Icon(Icons.Default.CallMerge, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Merge Tracks")
                    }
                }
            }

            is AudioMergerUiState.Processing -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            state.statusMessage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is AudioMergerUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tracks Merged Successfully!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Saved to Music/PixelRox", style = MaterialTheme.typography.bodySmall)
                        Text("File: ${state.displayName}", fontWeight = FontWeight.SemiBold)
                        Text("${state.trackCount} Tracks Joined • Duration: ${VideoTrimmerUtils.formatDuration(state.durationMs)} • Size: ${VideoTrimmerUtils.formatFileSize(state.fileSizeBytes)}")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleResultPlayback() },
                                modifier = Modifier.weight(1f).testTag("merger_play_result")
                            ) {
                                Icon(if (state.isResultPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (state.isResultPlaying) "Pause" else "Play")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = state.format.mimeType
                                        putExtra(Intent.EXTRA_STREAM, state.contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Audio"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Merge More Audio")
                        }
                    }
                }
            }

            is AudioMergerUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}
