package com.example.ui.screens.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.entity.download.DownloadEntity
import kotlinx.coroutines.launch
import com.example.data.downloader.extractor.DownloaderDiagnostics
import com.example.data.downloader.extractor.DownloadDiagnostics
import com.example.data.downloader.extractor.DownloaderDiagnosticsRegistry
import com.example.data.downloader.model.AnalysisState
import com.example.data.downloader.model.DownloadFormat
import com.example.data.downloader.model.MediaAnalysisResult
import com.example.data.downloader.model.MediaType
import com.example.data.repository.DownloadRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: DownloadRepository,
    preferencesRepository: com.example.data.repository.PreferencesRepository,
    sharedViewModel: DownloadsViewModel? = null,
    initialSharedUrl: String? = null,
    onNavigateToMedia: (String) -> Unit = {},
    onNavigatePdfReader: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val viewModel: DownloadsViewModel = sharedViewModel ?: viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DownloadsViewModel(repository, preferencesRepository) as T
                }
            }
        }
    )

    // Collect states
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val inputUrl by viewModel.inputUrl.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val mediaFilterType by viewModel.mediaFilterType.collectAsStateWithLifecycle()
    val displayProgressMap by viewModel.displayProgressMap.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val diagnostics by DownloaderDiagnosticsRegistry.diagnostics.collectAsStateWithLifecycle()
    val downloadDiagnosticsMap by com.example.data.downloader.extractor.DownloadDiagnosticsRegistry.diagnosticsMap.collectAsStateWithLifecycle(emptyMap())

    val snackbarHostState = remember { SnackbarHostState() }
    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            viewModel.setInitialSharedUrl(initialSharedUrl)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Direct Media Downloader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Device Direct Downloads • No Login Required",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (analysisState !is AnalysisState.Idle) {
                        IconButton(onClick = { viewModel.clearAnalysis() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Form")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Tab Navigation: [Analyze & Download] | [Active (X)] | [Completed (Y)]
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("Analyze & Download") },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active")
                            if (activeDownloads.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge { Text("${activeDownloads.size}") }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Downloading, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Completed")
                            if (completedDownloads.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge { Text("${completedDownloads.size}") }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> AnalyzeAndDownloadView(
                    inputUrl = inputUrl,
                    analysisState = analysisState,
                    mediaFilterType = mediaFilterType,
                    diagnostics = diagnostics,
                    onUrlChange = { viewModel.onUrlChange(it) },
                    onPasteFromClipboard = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            viewModel.onUrlChange(clip.trim())
                        }
                    },
                    onAnalyze = { viewModel.analyzeUrl() },
                    onSetMediaFilter = { viewModel.setMediaFilter(it) },
                    onStartDownload = { format, title ->
                        viewModel.startDownload(
                            format = format,
                            customTitle = title,
                            sourceUrl = inputUrl
                        )
                    }
                )
                1 -> ActiveDownloadsView(
                    activeDownloads = activeDownloads,
                    displayProgressMap = displayProgressMap,
                    onCancel = { viewModel.cancelDownload(it) },
                    onPause = { viewModel.pauseDownload(it) },
                    onResume = { viewModel.resumeDownload(it) },
                    onNavigateToAnalyze = { viewModel.setSelectedTab(0) }
                )
                2 -> CompletedDownloadsView(
                    completedDownloads = completedDownloads,
                    context = context,
                    downloadDiagnosticsMap = downloadDiagnosticsMap,
                    onOpenMedia = { entity ->
                        openMediaItem(context, entity, onNavigateToMedia, onNavigatePdfReader)
                    },
                    onShareMedia = { entity ->
                        shareMediaItem(context, entity)
                    },
                    onDeleteRequest = { entity ->
                        itemToDelete = entity
                    },
                    onNavigateToAnalyze = { viewModel.setSelectedTab(0) }
                )
            }
        }

        // Confirmation dialog for deletion
        if (itemToDelete != null) {
            val item = itemToDelete!!
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Downloaded File?") },
                text = {
                    Text("Are you sure you want to permanently delete \"${item.filename}\" from device storage and remove it from download history?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteDownload(item.id, deleteFile = true)
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun AnalyzeAndDownloadView(
    inputUrl: String,
    analysisState: AnalysisState,
    mediaFilterType: MediaType,
    diagnostics: DownloaderDiagnostics? = null,
    onUrlChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onAnalyze: () -> Unit,
    onSetMediaFilter: (MediaType) -> Unit,
    onStartDownload: (DownloadFormat, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top URL Input Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Paste Media Link",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = onUrlChange,
                        placeholder = { Text("https://example.com/video.mp4 or .m3u8") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingIcon = {
                            if (inputUrl.isNotBlank()) {
                                IconButton(onClick = { onUrlChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = onPasteFromClipboard) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPasteFromClipboard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste")
                        }

                        Button(
                            onClick = onAnalyze,
                            enabled = inputUrl.isNotBlank() && analysisState !is AnalysisState.Analyzing,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (analysisState is AnalysisState.Analyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analyze")
                            }
                        }
                    }
                }
            }
        }

        // Analysis State Presentation (Never silently fail)
        item {
            when (analysisState) {
                is AnalysisState.Idle -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Supported Direct Download Sources",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Public Social: TikTok, Instagram Reels, Facebook (public links)", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Direct Video: HTTPS MP4, WebM, MKV, MOV, AVI, 3GP", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Direct Audio: HTTPS MP3, M4A, AAC, WAV, FLAC, OGG, OPUS", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Documents & Files: PDF, Images (PNG, JPG, WebP), Archives (ZIP)", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Streaming Playlists: .m3u8 with quality selection", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Note: Only publicly accessible content without login is supported on-device. Private posts, login walls, and YouTube are not supported.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is AnalysisState.Analyzing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Analyzing Media Stream & Formats...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Checking stream resolution, format, and content headers", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is AnalysisState.ResolverRequired -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("This source can't be downloaded directly on this device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                is AnalysisState.AuthRequired -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Authentication Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                is AnalysisState.PrivateOrRestricted -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Private or Restricted Content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                is AnalysisState.DrmProtected -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("DRM Protected Content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                is AnalysisState.NetworkError -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Network Error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AnalysisState.ExtractionFailed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Extraction Failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AnalysisState.DirectMedia -> {
                    AnalysisResultCard(
                        result = analysisState.result,
                        mediaFilterType = mediaFilterType,
                        onSetMediaFilter = onSetMediaFilter,
                        onStartDownload = onStartDownload
                    )
                }
                is AnalysisState.PublicMediaPage -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Public Media Page", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                is AnalysisState.Unsupported -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Unsupported Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                is AnalysisState.InvalidUrl -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Invalid URL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AnalysisState.DownloadUnavailable -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Download Unavailable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AnalysisState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Analysis Error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(analysisState.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AnalysisState.Success -> {
                    AnalysisResultCard(
                        result = analysisState.result,
                        mediaFilterType = mediaFilterType,
                        onSetMediaFilter = onSetMediaFilter,
                        onStartDownload = onStartDownload
                    )
                }
            }
        }

        val isFailureState = analysisState !is AnalysisState.Idle &&
                analysisState !is AnalysisState.Analyzing &&
                analysisState !is AnalysisState.Success &&
                analysisState !is AnalysisState.DirectMedia

        if (diagnostics != null && isFailureState) {
            item {
                DownloaderDebugDetails(diagnostics = diagnostics, downloadDiagnostics = null)
            }
        }
    }
}

