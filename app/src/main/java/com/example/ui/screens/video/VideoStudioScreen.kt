package com.example.ui.screens.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.trimmer.VideoTrimmerUtils
import com.example.core.video.FrameImageFormat
import com.example.core.video.VideoCompressorViewModel
import com.example.core.video.VideoCropPreset
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoRotationAngle
import com.example.core.video.VideoSpeedPreset
import com.example.ui.screens.trimmer.VideoTrimmerScreen
import com.example.ui.screens.trimmer.VideoTrimmerViewModel

enum class VideoStudioCapability(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tag: String
) {
    TRIM("Video Trimmer", "Cut and trim clips precisely", Icons.Default.ContentCut, "studio_cap_trim"),
    MUTE("Video Mute", "Remove audio streams completely", Icons.Default.VolumeMute, "studio_cap_mute"),
    ROTATE("Video Rotate", "Rotate 90°, 180°, or 270°", Icons.Default.ScreenRotation, "studio_cap_rotate"),
    CROP("Video Crop", "Crop dimensions and aspect ratios", Icons.Default.CropLandscape, "studio_cap_crop"),
    SPEED("Speed Control", "0.5x to 2.0x fast/slow motion", Icons.Default.FastForward, "studio_cap_speed"),
    FRAME_EXTRACT("Frame Extractor", "Capture high-res still photos", Icons.Default.CropOriginal, "studio_cap_frame"),
    COMPRESS("Video Compressor", "Reduce video size with background export", Icons.Default.Compress, "studio_cap_compress")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStudioScreen(
    initialCapability: VideoStudioCapability? = null,
    onBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit = {}
) {
    var activeCapability by remember { mutableStateOf(initialCapability) }
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vmFactory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)

    // Instantiated child ViewModels delegating their respective operations
    val trimmerVm: VideoTrimmerViewModel = viewModel(factory = vmFactory)
    val muteVm: VideoMuteViewModel = viewModel(factory = vmFactory)
    val rotateVm: VideoRotateViewModel = viewModel(factory = vmFactory)
    val cropVm: VideoCropViewModel = viewModel(factory = vmFactory)
    val speedVm: VideoSpeedViewModel = viewModel(factory = vmFactory)
    val frameVm: VideoFrameExtractorViewModel = viewModel(factory = vmFactory)
    val compressorVm: VideoCompressorViewModel = viewModel(factory = vmFactory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCapability?.title ?: "Video Studio",
                        fontWeight = FontWeight.SemiBold
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
                        modifier = Modifier.testTag("btn_video_studio_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("video_studio_container")
        ) {
            when (activeCapability) {
                null -> {
                    // Studio Hub Dashboard / Capability Grid
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "All-in-One Video Power Tool",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Trim, mute, rotate, crop, adjust speed, extract still frames, and compress videos directly on device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(VideoStudioCapability.entries) { cap ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clickable { activeCapability = cap }
                                        .testTag(cap.tag),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = cap.icon,
                                            contentDescription = cap.title,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = cap.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = cap.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                VideoStudioCapability.TRIM -> {
                    VideoTrimmerScreen(
                        viewModel = trimmerVm,
                        onBack = { activeCapability = null },
                        onPlayVideo = onPlayVideo
                    )
                }

                VideoStudioCapability.MUTE -> {
                    VideoMuteContent(
                        viewModel = muteVm,
                        onBack = { activeCapability = null },
                        onPlayVideo = onPlayVideo
                    )
                }

                VideoStudioCapability.ROTATE -> {
                    VideoRotateContent(
                        viewModel = rotateVm,
                        onBack = { activeCapability = null },
                        onPlayVideo = onPlayVideo
                    )
                }

                VideoStudioCapability.CROP -> {
                    VideoCropContent(
                        viewModel = cropVm,
                        onBack = { activeCapability = null },
                        onPlayVideo = onPlayVideo
                    )
                }

                VideoStudioCapability.SPEED -> {
                    VideoSpeedContent(
                        viewModel = speedVm,
                        onBack = { activeCapability = null },
                        onPlayVideo = onPlayVideo
                    )
                }

                VideoStudioCapability.FRAME_EXTRACT -> {
                    VideoFrameExtractorContent(
                        viewModel = frameVm,
                        onBack = { activeCapability = null }
                    )
                }

                VideoStudioCapability.COMPRESS -> {
                    VideoCompressorScreen(
                        viewModel = compressorVm,
                        onBack = { activeCapability = null },
                        showTopBar = false
                    )
                }
            }
        }
    }
}

@Composable
fun VideoMuteContent(
    viewModel: VideoMuteViewModel,
    onBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is VideoMuteUiState.Idle -> {
                VideoPickerCard(
                    title = "Video Mute Tool",
                    description = "Losslessly remove all audio tracks while preserving video quality and duration.",
                    icon = Icons.Default.VolumeMute,
                    buttonTag = "btn_pick_video_mute",
                    onPick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            is VideoMuteUiState.Ready -> {
                VideoMetadataCard(state.metadata)
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Audio Track Removal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Removes the audio stream losslessly from the container without re-encoding video frames.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Video")
                    }
                    Button(
                        onClick = { viewModel.muteVideo() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_mute_video")
                    ) {
                        Icon(Icons.Default.VolumeMute, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mute Video")
                    }
                }
            }

            is VideoMuteUiState.Processing -> {
                ProcessingCard(title = "Muting Video...", progress = state.progress)
            }

            is VideoMuteUiState.Success -> {
                SuccessCard(
                    title = "Audio Track Removed Successfully!",
                    displayName = state.result.displayName,
                    fileSize = VideoTrimmerUtils.formatFileSize(state.result.fileSizeBytes),
                    duration = VideoTrimmerUtils.formatDuration(state.result.durationMs),
                    destination = "Movies/PixelRox",
                    outputUri = state.result.outputUri,
                    onPlay = { onPlayVideo(state.result.outputUri) },
                    onReset = { viewModel.reset() }
                )
            }

            is VideoMuteUiState.Error -> {
                ErrorCard(message = state.message, onRetry = { viewModel.reset() })
            }
        }
    }
}

@Composable
fun VideoRotateContent(
    viewModel: VideoRotateViewModel,
    onBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is VideoRotateUiState.Idle -> {
                VideoPickerCard(
                    title = "Video Rotate Tool",
                    description = "Rotate video orientation 90°, 180°, or 270° clockwise with hardware frame transformation.",
                    icon = Icons.Default.ScreenRotation,
                    buttonTag = "btn_pick_video_rotate",
                    onPick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            is VideoRotateUiState.Ready -> {
                VideoMetadataCard(state.metadata)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Rotation Angle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoRotationAngle.entries.forEach { angle ->
                        val isSelected = state.selectedAngle == angle
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectAngle(angle) },
                            label = { Text(angle.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_rotate_${angle.degrees}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Video")
                    }
                    Button(
                        onClick = { viewModel.rotateVideo() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_rotate_video")
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate Video")
                    }
                }
            }

            is VideoRotateUiState.Processing -> {
                ProcessingCard(title = "Rotating Video Frames...", progress = state.progress)
            }

            is VideoRotateUiState.Success -> {
                SuccessCard(
                    title = "Video Rotated by ${state.angle.label}!",
                    displayName = state.result.displayName,
                    fileSize = VideoTrimmerUtils.formatFileSize(state.result.fileSizeBytes),
                    duration = VideoTrimmerUtils.formatDuration(state.result.durationMs),
                    destination = "Movies/PixelRox",
                    outputUri = state.result.outputUri,
                    onPlay = { onPlayVideo(state.result.outputUri) },
                    onReset = { viewModel.reset() }
                )
            }

            is VideoRotateUiState.Error -> {
                ErrorCard(message = state.message, onRetry = { viewModel.reset() })
            }
        }
    }
}

@Composable
fun VideoCropContent(
    viewModel: VideoCropViewModel,
    onBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is VideoCropUiState.Idle -> {
                VideoPickerCard(
                    title = "Video Crop Tool",
                    description = "Crop video frame boundaries into standard aspect ratios (1:1, 4:5, 9:16, 16:9).",
                    icon = Icons.Default.CropLandscape,
                    buttonTag = "btn_pick_video_crop",
                    onPick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            is VideoCropUiState.Ready -> {
                VideoMetadataCard(state.metadata)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Target Aspect Ratio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    items(VideoCropPreset.entries.filter { it != VideoCropPreset.CUSTOM }) { preset ->
                        val isSelected = state.selectedPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPreset(preset) },
                            label = { Text(preset.title) },
                            modifier = Modifier.testTag("chip_crop_${preset.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Video")
                    }
                    Button(
                        onClick = { viewModel.cropVideo() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_crop_video")
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crop Video")
                    }
                }
            }

            is VideoCropUiState.Processing -> {
                ProcessingCard(title = "Cropping Video Boundaries...", progress = state.progress)
            }

            is VideoCropUiState.Success -> {
                SuccessCard(
                    title = "Video Cropped (${state.preset.title})!",
                    displayName = state.result.displayName,
                    fileSize = VideoTrimmerUtils.formatFileSize(state.result.fileSizeBytes),
                    duration = VideoTrimmerUtils.formatDuration(state.result.durationMs),
                    destination = "Movies/PixelRox",
                    outputUri = state.result.outputUri,
                    onPlay = { onPlayVideo(state.result.outputUri) },
                    onReset = { viewModel.reset() }
                )
            }

            is VideoCropUiState.Error -> {
                ErrorCard(message = state.message, onRetry = { viewModel.reset() })
            }
        }
    }
}

@Composable
fun VideoSpeedContent(
    viewModel: VideoSpeedViewModel,
    onBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is VideoSpeedUiState.Idle -> {
                VideoPickerCard(
                    title = "Video Speed Control",
                    description = "Create slow motion (0.5x, 0.75x) or fast motion (1.25x, 1.5x, 2.0x) videos with synchronized timestamps.",
                    icon = Icons.Default.FastForward,
                    buttonTag = "btn_pick_video_speed",
                    onPick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            is VideoSpeedUiState.Ready -> {
                VideoMetadataCard(state.metadata)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Playback Speed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    items(VideoSpeedPreset.entries) { speed ->
                        val isSelected = state.selectedSpeed == speed
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectSpeed(speed) },
                            label = { Text(speed.label) },
                            modifier = Modifier.testTag("chip_speed_${(speed.multiplier * 100).toInt()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Video")
                    }
                    Button(
                        onClick = { viewModel.applySpeed() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_apply_speed")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Speed")
                    }
                }
            }

            is VideoSpeedUiState.Processing -> {
                ProcessingCard(title = "Adjusting Playback Speed...", progress = state.progress)
            }

            is VideoSpeedUiState.Success -> {
                SuccessCard(
                    title = "Speed Adjusted (${state.speed.label})!",
                    displayName = state.result.displayName,
                    fileSize = VideoTrimmerUtils.formatFileSize(state.result.fileSizeBytes),
                    duration = VideoTrimmerUtils.formatDuration(state.result.durationMs),
                    destination = "Movies/PixelRox",
                    outputUri = state.result.outputUri,
                    onPlay = { onPlayVideo(state.result.outputUri) },
                    onReset = { viewModel.reset() }
                )
            }

            is VideoSpeedUiState.Error -> {
                ErrorCard(message = state.message, onRetry = { viewModel.reset() })
            }
        }
    }
}

