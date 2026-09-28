package com.example.ui.screens.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class DragGestureType { VOLUME, BRIGHTNESS, SEEK }

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MediaPlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    val playerState by viewModel.playbackState.collectAsState()
    val currentItem by viewModel.currentMediaItem.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val isPipMode by viewModel.isPipMode.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val currentDuration by viewModel.currentDuration.collectAsState()

    var streamUrlInput by remember { mutableStateOf("") }
    var showStreamDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var pendingResumeItem by remember { mutableStateOf<com.example.core.database.entity.media.MediaItemEntity?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Controls visibility states
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Swipe gestured indicators
    var activeGestureType by remember { mutableStateOf<DragGestureType?>(null) }
    var gestureIndicatorValue by remember { mutableFloatStateOf(0f) }
    var gestureSeekTargetMs by remember { mutableLongStateOf(0L) }

    // For seek slider dragging
    var draggingValue by remember { mutableStateOf<Float?>(null) }

    // Single click show/hide controls timer reset on action
    LaunchedEffect(controlsVisible, playerState) {
        if (controlsVisible && playerState is PlayerState.Playing && !isLocked) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Auto-reset gesture indicator after a delay
    LaunchedEffect(activeGestureType, gestureIndicatorValue, gestureSeekTargetMs) {
        if (activeGestureType != null) {
            delay(1500)
            activeGestureType = null
        }
    }

    // Setup WindowInsetsController & Orientation for Fullscreen Mode
    DisposableEffect(isFullscreen) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Handle back button while playing or locked
    BackHandler(enabled = isFullscreen || isLocked) {
        if (isLocked) {
            // Safe prompt or tap to unlock first, prevent exiting accidentally
            scope.launch {
                activeGestureType = null
                // Show floating unlock helper briefly by forcing controls visible
                controlsVisible = true
            }
        } else {
            viewModel.setFullscreen(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    playerViewRef?.onResume()
                    viewModel.exoPlayer.playWhenReady = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    playerViewRef?.onPause()
                    if (activity?.isInPictureInPictureMode != true) {
                        viewModel.exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.saveProgress()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setFullscreen(false)
            playerViewRef?.player = null
            viewModel.stopPlayback()
        }
    }

    fun enterPiP() {
        if (activity is MainActivity) {
            activity.enterPipMode()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val videoFormat = viewModel.exoPlayer.videoFormat
                val width = videoFormat?.width ?: 16
                val height = videoFormat?.height ?: 9
                val rational = if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    if (ratio in 0.42f..2.38f) Rational(width, height) else Rational(16, 9)
                } else Rational(16, 9)

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build()
                try {
                    activity.enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
                val newItem = com.example.core.database.entity.media.MediaItemEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    uri = it.toString(),
                    title = it.lastPathSegment ?: "Local Video",
                    duration = 0L,
                    lastPlayed = System.currentTimeMillis(),
                    lastPosition = 0L,
                    completed = false,
                    mediaType = "LOCAL"
                )
                if (newItem.lastPosition > 0 && !newItem.completed) {
                    pendingResumeItem = newItem
                    showResumeDialog = true
                } else {
                    viewModel.playMedia(newItem)
                }
            }
        }
    )

    // Scaffold removed to eliminate TopAppBar default background and padding.
    // Box used to enable edge-to-edge content.
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val effectivePadding = PaddingValues(0.dp)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.exoPlayer
                        useController = false // Disable default controller completely!
                        keepScreenOn = true
                        playerViewRef = this
                    }
                },
                update = { view ->
                    if (view.player != viewModel.exoPlayer) {
                        view.player = viewModel.exoPlayer
                    }
                    view.resizeMode = resizeMode.modeValue
                    view.keepScreenOn = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("video_player_view")
                    // Gesture handler layer
                    .pointerInput(isLocked, isFullscreen) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            }
                        )
                    }
                    .pointerInput(isLocked, isFullscreen) {
                        val widthPx = size.width
                        val heightPx = size.height
                        var dragStartX = 0f
                        var dragType: DragGestureType? = null
                        var startVolume = 0f
                        var accumulatedVolumeDelta = 0f

                        detectDragGestures(
                            onDragStart = { offset ->
                                if (isLocked) return@detectDragGestures
                                dragStartX = offset.x
                                dragType = null
                                startVolume = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat()
                                accumulatedVolumeDelta = 0f
                            },
                            onDragEnd = {
                                if (isLocked) return@detectDragGestures
                                if (dragType == DragGestureType.SEEK) {
                                    viewModel.seekTo(gestureSeekTargetMs)
                                }
                                activeGestureType = null
                            },
                            onDragCancel = {
                                activeGestureType = null
                            },
                            onDrag = { change, dragAmount ->
                                if (isLocked) return@detectDragGestures
                                change.consume()

                                if (dragType == null) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        dragType = DragGestureType.SEEK
                                        gestureSeekTargetMs = viewModel.exoPlayer.currentPosition
                                    } else {
                                        dragType = if (dragStartX < widthPx / 2) {
                                            DragGestureType.BRIGHTNESS
                                        } else {
                                            DragGestureType.VOLUME
                                        }
                                    }
                                }

                                when (dragType) {
                                    DragGestureType.VOLUME -> {
                                        val maxVolume = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).toFloat()
                                        val delta = (-dragAmount.y / heightPx) * maxVolume * 1.5f
                                        accumulatedVolumeDelta += delta
                                        val targetVolFloat = (startVolume + accumulatedVolumeDelta).coerceIn(0f, maxVolume)
                                        val targetVolInt = targetVolFloat.toInt()

                                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)

                                        activeGestureType = DragGestureType.VOLUME
                                        gestureIndicatorValue = targetVolFloat / maxVolume
                                    }
                                    DragGestureType.BRIGHTNESS -> {
                                        val delta = -dragAmount.y / heightPx
                                        val layoutParams = activity?.window?.attributes
                                        val currentBrightness = layoutParams?.screenBrightness ?: 0.5f
                                        val actualBrightness = if (currentBrightness < 0) 0.5f else currentBrightness
                                        val targetBrightness = (actualBrightness + delta).coerceIn(0.01f, 1f)

                                        layoutParams?.screenBrightness = targetBrightness
                                        activity?.window?.attributes = layoutParams

                                        activeGestureType = DragGestureType.BRIGHTNESS
                                        gestureIndicatorValue = targetBrightness
                                    }
                                    DragGestureType.SEEK -> {
                                        if (currentDuration > 0) {
                                            val deltaMs = (dragAmount.x / widthPx * currentDuration).toLong()
                                            gestureSeekTargetMs = (gestureSeekTargetMs + deltaMs).coerceIn(0L, currentDuration)
                                            activeGestureType = DragGestureType.SEEK
                                            gestureIndicatorValue = gestureSeekTargetMs.toFloat() / currentDuration.toFloat()
                                        }
                                    }
                                    null -> {}
                                }
                            }
                        )
                    }
            )

            // Buffering progress
            if (playerState is PlayerState.Buffering && !isPipMode) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Error display card
            if (playerState is PlayerState.Error && !isPipMode) {
                val err = (playerState as PlayerState.Error).message
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Playback Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.retryPlayback() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Brightness / Volume / Seek Gesture indicators
            AnimatedVisibility(
                visible = activeGestureType != null && !isPipMode,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                activeGestureType?.let { type ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (type) {
                                    DragGestureType.VOLUME -> if (gestureIndicatorValue == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                                    DragGestureType.BRIGHTNESS -> Icons.Default.Brightness5
                                    DragGestureType.SEEK -> Icons.Default.History
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (type) {
                                    DragGestureType.VOLUME -> "Volume: ${(gestureIndicatorValue * 100).toInt()}%"
                                    DragGestureType.BRIGHTNESS -> "Brightness: ${(gestureIndicatorValue * 100).toInt()}%"
                                    DragGestureType.SEEK -> "Seek: ${formatTime(gestureSeekTargetMs)} / ${formatTime(currentDuration)}"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { gestureIndicatorValue },
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // Screen Lock Unlock Floating Button
            if (isLocked && controlsVisible && !isPipMode) {
                Button(
                    onClick = {
                        isLocked = false
                        controlsVisible = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag("video_unlock_button")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Controls", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }


            // Center Actions (Play, pause, skip, rewind)
            AnimatedVisibility(
                visible = controlsVisible && !isPipMode && !isLocked,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val target = (viewModel.exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            viewModel.seekTo(target)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    // Play / Pause central primary button
                    val isPlaying = playerState is PlayerState.Playing
                    IconButton(
                        onClick = {
                            if (isPlaying) viewModel.pause() else viewModel.resume()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.9f), CircleShape)
                            .testTag("video_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            val target = (viewModel.exoPlayer.currentPosition + 10000L).coerceAtMost(currentDuration)
                            viewModel.seekTo(target)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Bottom Actions (Progress bar, seek slider, Aspect, Prev, Next, Speed, Track selectors, Lock)
            AnimatedVisibility(
                visible = controlsVisible && !isPipMode && !isLocked,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Seek Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val activeTime = draggingValue?.let { (it * currentDuration).toLong() } ?: currentPosition
                        Text(
                            text = formatTime(activeTime),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = draggingValue ?: if (currentDuration > 0) currentPosition.toFloat() / currentDuration.toFloat() else 0f,
                            onValueChange = { value ->
                                draggingValue = value
                            },
                            onValueChangeFinished = {
                                draggingValue?.let {
                                    val targetMs = (it * currentDuration).toLong()
                                    viewModel.seekTo(targetMs)
                                }
                                draggingValue = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_progress_slider"),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Text(
                            text = formatTime(currentDuration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Primary controls bar (Prev, Play/Pause, Next, Lock, Aspect, Settings)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left-side buttons: Lock controls
                        IconButton(
                            onClick = {
                                isLocked = true
                                controlsVisible = false
                            },
                            modifier = Modifier.testTag("video_lock_controls")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Screen Controls", tint = Color.White)
                        }

                        // Center buttons: Prev / Next
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.playPrevious() },
                                enabled = viewModel.exoPlayer.hasPreviousMediaItem(),
                                modifier = Modifier.testTag("video_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = if (viewModel.exoPlayer.hasPreviousMediaItem()) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.playNext() },
                                enabled = viewModel.exoPlayer.hasNextMediaItem(),
                                modifier = Modifier.testTag("video_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = if (viewModel.exoPlayer.hasNextMediaItem()) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // Right-side buttons: Aspect, Settings (Playback Speed, Tracks)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aspect Ratio cycle button
                            Button(
                                onClick = { viewModel.cycleResizeMode() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(resizeMode.displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Tracks & Playback speed settings sheet activator
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.testTag("video_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Player Settings", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // Top Controls Overlay (Dedicated Safe Region with Real WindowInsets & Gradient Scrim)
            AnimatedVisibility(
                visible = controlsVisible && !isPipMode && !isLocked,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isFullscreen) {
                                    viewModel.setFullscreen(false)
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.testTag("overlay_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentItem?.title ?: "Local Video Stream",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                IconButton(onClick = { enterPiP() }) {
                                    Icon(
                                        Icons.Default.PictureInPicture,
                                        contentDescription = "PiP",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.toggleFullscreen() }) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playback Network Dialog
        if (showStreamDialog && !isPipMode) {
            AlertDialog(
                onDismissRequest = { showStreamDialog = false },
                title = { Text("Open Network Stream") },
                text = {
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        label = { Text("HTTP / HTTPS / HLS / .m3u8 URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (streamUrlInput.isNotBlank()) {
                                viewModel.playUrl(streamUrlInput)
                                showStreamDialog = false
                                streamUrlInput = ""
                            }
                        })
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (streamUrlInput.isNotBlank()) {
                            viewModel.playUrl(streamUrlInput)
                            showStreamDialog = false
                            streamUrlInput = ""
                        }
                    }) {
                        Text("Play")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStreamDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Resume Playback Confirmation Dialog
        if (showResumeDialog && pendingResumeItem != null && !isPipMode) {
            val item = pendingResumeItem!!
            val posSec = item.lastPosition / 1000
            AlertDialog(
                onDismissRequest = { showResumeDialog = false },
                title = { Text("Resume Playback?") },
                text = { Text("Would you like to resume from $posSec seconds or start over?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.playMedia(item, item.lastPosition)
                        showResumeDialog = false
                        pendingResumeItem = null
                    }) {
                        Text("Resume")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.playMedia(item, 0L)
                        showResumeDialog = false
                        pendingResumeItem = null
                    }) {
                        Text("Start Over")
                    }
                }
            )
        }

        // Modern Premium Player Settings Bottom Sheet
        if (showSettingsSheet && !isPipMode) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                VideoSettingsSheetContent(
                    viewModel = viewModel,
                    currentResizeMode = resizeMode,
                    currentSpeed = playbackSpeed,
                    onDismiss = { showSettingsSheet = false }
                )
            }
        }
    }
}

@Composable
fun VideoSettingsSheetContent(
    viewModel: MediaPlayerViewModel,
    currentResizeMode: VideoResizeMode,
    currentSpeed: Float,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val audioTracks = remember { viewModel.getAudioTracks() }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Speed & Aspect", "Audio Tracks", "Subtitles")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 36.dp)
            .testTag("video_settings_sheet")
    ) {
        Text(
            text = "Playback Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> {
                // Playback speed & Resize configurations
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Speed selection list
                    Text("Playback Speed", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    val speedsRow1 = listOf(0.5f, 0.75f, 1.0f, 1.25f)
                    val speedsRow2 = listOf(1.5f, 1.75f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedsRow1.forEach { speed ->
                            val isSelected = speed == currentSpeed
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                },
                                label = { Text("${speed}x", maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedsRow2.forEach { speed ->
                            val isSelected = speed == currentSpeed
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                },
                                label = { Text("${speed}x", maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Resize options
                    Text("Aspect Ratio Resize Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoResizeMode.entries.forEach { mode ->
                            val isSelected = mode == currentResizeMode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setResizeMode(mode)
                                },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            1 -> {
                // Audio track configuration
                if (audioTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No alternate audio tracks available", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(audioTracks) { track ->
                            ListItem(
                                headlineContent = { Text(track.name, fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal) },
                                supportingContent = track.language?.let { { Text("Language: $it") } },
                                trailingContent = {
                                    if (track.isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.selectTrack(track, C.TRACK_TYPE_AUDIO)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }

            2 -> {
                // Subtitles Configuration
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentTracks = viewModel.exoPlayer.currentTracks
                    val isSubtitlesDisabled = viewModel.exoPlayer.trackSelectionParameters
                        .disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

                    ListItem(
                        headlineContent = { Text("Off", fontWeight = if (isSubtitlesDisabled) FontWeight.Bold else FontWeight.Normal) },
                        trailingContent = {
                            if (isSubtitlesDisabled) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.disableSubtitles()
                                onDismiss()
                            }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                    if (subtitleTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No subtitles / CC available", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(subtitleTracks) { track ->
                                val isSelected = !isSubtitlesDisabled && track.isSelected
                                ListItem(
                                    headlineContent = { Text(track.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    supportingContent = track.language?.let { { Text("Language: $it") } },
                                    trailingContent = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.enableSubtitles()
                                            viewModel.selectTrack(track, C.TRACK_TYPE_TEXT)
                                            onDismiss()
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