@Composable
private fun AnalysisResultCard(
    result: MediaAnalysisResult,
    mediaFilterType: MediaType,
    onSetMediaFilter: (MediaType) -> Unit,
    onStartDownload: (DownloadFormat, String) -> Unit
) {
    val videoFormats = remember(result) {
        result.formats
            .filter { it.mediaType == MediaType.VIDEO }
            .groupBy { it.resolution }
            .map { (resolution, formats) ->
                formats.sortedWith(
                    compareByDescending<DownloadFormat> { it.audioIncluded }
                        .thenByDescending { it.container.equals("MP4", ignoreCase = true) }
                        .thenByDescending { it.bitrate }
                        .thenByDescending { it.sizeBytes }
                ).first()
            }
            .sortedWith { f1, f2 ->
                val r1 = f1.resolution.filter { it.isDigit() }.toIntOrNull() ?: 0
                val r2 = f2.resolution.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (r1 != r2) r2.compareTo(r1) else f1.resolution.compareTo(f2.resolution)
            }
    }
    val audioFormats = remember(result) { result.formats.filter { it.mediaType == MediaType.AUDIO } }
    val fileFormats = remember(result) { result.formats.filter { it.mediaType == MediaType.FILE } }
    val displayedFormats = remember(result, mediaFilterType) {
        when (mediaFilterType) {
            MediaType.VIDEO -> videoFormats
            MediaType.AUDIO -> audioFormats
            MediaType.FILE -> fileFormats
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Media Title & Source Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val iconVector = when {
                        fileFormats.isNotEmpty() && mediaFilterType == MediaType.FILE -> Icons.Default.InsertDriveFile
                        audioFormats.isNotEmpty() && mediaFilterType == MediaType.AUDIO -> Icons.Default.MusicNote
                        videoFormats.isNotEmpty() -> Icons.Default.Videocam
                        else -> Icons.Default.Download
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Source: ${result.source}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        result.durationSeconds?.let { secs ->
                            if (secs > 0.0) {
                                val totalSecs = secs.toLong()
                                val hours = totalSecs / 3600
                                val minutes = (totalSecs % 3600) / 60
                                val seconds = totalSecs % 60
                                val durationStr = if (hours > 0) {
                                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                } else {
                                    String.format("%02d:%02d", minutes, seconds)
                                }
                                Text(
                                    " • $durationStr",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Format Selector Chips (VIDEO, AUDIO, FILES)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (videoFormats.isNotEmpty() || (audioFormats.isEmpty() && fileFormats.isEmpty())) {
                FilterChip(
                    selected = mediaFilterType == MediaType.VIDEO,
                    onClick = { onSetMediaFilter(MediaType.VIDEO) },
                    label = { Text("Video (${videoFormats.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (audioFormats.isNotEmpty()) {
                FilterChip(
                    selected = mediaFilterType == MediaType.AUDIO,
                    onClick = { onSetMediaFilter(MediaType.AUDIO) },
                    label = { Text("Audio (${audioFormats.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (fileFormats.isNotEmpty()) {
                FilterChip(
                    selected = mediaFilterType == MediaType.FILE,
                    onClick = { onSetMediaFilter(MediaType.FILE) },
                    label = { Text("Files (${fileFormats.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Available Formats
        if (displayedFormats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mediaFilterType) {
                        MediaType.VIDEO -> "No downloadable video formats found"
                        MediaType.AUDIO -> "No downloadable audio formats found"
                        MediaType.FILE -> "No downloadable file formats found"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            displayedFormats.forEach { format ->
                FormatOptionCard(
                    format = format,
                    title = result.title,
                    onDownloadClick = { onStartDownload(format, result.title) }
                )
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    format: DownloadFormat,
    title: String,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (format.resolution.isNotBlank()) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(format.resolution, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        format.container.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (format.codec.isNotBlank()) {
                        Text(
                            " • ${format.codec}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val showSize = format.sizeBytes > 0 || format.bitrate > 0
                    if (showSize) {
                        val sizeText = if (format.sizeBytes > 0) format.getFormattedSize() else format.getFormattedBitrate()
                        Text(
                            sizeText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (format.mediaType == MediaType.VIDEO) {
                        val finalOutputHasAudio = format.audioIncluded || format.separateAudioAvailable || format.mergeRequired
                        val labelText = if (finalOutputHasAudio) "Video • Audio included" else "Video only"
                        val prefix = if (showSize) " • " else ""
                        Text(
                            "$prefix$labelText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (format.mediaType == MediaType.AUDIO) {
                        val prefix = if (showSize) " • " else ""
                        val bitrateSuffix = if (format.bitrate > 0) " • ${format.getFormattedBitrate()}" else ""
                        Text(
                            "${prefix}Audio only$bitrateSuffix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download")
            }
        }
    }
}

@Composable
private fun ActiveDownloadsView(
    activeDownloads: List<DownloadEntity>,
    displayProgressMap: Map<String, Int>,
    onCancel: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onNavigateToAnalyze: () -> Unit
) {
    if (activeDownloads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No active downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Paste a link to start downloading video or audio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToAnalyze) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Download")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(activeDownloads, key = { it.id }) { item ->
                val displayProgress = displayProgressMap[item.id] ?: item.progress
                ActiveDownloadCard(
                    item = item,
                    displayProgress = displayProgress,
                    onCancel = { onCancel(item.id) },
                    onPause = { onPause(item.id) },
                    onResume = { onResume(item.id) }
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    item: DownloadEntity,
    displayProgress: Int,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val statusText = when {
                item.status == "QUEUED" -> "Preparing download…"
                item.status == "PREPARING" -> "Resolving media…"
                item.status == "DOWNLOADING" && displayProgress < 5 -> "Starting download…"
                item.status == "DOWNLOADING" && item.progress > 95 -> "Merging audio & video…"
                item.status == "DOWNLOADING" -> "Downloading…"
                item.status == "PAUSED" -> "Paused"
                else -> item.status.lowercase().replaceFirstChar { it.uppercase() }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = when (item.mediaType) {
                            "AUDIO" -> Icons.Default.MusicNote
                            "FILE" -> Icons.Default.InsertDriveFile
                            else -> Icons.Default.Videocam
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        item.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Badge(
                    containerColor = when (item.status) {
                        "PREPARING" -> MaterialTheme.colorScheme.tertiaryContainer
                        "DOWNLOADING" -> MaterialTheme.colorScheme.primaryContainer
                        "PAUSED" -> MaterialTheme.colorScheme.tertiaryContainer
                        "QUEUED" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (displayProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadedMb = if (item.downloadedBytes > 0) String.format("%.1f MB", item.downloadedBytes / (1024.0 * 1024.0)) else ""
                val totalMb = if (item.totalBytes > 0) String.format("%.1f MB", item.totalBytes / (1024.0 * 1024.0)) else ""
                val sizeInfo = when {
                    downloadedMb.isNotBlank() && totalMb.isNotBlank() -> "$downloadedMb / $totalMb"
                    totalMb.isNotBlank() -> totalMb
                    else -> ""
                }

                Text(
                    text = "${displayProgress}% ${if (sizeInfo.isNotBlank()) "• $sizeInfo" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (item.status == "DOWNLOADING") {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                        }
                    } else if (item.status == "PAUSED") {
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedDownloadsView(
    completedDownloads: List<DownloadEntity>,
    context: Context,
    downloadDiagnosticsMap: Map<String, com.example.data.downloader.extractor.DownloadDiagnostics>,
    onOpenMedia: (DownloadEntity) -> Unit,
    onShareMedia: (DownloadEntity) -> Unit,
    onDeleteRequest: (DownloadEntity) -> Unit,
    onNavigateToAnalyze: () -> Unit
) {
    if (completedDownloads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No downloaded media yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Completed videos and audio files will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToAnalyze) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Media")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(completedDownloads, key = { it.id }) { item ->
                CompletedDownloadCard(
                    item = item,
                    diagnostics = downloadDiagnosticsMap[item.id],
                    context = context,
                    clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current,
                    onOpen = { onOpenMedia(item) },
                    onShare = { onShareMedia(item) },
                    onDelete = { onDeleteRequest(item) }
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    item: DownloadEntity,
    diagnostics: com.example.data.downloader.extractor.DownloadDiagnostics? = null,
    context: Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val fileSizeStr = remember(item.fileSize, item.totalBytes) {
        val bytes = if (item.fileSize > 0) item.fileSize else item.totalBytes
        if (bytes > 0) {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.1f MB", mb)
        } else "Saved"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status != "FAILED") { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.status == "FAILED") {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.status == "FAILED") {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                when (item.mediaType) {
                                    "AUDIO" -> MaterialTheme.colorScheme.secondaryContainer
                                    "FILE" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.status == "FAILED") {
                            Icons.Default.Error
                        } else {
                            when (item.mediaType) {
                                "AUDIO" -> Icons.Default.Audiotrack
                                "FILE" -> Icons.Default.InsertDriveFile
                                else -> Icons.Default.Movie
                            }
                        },
                        contentDescription = null,
                        tint = if (item.status == "FAILED") {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            when (item.mediaType) {
                                "AUDIO" -> MaterialTheme.colorScheme.onSecondaryContainer
                                "FILE" -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.filename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.quality.isNotBlank()) {
                            Text(
                                item.quality,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(" • ", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            fileSizeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(" • ", style = MaterialTheme.typography.labelSmall)
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Explicit Actions: Play/Open, Share, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status != "FAILED") {
                    FilledTonalButton(
                        onClick = onOpen,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (item.mediaType == "AUDIO" || item.mediaType == "VIDEO") Icons.Default.PlayArrow else Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play / Open", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Text(
                        text = "Download Failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (item.status == "FAILED" && diagnostics != null) {
                Spacer(modifier = Modifier.height(10.dp))
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (expanded) "Hide Debug Details" else "Show Debug Details", fontSize = 11.sp)
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DEBUG DOWNLOAD DETAILS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                CopyButton(
                                    onClick = {
                                        val report = com.example.data.downloader.extractor.DiagnosticReportFormatter.format(null, downloadDiagnostics = diagnostics)
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(report))
                                        android.widget.Toast.makeText(context, "Debug info copied", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val lines = listOf(
                                "DownloadId" to diagnostics.downloadId,
                                "SourcePlatform" to diagnostics.sourcePlatform,
                                "SelectedResolution" to diagnostics.selectedResolution,
                                "SelectedFormatId" to diagnostics.selectedFormatId,
                                "ExpectedMimeType" to diagnostics.expectedMimeType,
                                "ExpectedFileSize" to diagnostics.expectedFileSize,
                                "ResolvedUrlPresent" to diagnostics.resolvedUrlPresent,
                                "ResolvedUrlScheme" to diagnostics.resolvedUrlScheme,
                                "ResolvedUrlHost" to diagnostics.resolvedUrlHost,
                                "ResolvedUrlExpiredSuspected" to diagnostics.resolvedUrlExpiredSuspected,
                                "RequestUserAgentPresent" to diagnostics.requestUserAgentPresent,
                                "RequestRefererPresent" to diagnostics.requestRefererPresent,
                                "RequestCookieRequired" to diagnostics.requestCookieRequired,
                                "RequestCookiePresent" to diagnostics.requestCookiePresent,
                                "ExtractorHeadersCount" to diagnostics.extractorHeadersCount,
                                "HeadersPassedToDownloadManager" to diagnostics.headersPassedToDownloadManager,
                                "MissingRequiredHeaders" to diagnostics.missingRequiredHeaders,
                                "DownloadManagerStatus" to diagnostics.downloadManagerStatus,
                                "DownloadManagerReasonCode" to diagnostics.downloadManagerReasonCode,
                                "DownloadManagerReasonName" to diagnostics.downloadManagerReasonName,
                                "LocalUriPresent" to diagnostics.localUriPresent,
                                "DownloadedBytes" to diagnostics.downloadedBytes,
                                "TotalExpectedBytes" to diagnostics.totalExpectedBytes,
                                "FailureStage" to diagnostics.failureStage,
                                "FinalDownloadError" to diagnostics.finalDownloadError,
                                "ResolvedUrlHasExpiryParameter" to diagnostics.resolvedUrlHasExpiryParameter,
                                "ResolvedUrlHasSignatureParameters" to diagnostics.resolvedUrlHasSignatureParameters,
                                "ResolvedUrlAgeAtEnqueueMs" to diagnostics.resolvedUrlAgeAtEnqueueMs,
                                "AnalyzeToDownloadDelayMs" to diagnostics.analyzeToDownloadDelayMs
                            )
                            lines.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers for opening and sharing media files
private fun openMediaItem(
    context: Context,
    entity: DownloadEntity,
    onNavigateToMedia: (String) -> Unit,
    onNavigatePdfReader: () -> Unit
) {
    if (entity.destinationUri.isBlank()) return

    val isContentUri = entity.destinationUri.startsWith("content://")
    val fileExists = if (isContentUri) {
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(entity.destinationUri), "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    } else {
        File(entity.destinationUri).exists()
    }

    val lowerName = entity.filename.lowercase()
    if (lowerName.endsWith(".pdf")) {
        onNavigatePdfReader()
        return
    }

    if (fileExists && (entity.mediaType == "VIDEO" || entity.mediaType == "AUDIO" || lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") || lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") || lowerName.endsWith(".mkv"))) {
        val playPath = if (isContentUri) entity.destinationUri else File(entity.destinationUri).absolutePath
        onNavigateToMedia(playPath)
        return
    }

    // Fallback to system FileProvider view intent
    if (fileExists) {
        try {
            val contentUri = if (isContentUri) {
                Uri.parse(entity.destinationUri)
            } else {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entity.destinationUri))
            }
            val mimeType = when {
                lowerName.endsWith(".mp4") -> "video/mp4"
                lowerName.endsWith(".webm") -> "video/webm"
                lowerName.endsWith(".mp3") -> "audio/mpeg"
                lowerName.endsWith(".m4a") -> "audio/mp4"
                lowerName.endsWith(".pdf") -> "application/pdf"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

private fun shareMediaItem(context: Context, entity: DownloadEntity) {
    if (entity.destinationUri.isBlank()) return

    val isContentUri = entity.destinationUri.startsWith("content://")
    val fileExists = if (isContentUri) {
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(entity.destinationUri), "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    } else {
        File(entity.destinationUri).exists()
    }
    if (!fileExists) return

    try {
        val contentUri = if (isContentUri) {
            Uri.parse(entity.destinationUri)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entity.destinationUri))
        }
        val mimeType = when {
            entity.filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            entity.filename.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            entity.filename.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            else -> "*/*"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Downloaded File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {}
}

@Composable
private fun CopyButton(
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Copy")
    }
}
@Composable
private fun DownloaderDebugDetails(
    diagnostics: DownloaderDiagnostics,
    downloadDiagnostics: com.example.data.downloader.extractor.DownloadDiagnostics? = null,
    viewModel: DownloadsViewModel? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEBUG DETAILS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                CopyButton(
                    onClick = {
                        scope.launch {
                            val freshDownloadDiagnostics = if (viewModel != null && downloadDiagnostics != null) {
                                viewModel.refreshDownloadDiagnostics(downloadDiagnostics.downloadId)
                            } else {
                                downloadDiagnostics
                            }
                            
                            val report = com.example.data.downloader.extractor.DiagnosticReportFormatter.format(
                                diagnostics, 
                                freshDownloadDiagnostics
                            )
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(report))
                            android.widget.Toast.makeText(context, "Debug info copied (with fresh status)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            val debugLines = mutableListOf<Pair<String, String>>()
            debugLines.addAll(listOf(
                "SourcePlatform" to diagnostics.sourcePlatform,
                "ExtractorSelected" to diagnostics.extractorSelected,
                "ExtractorAttemptOrder" to diagnostics.extractorAttemptOrder,
                "ExtractorReached" to diagnostics.extractorReached,
                "HttpStatus" to diagnostics.httpStatus,
                "RedirectCount" to diagnostics.redirectCount,
                "ResponseContentType" to diagnostics.responseContentType,
                "FailureCategory" to diagnostics.failureCategory,
                "UnderlyingExceptionClass" to diagnostics.underlyingExceptionClass,
                "UnderlyingExceptionMessage" to diagnostics.underlyingExceptionMessage,
                "RateLimitDetected" to diagnostics.rateLimitDetected,
                "BotChallengeDetected" to diagnostics.botChallengeDetected,
                "VerificationChallengeDetected" to diagnostics.verificationChallengeDetected,
                "NoMediaFound" to diagnostics.noMediaFound,
                "UnsupportedSource" to diagnostics.unsupportedSource,
                "TemporaryExtractionFailure" to diagnostics.temporaryExtractionFailure,
                "YtDlpFallbackInvoked" to diagnostics.ytDlpFallbackInvoked,
                "YtDlpInitialized" to diagnostics.ytDlpInitialized,
                "YtDlpExecutionReached" to diagnostics.ytDlpExecutionReached,
                "FinalMappedUserMessage" to diagnostics.finalMappedUserMessage,
                "YtDlpExceptionClass" to diagnostics.ytDlpExceptionClass,
                "YtDlpExceptionMessage" to diagnostics.ytDlpExceptionMessage,
                "YtDlpCauseClass" to diagnostics.ytDlpCauseClass,
                "YtDlpCauseMessage" to diagnostics.ytDlpCauseMessage,
                "YtDlpExitCode" to diagnostics.ytDlpExitCode,
                "YtDlpStdoutSummary" to diagnostics.ytDlpStdoutSummary,
                "YtDlpStderrSummary" to diagnostics.ytDlpStderrSummary,
                "YtDlpResultSuccess" to diagnostics.ytDlpResultSuccess,
                "YtDlpInfoExtractionSucceeded" to diagnostics.ytDlpInfoExtractionSucceeded,
                "YtDlpFormatsCount" to diagnostics.ytDlpFormatsCount,
                "NetworkTimeoutDetected" to diagnostics.networkTimeoutDetected,
                "YtDlpAttemptCount" to diagnostics.ytDlpAttemptCount,
                "YtDlpRetryPerformed" to diagnostics.ytDlpRetryPerformed,
                "YtDlpSocketTimeoutSeconds" to diagnostics.ytDlpSocketTimeoutSeconds
            ))
            
            downloadDiagnostics?.let { d ->
                debugLines.addAll(listOf(
                    "DownloadId" to d.downloadId,
                    "SelectedResolution" to d.selectedResolution,
                    "SelectedFormatId" to d.selectedFormatId,
                    "ExpectedMimeType" to d.expectedMimeType,
                    "ExpectedFileSize" to d.expectedFileSize,
                    "ResolvedUrlPresent" to d.resolvedUrlPresent,
                    "ResolvedUrlScheme" to d.resolvedUrlScheme,
                    "ResolvedUrlHost" to d.resolvedUrlHost,
                    "ResolvedUrlExpiredSuspected" to d.resolvedUrlExpiredSuspected,
                    "RequestUserAgentPresent" to d.requestUserAgentPresent,
                    "RequestRefererPresent" to d.requestRefererPresent,
                    "RequestCookieRequired" to d.requestCookieRequired,
                    "RequestCookiePresent" to d.requestCookiePresent,
                    "ExtractorHeadersCount" to d.extractorHeadersCount,
                    "HeadersPassedToDownloadManager" to d.headersPassedToDownloadManager,
                    "MissingRequiredHeaders" to d.missingRequiredHeaders,
                    "DownloadManagerStatus" to d.downloadManagerStatus,
                    "DownloadManagerReasonCode" to d.downloadManagerReasonCode,
                    "DownloadManagerReasonName" to d.downloadManagerReasonName,
                    "LocalUriPresent" to d.localUriPresent,
                    "DownloadedBytes" to d.downloadedBytes,
                    "TotalExpectedBytes" to d.totalExpectedBytes,
                    "FailureStage" to d.failureStage,
                    "FinalDownloadError" to d.finalDownloadError,
                    "ApplicationStatus" to d.applicationStatus,
                    "StatusSetBy" to d.applicationStatusSetBy,
                    "StatusSetAt" to d.applicationStatusSetAt,
                    "FailureTrigger" to d.applicationFailureTrigger,
                    "FailureReason" to d.applicationFailureReason,
                    "AppDownloadId" to d.appDownloadId,
                    "DmId" to d.downloadManagerId,
                    "DmQueryMatchedRow" to d.downloadManagerQueryMatchedRow,
                    "DmQueryRowCount" to d.downloadManagerQueryRowCount,
                    "DmRowMissing" to d.downloadManagerRowMissing,
                    "StuckAtZeroBytes" to d.stuckAtZeroBytes,
                    "ElapsedSeconds" to d.elapsedSinceEnqueueSeconds,
                    "ResolvedUrlHasExpiryParameter" to d.resolvedUrlHasExpiryParameter,
                    "ResolvedUrlHasSignatureParameters" to d.resolvedUrlHasSignatureParameters,
                    "ResolvedUrlAgeAtEnqueueMs" to d.resolvedUrlAgeAtEnqueueMs,
                    "AnalyzeToDownloadDelayMs" to d.analyzeToDownloadDelayMs
                ))
            }

            debugLines.forEach { (label, value) ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "$label=",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