@Composable
fun VideoFrameExtractorContent(
    viewModel: VideoFrameExtractorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = uiState) {
            is VideoFrameExtractorUiState.Idle -> {
                VideoPickerCard(
                    title = "Video Frame Extractor",
                    description = "Extract pristine, full-resolution still frames from any timestamp into JPEG or PNG.",
                    icon = Icons.Default.CropOriginal,
                    buttonTag = "btn_pick_video_frame",
                    onPick = {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
            }

            is VideoFrameExtractorUiState.Ready -> {
                VideoMetadataCard(state.metadata)
                Spacer(modifier = Modifier.height(16.dp))

                // Frame Preview Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.previewBitmap != null) {
                            Image(
                                bitmap = state.previewBitmap.asImageBitmap(),
                                contentDescription = "Extracted Frame Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        } else if (state.isExtractingPreview) {
                            CircularProgressIndicator()
                        } else {
                            Text("No frame preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Timestamp: ${VideoTrimmerUtils.formatDuration(state.selectedTimestampMs)} / ${VideoTrimmerUtils.formatDuration(state.metadata.durationMs)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = state.selectedTimestampMs.toFloat(),
                    onValueChange = { viewModel.onTimestampChanged(it.toLong()) },
                    valueRange = 0f..state.metadata.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_frame_timestamp")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameImageFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.selectedFormat == fmt,
                            onClick = { viewModel.selectFormat(fmt) },
                            label = { Text(fmt.name) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chip_frame_fmt_${fmt.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Video")
                    }
                    Button(
                        onClick = { viewModel.extractAndSaveFrame() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_action_extract_frame")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Frame")
                    }
                }
            }

            is VideoFrameExtractorUiState.Extracting -> {
                ProcessingCard(
                    title = "Extracting Full-Resolution Frame...",
                    progress = 50
                )
            }

            is VideoFrameExtractorUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Frame Saved to Pictures/PixelRox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${state.result.displayName} (${state.result.width}x${state.result.height})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, state.result.outputUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Extracted Frame"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_share_frame")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                            Button(
                                onClick = { viewModel.reset() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_extract_another_frame")
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            is VideoFrameExtractorUiState.Error -> {
                ErrorCard(message = state.message, onRetry = { viewModel.reset() })
            }
        }
    }
}

@Composable
private fun VideoPickerCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonTag: String,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPick,
                modifier = Modifier.testTag(buttonTag)
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Video")
            }
        }
    }
}

@Composable
private fun VideoMetadataCard(metadata: VideoMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = metadata.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Duration: ${VideoTrimmerUtils.formatDuration(metadata.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Size: ${VideoTrimmerUtils.formatFileSize(metadata.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${metadata.width}x${metadata.height}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProcessingCard(title: String, progress: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("$progress%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SuccessCard(
    title: String,
    displayName: String,
    fileSize: String,
    duration: String,
    destination: String,
    outputUri: Uri,
    onPlay: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$displayName • $fileSize • $duration\nSaved to $destination",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_play_processed_video")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, outputUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_share_video")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReset) {
                Text("Process Another Video")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Operation Failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}
