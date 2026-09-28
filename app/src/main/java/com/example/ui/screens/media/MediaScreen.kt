package com.example.ui.screens.media

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.media.MediaDateGroupUtil
import com.example.data.repository.MediaRepository

enum class MediaLibraryTab(val title: String, val icon: ImageVector) {
    AUDIO("Audio", Icons.Default.Audiotrack),
    VIDEO("Video", Icons.Default.Videocam),
    IMAGES("Images", Icons.Default.PhotoLibrary),
    IPTV("IPTV", Icons.Default.LiveTv)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    repository: MediaRepository,
    mediaPlayerViewModel: MediaPlayerViewModel? = null,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToMedia: ((MediaItemEntity) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MediaViewModel(app, repository) as T
            }
        }
    }
    val viewModel: MediaViewModel = viewModel(factory = factory)

    val playerVm: MediaPlayerViewModel = mediaPlayerViewModel ?: viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MediaPlayerViewModel(app, repository) as T
            }
        }
    )

    // Playback is managed by the player screens and background media service.

    MediaScreenContent(
        viewModel = viewModel,
        mediaPlayerViewModel = playerVm,
        onNavigateToVideo = onNavigateToVideo,
        onNavigateToMedia = onNavigateToMedia,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreenContent(
    viewModel: MediaViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToMedia: ((MediaItemEntity) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MediaLibraryTab.IMAGES) }
    var selectedPhotoForViewer by remember { mutableStateOf<MediaItemEntity?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Section Sort Options
    var imageSortOption by rememberSaveable { mutableStateOf(ImageSortOption.NEWEST) }
    var videoSortOption by rememberSaveable { mutableStateOf(VideoSortOption.NEWEST) }

    val deviceVideos by viewModel.deviceVideos.collectAsState()
    val continueWatchingVideos by viewModel.continueWatchingVideos.collectAsState()
    val deviceImages by viewModel.deviceImages.collectAsState()
    val deviceAudio by viewModel.deviceAudio.collectAsState()
    val singleStreams by viewModel.singleStreams.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val hasVideoPermission by viewModel.hasVideoPermission.collectAsState()
    val hasImagePermission by viewModel.hasImagePermission.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    val isFullscreen by mediaPlayerViewModel.isFullscreen.collectAsState()
    val isPipMode by mediaPlayerViewModel.isPipMode.collectAsState()

    // Stop or pause IPTV playback when user switches away from the IPTV tab (unless in PiP)
    LaunchedEffect(selectedTab) {
        if (selectedTab != MediaLibraryTab.IPTV && !mediaPlayerViewModel.isPipMode.value) {
            mediaPlayerViewModel.pause()
        }
    }

    val hasVisualPermission = hasVideoPermission || hasImagePermission

    // System Permission launchers
    val visualPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionsAndScan()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.checkPermissionsAndScan()
    }

    fun requestVisualPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            visualPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            visualPermissionsLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Filter & Sort Images
    val visibleImages = remember(deviceImages, searchQuery, imageSortOption) {
        val filtered = if (searchQuery.isBlank()) {
            deviceImages
        } else {
            val q = searchQuery.trim().lowercase()
            deviceImages.filter { it.title.lowercase().contains(q) }
        }
        MediaSortUtil.sortImages(filtered, imageSortOption)
    }

    // Filter & Sort Videos
    val visibleVideos = remember(deviceVideos, searchQuery, videoSortOption) {
        val filtered = if (searchQuery.isBlank()) {
            deviceVideos
        } else {
            val q = searchQuery.trim().lowercase()
            deviceVideos.filter { it.title.lowercase().contains(q) }
        }
        MediaSortUtil.sortVideos(filtered, videoSortOption)
    }

    // Timeline grouping for Images when sorted chronologically
    val groupedImagesTimeline = remember(visibleImages, imageSortOption) {
        if (imageSortOption == ImageSortOption.NEWEST || imageSortOption == ImageSortOption.OLDEST) {
            MediaDateGroupUtil.groupByDate(visibleImages)
        } else {
            emptyMap()
        }
    }

    // Timeline grouping for Videos when sorted chronologically
    val groupedVideosTimeline = remember(visibleVideos, videoSortOption) {
        if (videoSortOption == VideoSortOption.NEWEST || videoSortOption == VideoSortOption.OLDEST) {
            MediaDateGroupUtil.groupByDate(visibleVideos)
        } else {
            emptyMap()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen && !isPipMode) {
                if (isSearchActive && (selectedTab == MediaLibraryTab.IMAGES || selectedTab == MediaLibraryTab.VIDEO)) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(if (selectedTab == MediaLibraryTab.IMAGES) "Search photos..." else "Search videos...")
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("media_search_input")
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.testTag("media_search_close_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Media Center",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("gallery_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Search button for Images / Video
                            if (selectedTab == MediaLibraryTab.IMAGES || selectedTab == MediaLibraryTab.VIDEO) {
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.testTag("media_gallery_search_button")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search Media")
                                }
                            }

                            // Sort Menu for Images / Video
                            if (selectedTab == MediaLibraryTab.IMAGES || selectedTab == MediaLibraryTab.VIDEO) {
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("media_sort_button")
                                    ) {
                                        Icon(Icons.Default.Sort, contentDescription = "Sort Media")
                                    }

                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        if (selectedTab == MediaLibraryTab.IMAGES) {
                                            ImageSortOption.entries.forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = option.displayName,
                                                            fontWeight = if (imageSortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (imageSortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = if (imageSortOption == option) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null,
                                                    onClick = {
                                                        imageSortOption = option
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        } else {
                                            VideoSortOption.entries.forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = option.displayName,
                                                            fontWeight = if (videoSortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (videoSortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    leadingIcon = if (videoSortOption == option) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null,
                                                    onClick = {
                                                        videoSortOption = option
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // More overflow menu (Scan device)
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("media_gallery_more_button")
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Scan Device Storage") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.scanDeviceMedia()
                                        },
                                        modifier = Modifier.testTag("menu_item_scan")
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        modifier = modifier.testTag("media_gallery_screen")
    ) { padding ->
        val effectivePadding = if (isFullscreen || isPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(effectivePadding)
        ) {
            // UNIFIED 4-DESTINATION TAB NAVIGATION (Hidden in Fullscreen or PiP)
            if (!isFullscreen && !isPipMode) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_navigation_tab_row")
                ) {
                    MediaLibraryTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                                isSearchActive = false
                                searchQuery = ""
                            },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(imageVector = tab.icon, contentDescription = tab.title)
                            },
                            modifier = Modifier.testTag("media_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // CONTENT BASED ON SELECTED TAB
            when (selectedTab) {
                MediaLibraryTab.AUDIO -> {
                    AudioLibraryView(
                        audioItems = deviceAudio,
                        hasAudioPermission = hasAudioPermission,
                        isScanning = isScanning,
                        onRequestPermission = { requestAudioPermission() },
                        onScanDevice = { viewModel.scanDeviceMedia() },
                        onPlayAudio = { item ->
                            if (onNavigateToMedia != null) onNavigateToMedia(item)
                            else onNavigateToVideo(item.uri)
                        }
                    )
                }

                MediaLibraryTab.VIDEO -> {
                    if (visibleVideos.isEmpty() && !isScanning) {
                        GalleryEmptyState(
                            hasPermission = hasVisualPermission,
                            title = "No videos found",
                            subtitle = "Videos on your device storage will appear here automatically.",
                            icon = Icons.Default.Videocam,
                            onRequestPermission = { requestVisualPermissions() },
                            onScanDevice = { viewModel.scanDeviceMedia() }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("video_library_grid")
                        ) {
                            if (continueWatchingVideos.isNotEmpty() && searchQuery.isBlank()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = "Continue Watching",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                                        )
                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            this.items(continueWatchingVideos) { video ->
                                                ContinueWatchingCard(
                                                    video = video,
                                                    onClick = {
                                                        if (onNavigateToMedia != null) {
                                                            onNavigateToMedia(video)
                                                        } else {
                                                            onNavigateToVideo(video.uri)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            if (groupedVideosTimeline.isNotEmpty() && searchQuery.isBlank()) {
                                groupedVideosTimeline.forEach { (dateHeader, itemsInDate) ->
                                    item(
                                        key = "video_header_$dateHeader",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        TimelineDateHeader(
                                            title = dateHeader,
                                            count = itemsInDate.size
                                        )
                                    }

                                    items(
                                        items = itemsInDate,
                                        key = { it.id }
                                    ) { item ->
                                        VideoGridCard(
                                            item = item,
                                            onClick = {
                                                if (onNavigateToMedia != null) {
                                                    onNavigateToMedia(item)
                                                } else {
                                                    onNavigateToVideo(item.uri)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = visibleVideos,
                                    key = { it.id }
                                ) { item ->
                                    VideoGridCard(
                                        item = item,
                                        onClick = {
                                            if (onNavigateToMedia != null) {
                                                onNavigateToMedia(item)
                                            } else {
                                                onNavigateToVideo(item.uri)
                                            }
                                        }
                                    )
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }

                MediaLibraryTab.IMAGES -> {
                    if (visibleImages.isEmpty() && !isScanning) {
                        GalleryEmptyState(
                            hasPermission = hasVisualPermission,
                            title = if (searchQuery.isNotBlank()) "No photos match \"$searchQuery\"" else "No photos found",
                            subtitle = "Photos and images on your device will appear here.",
                            icon = Icons.Default.PhotoLibrary,
                            onRequestPermission = { requestVisualPermissions() },
                            onScanDevice = { viewModel.scanDeviceMedia() }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gallery_timeline_grid")
                        ) {
                            if (groupedImagesTimeline.isNotEmpty() && searchQuery.isBlank()) {
                                groupedImagesTimeline.forEach { (dateHeader, itemsInDate) ->
                                    item(
                                        key = "header_$dateHeader",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        TimelineDateHeader(
                                            title = dateHeader,
                                            count = itemsInDate.size
                                        )
                                    }

                                    items(
                                        items = itemsInDate,
                                        key = { it.id }
                                    ) { item ->
                                        GalleryThumbnailItem(
                                            item = item,
                                            onClick = { selectedPhotoForViewer = item }
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = visibleImages,
                                    key = { it.id }
                                ) { item ->
                                    GalleryThumbnailItem(
                                        item = item,
                                        onClick = { selectedPhotoForViewer = item }
                                    )
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }

                MediaLibraryTab.IPTV -> {
                    IptvStreamsView(
                        singleStreams = singleStreams,
                        playlists = playlists,
                        viewModel = viewModel,
                        mediaPlayerViewModel = mediaPlayerViewModel,
                        onBack = onBack
                    )
                }
            }

            // Mini Player for background Audio playback
            val currentItem by mediaPlayerViewModel.currentMediaItem.collectAsState()
            val playbackState by mediaPlayerViewModel.playbackState.collectAsState()
            val isPlaying = playbackState is PlayerState.Playing

            if (currentItem != null && currentItem?.mediaType == "AUDIO") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable {
                            if (onNavigateToMedia != null) {
                                onNavigateToMedia(currentItem!!)
                            }
                        }
                        .testTag("audio_mini_player"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentItem?.title ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("mini_player_title")
                            )
                            Text(
                                text = "Local Audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(
                            onClick = { mediaPlayerViewModel.playPrevious() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    mediaPlayerViewModel.pause()
                                } else {
                                    mediaPlayerViewModel.resume()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("mini_player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { mediaPlayerViewModel.playNext() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Full-screen image viewer overlay
        selectedPhotoForViewer?.let { photo ->
            PhotoViewerOverlay(
                item = photo,
                onDismiss = { selectedPhotoForViewer = null }
            )
        }
    }
}

@Composable
fun VideoGridCard(
    item: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("video_item_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .videoFrameMillis(1000L)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon overlay in center
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Duration badge bottom right
                if (item.duration > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = MediaDateGroupUtil.formatDuration(item.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TimelineDateHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp)
            .testTag("timeline_header_$title"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count ${if (count == 1) "item" else "items"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GalleryEmptyState(
    hasPermission: Boolean,
    title: String = "No media found",
    subtitle: String = "Media on your device will appear here automatically.",
    icon: ImageVector = Icons.Default.PhotoLibrary,
    onRequestPermission: () -> Unit,
    onScanDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.testTag("gallery_empty_state")
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (!hasPermission) "Storage access required" else title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (!hasPermission) {
                    "Allow access to your device files so media can appear here."
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.testTag("gallery_grant_permission_button")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Access")
                }
            } else {
                Button(
                    onClick = onScanDevice,
                    modifier = Modifier.testTag("gallery_scan_device_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Storage")
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    video: MediaItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .testTag("continue_watching_card_${video.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                // Video thumbnail using AsyncImage
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.uri)
                        .videoFrameMillis(2000) // load frame at 2 seconds
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Progress bar overlay at bottom of thumbnail
                if (video.duration > 0L) {
                    val progressPercent = (video.lastPosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Progress time
                val positionSec = video.lastPosition / 1000
                val progressText = "Resume at %02d:%02d".format(positionSec / 60, positionSec % 60)
                
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
