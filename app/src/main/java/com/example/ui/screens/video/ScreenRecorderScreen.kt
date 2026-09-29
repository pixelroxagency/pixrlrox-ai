package com.example.ui.screens.video

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.recorder.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecorderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScreenRecorderViewModel = viewModel()
) {
    val context = LocalContext.current
    val mode by viewModel.recordingMode.collectAsState()
    val quality by viewModel.videoQuality.collectAsState()
    val fps by viewModel.frameRate.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val floatingEnabled by viewModel.floatingControlsEnabled.collectAsState()
    val countdown by viewModel.countdownValue.collectAsState()

    var qualityExpanded by remember { mutableStateOf(false) }
    var fpsExpanded by remember { mutableStateOf(false) }
    var orientationExpanded by remember { mutableStateOf(false) }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenRecorderService::class.java).apply {
                putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecorderService.EXTRA_MODE, mode.ordinal)
                putExtra(ScreenRecorderService.EXTRA_QUALITY, quality.ordinal)
                putExtra(ScreenRecorderService.EXTRA_FPS, fps.ordinal)
                putExtra(ScreenRecorderService.EXTRA_ORIENTATION, orientation.ordinal)
                putExtra(ScreenRecorderService.EXTRA_SHOW_FLOATING, floatingEnabled)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Toast.makeText(context, "Screen Recording Started", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setRecordingState(false)
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun startScreenCaptureFlow() {
        viewModel.startCountdown {
            val captureIntent = projectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        startScreenCaptureFlow()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (floatingEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please allow 'Display over other apps' for floating controls", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                overlayPermissionLauncher.launch(intent)
            } catch (_: Exception) {
                startScreenCaptureFlow()
            }
        } else {
            startScreenCaptureFlow()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || mode == RecordingMode.SCREEN_ONLY || mode == RecordingMode.SCREEN_AUDIO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (floatingEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Please allow 'Display over other apps' for floating controls", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    overlayPermissionLauncher.launch(intent)
                } catch (_: Exception) {
                    startScreenCaptureFlow()
                }
            } else {
                startScreenCaptureFlow()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for this mode", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "Screen Recorder", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(text = "Capture screen and audio seamlessly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(text = "Recording Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    RecordingMode.entries.forEach { m ->
                        Card(
                            onClick = { viewModel.setRecordingMode(m) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (mode == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (mode == m),
                                    onClick = { viewModel.setRecordingMode(m) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = m.title, fontWeight = FontWeight.SemiBold)
                                    Text(text = m.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = "Video Quality", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = qualityExpanded,
                        onExpandedChange = { qualityExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = quality.title,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false }
                        ) {
                            VideoQuality.entries.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q.title) },
                                    onClick = {
                                        viewModel.setVideoQuality(q)
                                        qualityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(text = "Frame Rate", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = fpsExpanded,
                        onExpandedChange = { fpsExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = fps.title,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fpsExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = fpsExpanded,
                            onDismissRequest = { fpsExpanded = false }
                        ) {
                            FrameRate.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.title) },
                                    onClick = {
                                        viewModel.setFrameRate(f)
                                        fpsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(text = "Orientation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = orientationExpanded,
                        onExpandedChange = { orientationExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = orientation.title,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orientationExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = orientationExpanded,
                            onDismissRequest = { orientationExpanded = false }
                        ) {
                            RecordingOrientation.entries.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(o.title) },
                                    onClick = {
                                        viewModel.setOrientation(o)
                                        orientationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Floating recording controls", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = "Show draggable bubble controller over other apps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = floatingEnabled,
                            onCheckedChange = { viewModel.setFloatingControlsEnabled(it) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Save to: Movies / PixelRox / Screen Recordings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (mode == RecordingMode.SCREEN_AUDIO_MIC) {
                                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!hasMic) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Button
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }
                            if (floatingEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Please allow 'Display over other apps' for floating controls", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    overlayPermissionLauncher.launch(intent)
                                } catch (_: Exception) {
                                    startScreenCaptureFlow()
                                }
                                return@Button
                            }
                            startScreenCaptureFlow()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FiberSmartRecord, contentDescription = "Start Recording")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Start Recording", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Countdown Overlay
        if (countdown != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
