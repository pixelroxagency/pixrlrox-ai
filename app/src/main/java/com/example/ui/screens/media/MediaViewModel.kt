package com.example.ui.screens.media

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import com.example.data.repository.MediaRepository
import com.example.data.util.M3uPlaylistParser
import com.example.data.util.MediaStoreScanner
import com.example.data.util.RemotePlaylistFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class PlaylistImportState {
    object Idle : PlaylistImportState()
    data class Loading(val message: String) : PlaylistImportState()
    data class Success(val playlistName: String, val channelCount: Int) : PlaylistImportState()
    data class Error(val message: String) : PlaylistImportState()
}

data class PlaylistUiModel(
    val playlist: PlaylistEntity,
    val channelCount: Int
)

class MediaViewModel(
    private val application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    private val _deviceVideos = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val deviceVideos: StateFlow<List<MediaItemEntity>> = _deviceVideos.asStateFlow()

    private val _deviceAudio = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val deviceAudio: StateFlow<List<MediaItemEntity>> = _deviceAudio.asStateFlow()

    private val _deviceImages = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val deviceImages: StateFlow<List<MediaItemEntity>> = _deviceImages.asStateFlow()

    private val _singleStreams = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val singleStreams: StateFlow<List<MediaItemEntity>> = _singleStreams.asStateFlow()

    private val _allIptvStreams = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val allIptvStreams: StateFlow<List<MediaItemEntity>> = _allIptvStreams.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _importedMedia = MutableStateFlow<List<MediaItemEntity>>(emptyList())

    private val _visualMedia = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val visualMedia: StateFlow<List<MediaItemEntity>> = _visualMedia.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val allMedia: StateFlow<List<MediaItemEntity>> = _allMedia.asStateFlow()

    private val _continueWatchingVideos = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val continueWatchingVideos: StateFlow<List<MediaItemEntity>> = _continueWatchingVideos.asStateFlow()

    val mediaItems: StateFlow<List<MediaItemEntity>> = _allMedia

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasVideoPermission = MutableStateFlow(false)
    val hasVideoPermission: StateFlow<Boolean> = _hasVideoPermission.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _hasImagePermission = MutableStateFlow(false)
    val hasImagePermission: StateFlow<Boolean> = _hasImagePermission.asStateFlow()

    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    init {
        // 1. Immediately delete any legacy demo/sample media records from database
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteDemoMedia()
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error deleting legacy demo media", e)
            }
        }

        // 2. Observe user-created single streams and playlists from repository
        viewModelScope.launch {
            repository.getAllStreams().collect { list ->
                val nonDemoList = list.filterNot { isDemoMedia(it) }
                _allIptvStreams.value = nonDemoList
                // Only keep streams that are single streams (not prefixed by playlist_)
                _singleStreams.value = nonDemoList.filter { !it.id.startsWith("pl_chan_") }
                updateCombinedMedia()
            }
        }

        viewModelScope.launch {
            repository.getAllPlaylists().collect { list ->
                _playlists.value = list
                updateCombinedMedia()
            }
        }

        viewModelScope.launch {
            repository.getAllMedia().collect { list ->
                val nonDemoList = list.filterNot { isDemoMedia(it) }
                // Unfinished local or seekable videos
                val unfinished = nonDemoList.filter {
                    (it.mediaType.equals("LOCAL", ignoreCase = true) || it.mediaType.equals("VIDEO", ignoreCase = true)) &&
                    it.lastPosition > 0L &&
                    !it.completed
                }.sortedByDescending { it.lastPlayed }
                _continueWatchingVideos.value = unfinished

                _importedMedia.value = nonDemoList.filter {
                    (it.mediaType.equals("IMAGE", ignoreCase = true) || it.mediaType.equals("VIDEO", ignoreCase = true)) &&
                    !it.id.startsWith("pl_chan_")
                }
                updateCombinedMedia()
            }
        }

        // 3. Query MediaStore if permission is already granted
        checkPermissionsAndScan()
    }

    fun checkPermissionsAndScan() {
        checkPermissions()
        scanDeviceMedia()
    }

    fun checkPermissions(): Triple<Boolean, Boolean, Boolean> {
        val videoGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val imageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        _hasVideoPermission.value = videoGranted
        _hasAudioPermission.value = audioGranted
        _hasImagePermission.value = imageGranted
        return Triple(videoGranted, audioGranted, imageGranted)
    }

    fun scanDeviceMedia() {
        val (videoPerm, audioPerm, imagePerm) = checkPermissions()
        viewModelScope.launch {
            _isScanning.value = true
            try {
                if (videoPerm) {
                    val videos = MediaStoreScanner.queryDeviceVideos(application)
                    _deviceVideos.value = videos
                } else {
                    _deviceVideos.value = emptyList()
                }

                if (audioPerm) {
                    val audios = MediaStoreScanner.queryDeviceAudio(application)
                    _deviceAudio.value = audios
                } else {
                    _deviceAudio.value = emptyList()
                }

                if (imagePerm) {
                    val images = MediaStoreScanner.queryDeviceImages(application)
                    _deviceImages.value = images
                } else {
                    _deviceImages.value = emptyList()
                }

                updateCombinedMedia()
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error scanning device media", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun updateCombinedMedia() {
        val combined = mutableListOf<MediaItemEntity>()
        combined.addAll(_deviceVideos.value)
        combined.addAll(_deviceAudio.value)
        combined.addAll(_deviceImages.value)
        combined.addAll(_singleStreams.value)
        combined.addAll(_importedMedia.value)
        // Sort newest first by lastPlayed / dateModified
        _allMedia.value = combined.distinctBy { it.id }.sortedByDescending { it.lastPlayed }

        // Visual media: images + videos (device + imported), strictly sorted newest first
        val imagesAndVideos = mutableListOf<MediaItemEntity>()
        imagesAndVideos.addAll(_deviceImages.value)
        imagesAndVideos.addAll(_deviceVideos.value)
        imagesAndVideos.addAll(_importedMedia.value.filter {
            it.mediaType.equals("IMAGE", ignoreCase = true) || it.mediaType.equals("VIDEO", ignoreCase = true)
        })
        _visualMedia.value = imagesAndVideos.distinctBy { it.uri }.sortedByDescending { it.lastPlayed }
    }

    fun importMediaUris(uris: List<Uri>, context: Context) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isVideo = mimeType.startsWith("video") || uri.toString().contains("video")
                    val mediaType = if (isVideo) "VIDEO" else "IMAGE"
                    val derivedName = getFileNameFromUri(context, uri) ?: "Imported ${if (isVideo) "Video" else "Photo"}"
                    val now = System.currentTimeMillis()

                    val entity = MediaItemEntity(
                        id = "imported_${uri.toString().hashCode()}",
                        uri = uri.toString(),
                        title = derivedName,
                        duration = 0L,
                        lastPlayed = now,
                        lastPosition = 0L,
                        completed = false,
                        mediaType = mediaType
                    )
                    repository.insertMedia(entity)
                } catch (e: Exception) {
                    Log.e("MediaViewModel", "Error importing media URI: $uri", e)
                }
            }
            withContext(Dispatchers.Main) {
                scanDeviceMedia()
            }
        }
    }

    fun addSingleStream(title: String, url: String) {
        if (title.isBlank() || url.isBlank()) return
        val item = MediaItemEntity(
            id = UUID.randomUUID().toString(),
            uri = url.trim(),
            title = title.trim(),
            duration = 0L,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 0L,
            completed = false,
            mediaType = "STREAM"
        )
        viewModelScope.launch {
            repository.insertMedia(item)
        }
    }

    // Backward-compatibility overload
    fun addMedia(title: String, url: String, type: String = "STREAM") {
        addSingleStream(title, url)
    }

    fun importPlaylistFromUrl(url: String, customName: String? = null) {
        viewModelScope.launch {
            _importState.value = PlaylistImportState.Loading("Connecting to playlist URL...")
            try {
                val result = RemotePlaylistFetcher.fetchAndParse(url, customName)
                saveParsedPlaylist(result, customName ?: result.playlistName ?: "Web Playlist", "M3U_URL")
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error importing playlist from URL", e)
                _importState.value = PlaylistImportState.Error(e.message ?: "Failed to import playlist")
            }
        }
    }

    fun importPlaylistFromFile(uri: Uri, context: Context, customName: String? = null) {
        viewModelScope.launch {
            _importState.value = PlaylistImportState.Loading("Reading playlist file...")
            try {
                val derivedName = customName?.takeIf { it.isNotBlank() }
                    ?: getFileNameFromUri(context, uri)
                    ?: "Local Playlist"

                val result = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open selected file stream.")
                    stream.use {
                        M3uPlaylistParser.parse(it, derivedName)
                    }
                }

                if (result.isSingleHlsManifest) {
                    throw IllegalStateException("The selected file is a single video stream manifest rather than an IPTV playlist. Please add it as a Single Stream.")
                }

                if (result.channels.isEmpty()) {
                    throw IllegalStateException("No playable channels found in the selected file.")
                }

                saveParsedPlaylist(result, derivedName, "M3U_FILE")
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error importing playlist from file", e)
                _importState.value = PlaylistImportState.Error(e.message ?: "Failed to read playlist file")
            }
        }
    }

    private suspend fun saveParsedPlaylist(
        result: com.example.data.util.M3uParseResult,
        playlistTitle: String,
        type: String
    ) {
        _importState.value = PlaylistImportState.Loading("Importing ${result.channels.size} channels...")
        val playlistId = UUID.randomUUID().toString()
        val playlist = PlaylistEntity(
            id = playlistId,
            name = playlistTitle.trim(),
            type = type
        )

        val mediaEntities = mutableListOf<MediaItemEntity>()
        val crossRefs = mutableListOf<PlaylistMediaCrossRef>()

        result.channels.forEachIndexed { index, channel ->
            val mediaId = "pl_chan_${playlistId}_$index"
            val titleWithMetadata = if (!channel.groupTitle.isNullOrBlank() || !channel.tvgLogo.isNullOrBlank()) {
                "${channel.title}|||${channel.groupTitle ?: ""}|||${channel.tvgLogo ?: ""}"
            } else {
                channel.title
            }
            val entity = MediaItemEntity(
                id = mediaId,
                uri = channel.url,
                title = titleWithMetadata,
                duration = 0L,
                lastPlayed = System.currentTimeMillis(),
                lastPosition = 0L,
                completed = false,
                mediaType = "STREAM"
            )
            mediaEntities.add(entity)
            crossRefs.add(
                PlaylistMediaCrossRef(
                    playlistId = playlistId,
                    mediaId = mediaId,
                    itemOrder = index
                )
            )
        }

        repository.importPlaylist(playlist, mediaEntities, crossRefs)
        _importState.value = PlaylistImportState.Success(playlist.name, mediaEntities.size)
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(playlistId)
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error deleting playlist", e)
            }
        }
    }

    fun getChannelsForPlaylist(playlistId: String): Flow<List<MediaItemEntity>> {
        return repository.getMediaForPlaylist(playlistId)
    }

    fun getChannelCountForPlaylist(playlistId: String): Flow<Int> {
        return repository.getChannelCountForPlaylist(playlistId)
    }

    fun resetImportState() {
        _importState.value = PlaylistImportState.Idle
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            repository.deleteMedia(mediaId)
            _deviceVideos.value = _deviceVideos.value.filterNot { it.id == mediaId }
            _deviceAudio.value = _deviceAudio.value.filterNot { it.id == mediaId }
            _singleStreams.value = _singleStreams.value.filterNot { it.id == mediaId }
            updateCombinedMedia()
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            var fileName: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            if (fileName == null) {
                fileName = uri.path?.substringAfterLast('/')
            }
            fileName?.substringBeforeLast('.')
        } catch (_: Exception) {
            null
        }
    }

    private fun isDemoMedia(item: MediaItemEntity): Boolean {
        val uri = item.uri.lowercase()
        val title = item.title.lowercase()
        return uri.contains("bigbuckbunny") ||
                uri.contains("exoplayer-test-media") ||
                uri.contains("gtv-videos-bucket") ||
                title.contains("big buck bunny") ||
                title.contains("google play (hls stream)")
    }
}
