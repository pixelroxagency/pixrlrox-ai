package com.example.ui.screens.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun IptvStreamsView(
    singleStreams: List<MediaItemEntity>,
    playlists: List<PlaylistEntity>,
    viewModel: MediaViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val importState by viewModel.importState.collectAsState()
    val isFullscreen by mediaPlayerViewModel.isFullscreen.collectAsState()
    val isPipMode by mediaPlayerViewModel.isPipMode.collectAsState()
    val resizeMode by mediaPlayerViewModel.resizeMode.collectAsState()
    val playerState by mediaPlayerViewModel.playbackState.collectAsState()
    val currentPlayingItem by mediaPlayerViewModel.currentMediaItem.collectAsState()

    // Check Picture-in-Picture support
    val supportsPip = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPip() {
        if (activity is com.example.MainActivity) {
            activity.enterPipMode()
        } else if (supportsPip && activity != null) {
            if (isFullscreen) {
                mediaPlayerViewModel.setFullscreen(false)
            }
            mediaPlayerViewModel.setPipMode(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val videoFormat = mediaPlayerViewModel.exoPlayer.videoFormat
                val width = videoFormat?.width ?: 16
                val height = videoFormat?.height ?: 9
                val rational = if (width > 0 && height > 0) {
                    val ratio = width.toFloat() / height.toFloat()
                    if (ratio in 0.42f..2.38f) Rational(width, height) else Rational(16, 9)
                } else Rational(16, 9)

                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(false)
                    builder.setSeamlessResizeEnabled(true)
                }
                try {
                    activity.enterPictureInPictureMode(builder.build())
                } catch (_: Exception) {}
            }
        }
    }

    val sharedPrefs = remember {
        context.getSharedPreferences("pixelrox_iptv_preferences", android.content.Context.MODE_PRIVATE)
    }

    // Dialog state
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showSingleStreamDialog by remember { mutableStateOf(false) }
    var showPlaylistUrlDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var streamToDelete by remember { mutableStateOf<IptvChannel?>(null) }

    // Active playlist selection ("ALL" or specific playlist ID)
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(sharedPrefs.getString("last_selected_source", "ALL")) }
    var lastWatchedChannelId by rememberSaveable { mutableStateOf<String?>(sharedPrefs.getString("last_selected_channel_id", null)) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>("ALL") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var currentSortOption by rememberSaveable { mutableStateOf(IptvSortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showPlaylistDropdown by remember { mutableStateOf(false) }

    // Playlist channels flow
    val currentPlaylistChannels by produceState<List<MediaItemEntity>>(
        initialValue = emptyList(),
        key1 = selectedPlaylistId,
        key2 = playlists
    ) {
        if (selectedPlaylistId == null || selectedPlaylistId == "ALL" || selectedPlaylistId == "SINGLE_STREAMS") {
            value = emptyList()
        } else {
            viewModel.getChannelsForPlaylist(selectedPlaylistId!!).collect {
                value = it
            }
        }
    }

    val allStreamsList by viewModel.allIptvStreams.collectAsState()

    // Combine raw stream items based on selected playlist filter
    val rawMediaItems = remember(selectedPlaylistId, singleStreams, allStreamsList, currentPlaylistChannels) {
        when (selectedPlaylistId) {
            "SINGLE_STREAMS" -> singleStreams
            "ALL" -> allStreamsList
            else -> currentPlaylistChannels
        }
    }

    // Convert raw MediaItemEntity to parsed IptvChannel
    val allParsedChannels = remember(rawMediaItems) {
        rawMediaItems.map { IptvChannel.fromEntity(it) }
    }

    // Extract unique groups for the category chips
    val availableGroups = remember(allParsedChannels) {
        val groups = allParsedChannels.mapNotNull { it.group }.distinct().sorted()
        listOf("ALL") + groups
    }

    // Filter by group and search query, then sort
    val visibleChannels = remember(allParsedChannels, selectedGroup, searchQuery, currentSortOption) {
        var list = allParsedChannels

        // Group filter
        if (!selectedGroup.isNullOrBlank() && selectedGroup != "ALL") {
            list = list.filter { it.group.equals(selectedGroup, ignoreCase = true) }
        }

        // Search query filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || (it.group?.lowercase()?.contains(q) == true)
            }
        }

        // Apply sort
        MediaSortUtil.sortIptvChannels(list, currentSortOption)
    }

    // Currently playing parsed channel
    val activeChannel = remember(currentPlayingItem, visibleChannels, allParsedChannels) {
        if (currentPlayingItem == null) null
        else {
            visibleChannels.find { it.id == currentPlayingItem?.id }
                ?: allParsedChannels.find { it.id == currentPlayingItem?.id }
                ?: IptvChannel.fromEntity(currentPlayingItem!!)
        }
    }

    // Handle channel playback
    fun selectAndPlayChannel(channel: IptvChannel) {
        lastWatchedChannelId = channel.id
        sharedPrefs.edit()
            .putString("last_selected_channel_id", channel.id)
            .apply()
        mediaPlayerViewModel.playMedia(channel.rawItem)
    }

    fun playNextChannel() {
        val next = MediaSortUtil.getNextChannel(visibleChannels, activeChannel)
        if (next != null) {
            selectAndPlayChannel(next)
        }
    }

    fun playPreviousChannel() {
        val prev = MediaSortUtil.getPreviousChannel(visibleChannels, activeChannel)
        if (prev != null) {
            selectAndPlayChannel(prev)
        }
    }

    // Handle Fullscreen orientation and System bars
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
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Back handler for Fullscreen
    BackHandler(enabled = isFullscreen) {
        mediaPlayerViewModel.setFullscreen(false)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importPlaylistFromFile(it, context)
        }
    }

    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("iptv_pip_view")
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = mediaPlayerViewModel.exoPlayer
                        useController = false
                        this.resizeMode = resizeMode.modeValue
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = resizeMode.modeValue
                    if (playerView.player != mediaPlayerViewModel.exoPlayer) {
                        playerView.player = mediaPlayerViewModel.exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (isFullscreen) {
        // FULLSCREEN OVERLAY (Takes entire display)
        IptvFullscreenPlayer(
            mediaPlayerViewModel = mediaPlayerViewModel,
            currentChannel = activeChannel,
            playerState = playerState,
            supportsPip = supportsPip,
            onEnterPip = { enterPip() },
            onPrevious = { playPreviousChannel() },
            onNext = { playNextChannel() },
            onExitFullscreen = { mediaPlayerViewModel.setFullscreen(false) },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // PORTRAIT IPTV EXPERIENCE
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("iptv_streams_view")
    ) {
        // Top Player Section (16:9 Aspect Ratio)
        IptvEmbeddedPlayer(
            mediaPlayerViewModel = mediaPlayerViewModel,
            currentChannel = activeChannel,
            playerState = playerState,
            hasChannels = visibleChannels.isNotEmpty(),
            supportsPip = supportsPip,
            onEnterPip = { enterPip() },
            onPrevious = { playPreviousChannel() },
            onNext = { playNextChannel() },
            onToggleFullscreen = { mediaPlayerViewModel.setFullscreen(true) }
        )

        // Currently Playing Channel Info Bar
        IptvCurrentChannelBanner(
            channel = activeChannel,
            playerState = playerState
        )

        // Playlist / Category Navigation & Search / Sort Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Playlist dropdown selector
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPlaylistDropdown = true }
                            .testTag("iptv_playlist_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val currentPlaylistName = when (selectedPlaylistId) {
                                "ALL" -> "All Streams"
                                "SINGLE_STREAMS" -> "Single Streams"
                                else -> playlists.find { it.id == selectedPlaylistId }?.name ?: "Playlist"
                            }
                            Text(
                                text = currentPlaylistName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    val playlistChannelCounts = remember(allStreamsList, playlists) {
                        playlists.associate { pl ->
                            pl.id to allStreamsList.count { it.id.startsWith("pl_chan_${pl.id}_") }
                        }
                    }

                    DropdownMenu(
                        expanded = showPlaylistDropdown,
                        onDismissRequest = { showPlaylistDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Streams (${allStreamsList.size})") },
                            leadingIcon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                            onClick = {
                                selectedPlaylistId = "ALL"
                                selectedGroup = "ALL"
                                showPlaylistDropdown = false
                                sharedPrefs.edit().putString("last_selected_source", "ALL").apply()
                            }
                        )
                        if (singleStreams.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Single Streams (${singleStreams.size})") },
                                leadingIcon = { Icon(Icons.Default.Stream, contentDescription = null) },
                                onClick = {
                                    selectedPlaylistId = "SINGLE_STREAMS"
                                    selectedGroup = "ALL"
                                    showPlaylistDropdown = false
                                    sharedPrefs.edit().putString("last_selected_source", "SINGLE_STREAMS").apply()
                                }
                            )
                        }
                        if (playlists.isNotEmpty()) {
                            HorizontalDivider()
                            playlists.forEach { pl ->
                                val count = playlistChannelCounts[pl.id] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${pl.name} ($count)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    showPlaylistDropdown = false
                                                    playlistToDelete = pl
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("delete_playlist_${pl.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Playlist",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                                    onClick = {
                                        selectedPlaylistId = pl.id
                                        selectedGroup = "ALL"
                                        showPlaylistDropdown = false
                                        sharedPrefs.edit().putString("last_selected_source", pl.id).apply()
                                    }
                                )
                            }
                        }
                    }
                }

                // Action icons: Add Source, Search, Sort
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("iptv_search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Channels",
                            tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("iptv_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Channels",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            IptvSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.displayName,
                                            fontWeight = if (currentSortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = if (currentSortOption == option) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        currentSortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showAddSourceDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("iptv_add_source_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add IPTV Source",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Search input field (animated)
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ${visibleChannels.size} channels...") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("iptv_channel_search_input")
                )
            }

            // Group / Category Filter Chips
            if (availableGroups.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    items(availableGroups) { group ->
                        val isSelected = (selectedGroup == group) || (selectedGroup == null && group == "ALL")
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGroup = group },
                            label = {
                                Text(
                                    text = if (group == "ALL") "All (${allParsedChannels.size})" else group,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("iptv_group_chip_$group")
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        // Channel List
        if (visibleChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No channels match \"$searchQuery\"" else "No IPTV channels available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add live stream URLs or import M3U playlist files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAddSourceDialog = true },
                        modifier = Modifier.testTag("iptv_empty_add_source_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add IPTV Channel")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("iptv_channel_list")
            ) {
                items(
                    items = visibleChannels,
                    key = { it.id }
                ) { channel ->
                    val isPlaying = (activeChannel?.id == channel.id) || (activeChannel == null && lastWatchedChannelId == channel.id)
                    val isSingleStream = !channel.id.startsWith("pl_chan_") && (selectedPlaylistId == "SINGLE_STREAMS" || selectedPlaylistId == "ALL")
                    IptvChannelRowItem(
                        channel = channel,
                        isPlaying = isPlaying,
                        onClick = { selectAndPlayChannel(channel) },
                        onDelete = if (isSingleStream) {
                            { streamToDelete = channel }
                        } else null
                    )
                }
            }
        }
    }

    // Dialogs
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlistToDelete?.name}\" will be removed from PixelRox.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = playlistToDelete!!.id
                        mediaPlayerViewModel.stopAndClearPlaylistIfDeleted(id)
                        viewModel.deletePlaylist(id)
                        if (selectedPlaylistId == id) {
                            selectedPlaylistId = "ALL"
                        }
                        playlistToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_playlist_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (streamToDelete != null) {
        AlertDialog(
            onDismissRequest = { streamToDelete = null },
            title = { Text("Delete stream?") },
            text = { Text("\"${streamToDelete?.title}\" will be removed from PixelRox.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = streamToDelete!!.id
                        mediaPlayerViewModel.stopAndClearIfDeleted(id)
                        viewModel.deleteMedia(id)
                        streamToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_stream_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { streamToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showAddSourceDialog) {
        AddMediaSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onSelectSingleStream = {
                showAddSourceDialog = false
                showSingleStreamDialog = true
            },
            onSelectPlaylistUrl = {
                showAddSourceDialog = false
                showPlaylistUrlDialog = true
            },
            onSelectPlaylistFile = {
                showAddSourceDialog = false
                openDocumentLauncher.launch(
                    arrayOf(
                        "*/*",
                        "audio/x-mpegurl",
                        "application/vnd.apple.mpegurl",
                        "application/x-mpegurl"
                    )
                )
            }
        )
    }

    if (showSingleStreamDialog) {
        SingleStreamDialog(
            onDismiss = { showSingleStreamDialog = false },
            onConfirm = { title, url ->
                viewModel.addSingleStream(title, url)
                showSingleStreamDialog = false
            }
        )
    }

    if (showPlaylistUrlDialog) {
        PlaylistUrlDialog(
            importState = importState,
            onDismiss = {
                showPlaylistUrlDialog = false
                viewModel.resetImportState()
            },
            onImport = { url, name ->
                viewModel.importPlaylistFromUrl(url, name)
            },
            onFinished = {
                showPlaylistUrlDialog = false
                viewModel.resetImportState()
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun IptvEmbeddedPlayer(
    mediaPlayerViewModel: MediaPlayerViewModel,
    currentChannel: IptvChannel?,
    playerState: PlayerState,
    hasChannels: Boolean,
    supportsPip: Boolean,
    onEnterPip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val resizeMode by mediaPlayerViewModel.resizeMode.collectAsState()
    val volume by mediaPlayerViewModel.volume.collectAsState()
    val isMuted by mediaPlayerViewModel.isMuted.collectAsState()

    // Auto-hide controls after 4 seconds when playing
    LaunchedEffect(controlsVisible, playerState) {
        if (controlsVisible && playerState is PlayerState.Playing) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clickable { controlsVisible = !controlsVisible }
            .testTag("iptv_embedded_player")
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = mediaPlayerViewModel.exoPlayer
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode.modeValue
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode.modeValue
                if (playerView.player != mediaPlayerViewModel.exoPlayer) {
                    playerView.player = mediaPlayerViewModel.exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible || playerState is PlayerState.Buffering || playerState is PlayerState.Error || playerState is PlayerState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            ) {
                // Top status bar with LIVE badge, Aspect Ratio, PiP, Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE badge
                    Surface(
                        color = Color.Red.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Top Action buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Video Aspect Ratio / Resize mode button
                        Surface(
                            onClick = { mediaPlayerViewModel.cycleResizeMode() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("iptv_aspect_ratio_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = resizeMode.displayName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Picture in Picture Button
                        if (supportsPip) {
                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("iptv_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Fullscreen Button
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("iptv_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Channel
                    IconButton(
                        onClick = onPrevious,
                        enabled = hasChannels,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("iptv_prev_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Channel",
                            tint = if (hasChannels) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play / Pause / Buffering Indicator
                    when (playerState) {
                        is PlayerState.Buffering -> {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        is PlayerState.Playing -> {
                            IconButton(
                                onClick = { mediaPlayerViewModel.pause() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("iptv_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = {
                                    if (currentChannel != null) {
                                        mediaPlayerViewModel.resume()
                                    } else if (hasChannels) {
                                        onNext()
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("iptv_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // Next Channel
                    IconButton(
                        onClick = onNext,
                        enabled = hasChannels,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("iptv_next_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Channel",
                            tint = if (hasChannels) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Controls: Volume & Error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Volume Control Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { mediaPlayerViewModel.toggleMute() },
                                modifier = Modifier.size(28.dp).testTag("iptv_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume == 0f) Icons.Default.VolumeOff
                                    else if (volume < 0.5f) Icons.Default.VolumeDown
                                    else Icons.Default.VolumeUp,
                                    contentDescription = "Mute Toggle",
                                    tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { mediaPlayerViewModel.volumeDown() },
                                modifier = Modifier.size(28.dp).testTag("iptv_volume_down_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Volume Down",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${if (isMuted) 0 else (volume * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick = { mediaPlayerViewModel.volumeUp() },
                                modifier = Modifier.size(28.dp).testTag("iptv_volume_up_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Volume Up",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Error Overlay
                if (playerState is PlayerState.Error) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stream Error: ${(playerState as PlayerState.Error).message.take(40)}...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { mediaPlayerViewModel.retryPlayback() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IptvCurrentChannelBanner(
    channel: IptvChannel?,
    playerState: PlayerState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Logo / Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                if (!channel?.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel?.logoUrl,
                        contentDescription = channel?.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel?.title ?: "No Channel Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!channel?.group.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = channel!!.group!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when (playerState) {
                            is PlayerState.Playing -> "Playing Live"
                            is PlayerState.Buffering -> "Buffering..."
                            is PlayerState.Paused -> "Paused"
                            is PlayerState.Error -> "Connection error"
                            else -> "Ready to play"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IptvChannelRowItem(
    channel: IptvChannel,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("iptv_channel_item_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel logo or icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!channel.group.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channel.group,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status / Play indicator or Delete button
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_stream_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Stream",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun IptvFullscreenPlayer(
    mediaPlayerViewModel: MediaPlayerViewModel,
    currentChannel: IptvChannel?,
    playerState: PlayerState,
    supportsPip: Boolean,
    onEnterPip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val resizeMode by mediaPlayerViewModel.resizeMode.collectAsState()
    val volume by mediaPlayerViewModel.volume.collectAsState()
    val isMuted by mediaPlayerViewModel.isMuted.collectAsState()

    LaunchedEffect(controlsVisible, playerState) {
        if (controlsVisible && playerState is PlayerState.Playing) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = !controlsVisible }
            .testTag("iptv_fullscreen_view")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = mediaPlayerViewModel.exoPlayer
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode.modeValue
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode.modeValue
                if (playerView.player != mediaPlayerViewModel.exoPlayer) {
                    playerView.player = mediaPlayerViewModel.exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible || playerState !is PlayerState.Playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Header in Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Channel info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = Color.Red.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currentChannel?.title ?: "IPTV Stream",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!currentChannel?.group.isNullOrBlank()) {
                                Text(
                                    text = currentChannel.group!!,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Right: Aspect Ratio, PiP, Exit Fullscreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Aspect Ratio Mode Button
                        Surface(
                            onClick = { mediaPlayerViewModel.cycleResizeMode() },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("iptv_fullscreen_aspect_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = resizeMode.displayName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // PiP Button
                        if (supportsPip) {
                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("iptv_fullscreen_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Exit Fullscreen Button
                        IconButton(
                            onClick = onExitFullscreen,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("iptv_exit_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("iptv_fullscreen_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Channel",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    when (playerState) {
                        is PlayerState.Buffering -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        is PlayerState.Playing -> {
                            IconButton(
                                onClick = { mediaPlayerViewModel.pause() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("iptv_fullscreen_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = {
                                    if (currentChannel != null) {
                                        mediaPlayerViewModel.resume()
                                    } else {
                                        onNext()
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .testTag("iptv_fullscreen_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(58.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("iptv_fullscreen_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Channel",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Controls: Volume & Error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Volume Control Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = { mediaPlayerViewModel.toggleMute() },
                                modifier = Modifier.size(32.dp).testTag("iptv_fullscreen_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume == 0f) Icons.Default.VolumeOff
                                    else if (volume < 0.5f) Icons.Default.VolumeDown
                                    else Icons.Default.VolumeUp,
                                    contentDescription = "Mute Toggle",
                                    tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { mediaPlayerViewModel.volumeDown() },
                                modifier = Modifier.size(32.dp).testTag("iptv_fullscreen_vol_down_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Volume Down",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${if (isMuted) 0 else (volume * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { mediaPlayerViewModel.volumeUp() },
                                modifier = Modifier.size(32.dp).testTag("iptv_fullscreen_vol_up_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Volume Up",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Error banner if any
                    if (playerState is PlayerState.Error) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stream Error: ${(playerState as PlayerState.Error).message.take(45)}...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { mediaPlayerViewModel.retryPlayback() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Retry", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMediaSourceDialog(
    onDismiss: () -> Unit,
    onSelectSingleStream: () -> Unit,
    onSelectPlaylistUrl: () -> Unit,
    onSelectPlaylistFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add IPTV Source", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSelectSingleStream)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Single Stream URL", fontWeight = FontWeight.SemiBold)
                            Text("Add an HLS (.m3u8), MP4, or live URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSelectPlaylistUrl)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("M3U Playlist URL", fontWeight = FontWeight.SemiBold)
                            Text("Download and parse online IPTV playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSelectPlaylistFile)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Import M3U File", fontWeight = FontWeight.SemiBold)
                            Text("Select a local .m3u or .m3u8 file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SingleStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Single Stream URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Title") },
                    placeholder = { Text("e.g. News 24/7") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL (HLS / MP4)") },
                    placeholder = { Text("https://example.com/live.m3u8") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.ifBlank { "Live Stream" }, url.trim()) },
                enabled = url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PlaylistUrlDialog(
    importState: PlaylistImportState,
    onDismiss: () -> Unit,
    onImport: (String, String?) -> Unit,
    onFinished: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (importState !is PlaylistImportState.Loading) onDismiss()
        },
        title = { Text("Import Playlist from URL") },
        text = {
            Column {
                when (importState) {
                    is PlaylistImportState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = importState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is PlaylistImportState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist imported successfully!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Imported ${importState.channelCount} channels into \"${importState.playlistName}\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is PlaylistImportState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = importState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Playlist Name (Optional)") },
                                placeholder = { Text("e.g. My IPTV") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("M3U Playlist URL") },
                                placeholder = { Text("https://example.com/playlist.m3u") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Playlist Name (Optional)") },
                            placeholder = { Text("e.g. My IPTV") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("M3U Playlist URL") },
                            placeholder = { Text("https://example.com/playlist.m3u") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (importState) {
                is PlaylistImportState.Success -> {
                    Button(onClick = onFinished) { Text("Done") }
                }
                is PlaylistImportState.Loading -> {}
                else -> {
                    Button(
                        onClick = { onImport(url, name.takeIf { it.isNotBlank() }) },
                        enabled = url.isNotBlank()
                    ) {
                        Text("Import")
                    }
                }
            }
        },
        dismissButton = {
            if (importState !is PlaylistImportState.Loading && importState !is PlaylistImportState.Success) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
