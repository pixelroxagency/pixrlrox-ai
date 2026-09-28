package com.example.ui.screens.media

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.core.audio.AudioPlaybackService
import com.example.core.database.entity.media.MediaItemEntity
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class VideoResizeMode(val displayName: String, val modeValue: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill / Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean,
    val mimeType: String?,
    val language: String?
)

@OptIn(UnstableApi::class)
class MediaPlayerViewModel(
    application: Application,
    private val mediaRepository: MediaRepository
) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val prefs = application.getSharedPreferences("pixelrox_audio_prefs", Context.MODE_PRIVATE)

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("PixelRox/1.0 (Android; ExoPlayer)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)

    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(application, httpDataSourceFactory)
    )

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(audioAttributes, true)
        .build()

    private var mediaSession: MediaSession? = null

    private val _playbackState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItemEntity?>(null)
    val currentMediaItem: StateFlow<MediaItemEntity?> = _currentMediaItem.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    private val _resizeMode = MutableStateFlow(VideoResizeMode.FIT)
    val resizeMode: StateFlow<VideoResizeMode> = _resizeMode.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Playlist/Queue support
    private val _playlistQueue = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val playlistQueue: StateFlow<List<MediaItemEntity>> = _playlistQueue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Dynamic Position/Duration tracking
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var progressJob: Job? = null

    // Audio Noisy Broadcast Receiver (headphone unplugged, bluetooth disconnected)
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        // Build MediaSession
        try {
            mediaSession = MediaSession.Builder(application, exoPlayer)
                .setId("PixelRoxMediaSession")
                .build()
            activeSession = mediaSession
        } catch (e: Exception) {
            Log.e("MediaPlayerVM", "Error building MediaSession", e)
        }

        // Register Noisy Receiver
        try {
            val filter = android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            application.registerReceiver(noisyReceiver, filter)
        } catch (e: Exception) {
            Log.e("MediaPlayerVM", "Error registering noisy receiver", e)
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("MediaPlayerVM", "onPlaybackStateChanged: $playbackState (isPlaying=${exoPlayer.isPlaying}, playWhenReady=${exoPlayer.playWhenReady})")
                when (playbackState) {
                    Player.STATE_BUFFERING -> _playbackState.value = PlayerState.Buffering
                    Player.STATE_READY -> {
                        _currentDuration.value = exoPlayer.duration.coerceAtLeast(0L)
                        if (exoPlayer.isPlaying) {
                            _playbackState.value = PlayerState.Playing
                        } else {
                            _playbackState.value = PlayerState.Paused
                        }
                        startProgressJob()
                    }
                    Player.STATE_ENDED -> {
                        _playbackState.value = PlayerState.Ended
                        markCurrentCompleted()
                    }
                    Player.STATE_IDLE -> _playbackState.value = PlayerState.Idle
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("MediaPlayerVM", "onIsPlayingChanged: $isPlaying")
                if (isPlaying) {
                    _playbackState.value = PlayerState.Playing
                    startProgressJob()
                } else {
                    if (exoPlayer.playbackState == Player.STATE_READY) {
                        _playbackState.value = PlayerState.Paused
                        saveProgress()
                    }
                    stopProgressJob()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorCodeName = error.errorCodeName
                val causeMsg = error.cause?.message ?: error.message ?: "Unknown playback error"
                val detailedMessage = "[$errorCodeName] $causeMsg"
                Log.e("MediaPlayerVM", "onPlayerError: $detailedMessage", error)
                _playbackState.value = PlayerState.Error(detailedMessage)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d("MediaPlayerVM", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}, reason=$reason")
                if (mediaItem != null) {
                    val currentId = mediaItem.mediaId
                    val matchedEntity = _playlistQueue.value.find { it.id == currentId }
                    if (matchedEntity != null) {
                        _currentMediaItem.value = matchedEntity
                        val index = _playlistQueue.value.indexOfFirst { it.id == currentId }
                        _currentQueueIndex.value = index
                        saveProgress()
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })

        // Restore last played audio silently without auto-playing
        loadLastAudio()
    }

    private fun startPlaybackService() {
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, AudioPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerVM", "Failed to start AudioPlaybackService", e)
        }
    }

    private fun loadLastAudio() {
        val lastUri = prefs.getString("last_played_audio_uri", null)
        val lastTitle = prefs.getString("last_played_audio_title", null)
        val lastId = prefs.getString("last_played_audio_id", null)
        val lastPos = prefs.getLong("last_played_audio_position", 0L)
        val lastDuration = prefs.getLong("last_played_audio_duration", 0L)
        val lastShuffle = prefs.getBoolean("last_played_audio_shuffle", false)
        val lastRepeat = prefs.getInt("last_played_audio_repeat", Player.REPEAT_MODE_OFF)

        if (lastUri != null && lastId != null && lastTitle != null) {
            val entity = MediaItemEntity(
                id = lastId,
                uri = lastUri,
                title = lastTitle,
                duration = lastDuration,
                lastPlayed = System.currentTimeMillis(),
                lastPosition = lastPos,
                completed = false,
                mediaType = "AUDIO"
            )
            _currentMediaItem.value = entity
            _currentPosition.value = lastPos
            _currentDuration.value = lastDuration
            _isShuffleEnabled.value = lastShuffle
            _repeatMode.value = lastRepeat

            try {
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(lastTitle)
                    .setArtist("Local Audio")
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(lastUri))
                    .setMediaId(lastId)
                    .setMediaMetadata(metadata)
                    .build()
                
                _playlistQueue.value = listOf(entity)
                _currentQueueIndex.value = 0

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.shuffleModeEnabled = lastShuffle
                exoPlayer.repeatMode = lastRepeat
                exoPlayer.prepare()
                exoPlayer.seekTo(lastPos)
                exoPlayer.playWhenReady = false
            } catch (e: Exception) {
                Log.e("MediaPlayerVM", "Failed to silently restore last played audio", e)
            }
        }
    }

    fun playAudioMedia(item: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.getAllMedia().collect { allMedia ->
                val audioQueue = allMedia.filter { it.mediaType == "AUDIO" }
                val sortedQueue = audioQueue.sortedByDescending { it.lastPlayed }
                playAudioWithQueue(item, sortedQueue)
                this@launch.coroutineContext[Job]?.cancel()
            }
        }
    }

    fun playVideoMedia(item: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.getAllMedia().collect { allMedia ->
                val videoQueue = allMedia.filter {
                    it.mediaType.equals("LOCAL", ignoreCase = true) || it.mediaType.equals("VIDEO", ignoreCase = true)
                }
                val sortedQueue = videoQueue.sortedByDescending { it.lastPlayed }
                playVideoWithQueue(item, sortedQueue)
                this@launch.coroutineContext[Job]?.cancel()
            }
        }
    }

    fun playAudioWithQueue(item: MediaItemEntity, queue: List<MediaItemEntity>) {
        Log.d("MediaPlayerVM", "playAudioWithQueue: song='${item.title}', queueSize=${queue.size}")
        _playlistQueue.value = queue

        val mediaItems = queue.map { entity ->
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(entity.title)
                .setArtist("Local Audio")
                .build()
            MediaItem.Builder()
                .setUri(Uri.parse(entity.uri))
                .setMediaId(entity.id)
                .setMediaMetadata(metadata)
                .build()
        }

        val index = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _currentQueueIndex.value = index
        _currentMediaItem.value = item

        exoPlayer.setMediaItems(mediaItems, index, 0L)

        val effectivePosition = if (item.lastPosition > 0L && !item.completed) item.lastPosition else 0L
        if (effectivePosition > 0L) {
            exoPlayer.seekTo(index, effectivePosition)
            _currentPosition.value = effectivePosition
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        startPlaybackService()
    }

    fun playMedia(item: MediaItemEntity, startPosition: Long = 0L) {
        Log.d("MediaPlayerVM", "playMedia: title='${item.title}', uri='${item.uri}', startPosition=$startPosition")
        viewModelScope.launch {
            val dbItem = mediaRepository.getMediaByUri(item.uri)
            val effectivePosition = if (startPosition > 0L) {
                startPosition
            } else if (dbItem != null && dbItem.lastPosition > 0L && !dbItem.completed) {
                dbItem.lastPosition
            } else {
                0L
            }

            val finalItem = dbItem ?: item
            _currentMediaItem.value = finalItem
            _playbackState.value = PlayerState.Buffering
            
            // For single play, clear queue and put just this item
            _playlistQueue.value = listOf(finalItem)
            _currentQueueIndex.value = 0

            try {
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(finalItem.title)
                    .setArtist(if (finalItem.mediaType == "AUDIO") "Local Audio" else "Video Stream")
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(finalItem.uri))
                    .setMediaId(finalItem.id)
                    .setMediaMetadata(metadata)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (effectivePosition > 0L) {
                    exoPlayer.seekTo(effectivePosition)
                    _currentPosition.value = effectivePosition
                }
                exoPlayer.playWhenReady = true

                if (finalItem.mediaType == "AUDIO") {
                    startPlaybackService()
                }
            } catch (e: Exception) {
                Log.e("MediaPlayerVM", "Error preparing media item", e)
                _playbackState.value = PlayerState.Error(e.message ?: "Failed to open media URI")
            }
        }
    }

    fun playUrl(url: String, title: String = "Stream") {
        val item = MediaItemEntity(
            id = UUID.randomUUID().toString(),
            uri = url,
            title = title,
            duration = 0L,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 0L,
            completed = false,
            mediaType = "STREAM"
        )
        playMedia(item)
    }

    fun retryPlayback() {
        val current = _currentMediaItem.value
        if (current != null) {
            playMedia(current, exoPlayer.currentPosition.coerceAtLeast(0L))
        } else {
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun stopPlayback() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playlistQueue.value = emptyList()
        _currentQueueIndex.value = -1
        _playbackState.value = PlayerState.Idle
        _isFullscreen.value = false
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    fun playPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val nextShuffle = !exoPlayer.shuffleModeEnabled
        exoPlayer.shuffleModeEnabled = nextShuffle
        _isShuffleEnabled.value = nextShuffle
    }

    fun cycleRepeatMode() {
        val nextMode = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    // Video Resize Modes
    fun setResizeMode(mode: VideoResizeMode) {
        _resizeMode.value = mode
    }

    fun cycleResizeMode(): VideoResizeMode {
        val modes = VideoResizeMode.entries
        val nextIndex = (modes.indexOf(_resizeMode.value) + 1) % modes.size
        val nextMode = modes[nextIndex]
        _resizeMode.value = nextMode
        return nextMode
    }

    // Volume & Mute Controls
    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        if (!_isMuted.value) {
            exoPlayer.volume = clamped
        }
    }

    fun setVolumePercentage(percentage: Float, audioManager: AudioManager?) {
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val targetVolume = (percentage * maxVolume).toInt()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        setVolume(percentage)
    }

    fun volumeUp(step: Float = 0.1f) {
        if (_isMuted.value) {
            _isMuted.value = false
        }
        val newVol = (_volume.value + step).coerceIn(0f, 1f)
        _volume.value = newVol
        exoPlayer.volume = newVol
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
        } catch (_: Exception) {}
    }

    fun volumeDown(step: Float = 0.1f) {
        val newVol = (_volume.value - step).coerceIn(0f, 1f)
        _volume.value = newVol
        if (newVol <= 0.01f) {
            _isMuted.value = true
            exoPlayer.volume = 0f
        } else {
            if (!_isMuted.value) {
                exoPlayer.volume = newVol
            }
        }
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        } catch (_: Exception) {}
    }

    fun toggleMute() {
        val nextMute = !_isMuted.value
        _isMuted.value = nextMute
        if (nextMute) {
            exoPlayer.volume = 0f
        } else {
            val restored = if (_volume.value <= 0.05f) 0.5f else _volume.value
            _volume.value = restored
            exoPlayer.volume = restored
        }
    }

    private fun startProgressJob() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                // Update seek progress variables
                _currentPosition.value = exoPlayer.currentPosition
                _currentDuration.value = exoPlayer.duration.coerceAtLeast(0L)
                
                // Periodically save progress state
                saveProgress()
                delay(500L)
            }
        }
    }

    private fun stopProgressJob() {
        progressJob?.cancel()
        progressJob = null
    }

    fun saveProgress() {
        val currentItem = _currentMediaItem.value ?: return
        val pos = exoPlayer.currentPosition
        val dur = exoPlayer.duration.coerceAtLeast(0L)
        val isLive = exoPlayer.isCurrentMediaItemLive

        val lastPos = if (isLive || dur <= 0L) 0L else pos
        val completed = if (dur > 0 && !isLive) (pos.toDouble() / dur.toDouble()) > 0.95 else false

        val updated = currentItem.copy(
            lastPosition = lastPos,
            duration = if (dur > 0) dur else currentItem.duration,
            lastPlayed = System.currentTimeMillis(),
            completed = completed
        )
        _currentMediaItem.value = updated
        viewModelScope.launch {
            mediaRepository.insertMedia(updated)
        }

        // Save last audio tracks configuration to preference storage
        if (currentItem.mediaType == "AUDIO") {
            prefs.edit().apply {
                putString("last_played_audio_uri", currentItem.uri)
                putString("last_played_audio_title", currentItem.title)
                putString("last_played_audio_id", currentItem.id)
                putLong("last_played_audio_position", lastPos)
                putLong("last_played_audio_duration", if (dur > 0) dur else currentItem.duration)
                putBoolean("last_played_audio_shuffle", exoPlayer.shuffleModeEnabled)
                putInt("last_played_audio_repeat", exoPlayer.repeatMode)
                commit()
            }
        }
    }

    private fun markCurrentCompleted() {
        val currentItem = _currentMediaItem.value ?: return
        val updated = currentItem.copy(completed = true, lastPosition = 0L)
        _currentMediaItem.value = updated
        viewModelScope.launch {
            mediaRepository.insertMedia(updated)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setPipMode(inPip: Boolean) {
        _isPipMode.value = inPip
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer.setPlaybackSpeed(speed)
    }

    fun getSubtitleTracks(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val currentTracks = exoPlayer.currentTracks
        val groups = currentTracks.groups
        for (i in 0 until groups.size) {
            val group = groups[i]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (j in 0 until group.length) {
                    val format = group.getTrackFormat(j)
                    val name = format.label ?: format.language ?: "Subtitle ${tracks.size + 1}"
                    tracks.add(
                        TrackInfo(
                            groupIndex = i,
                            trackIndex = j,
                            name = name,
                            isSelected = group.isTrackSelected(j),
                            mimeType = format.sampleMimeType,
                            language = format.language
                        )
                    )
                }
            }
        }
        return tracks
    }

    fun getAudioTracks(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val currentTracks = exoPlayer.currentTracks
        val groups = currentTracks.groups
        for (i in 0 until groups.size) {
            val group = groups[i]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (j in 0 until group.length) {
                    val format = group.getTrackFormat(j)
                    val name = format.label ?: format.language ?: "Audio Track ${tracks.size + 1}"
                    tracks.add(
                        TrackInfo(
                            groupIndex = i,
                            trackIndex = j,
                            name = name,
                            isSelected = group.isTrackSelected(j),
                            mimeType = format.sampleMimeType,
                            language = format.language
                        )
                    )
                }
            }
        }
        return tracks
    }

    fun selectTrack(track: TrackInfo, type: Int) {
        val currentTracks = exoPlayer.currentTracks
        if (track.groupIndex in 0 until currentTracks.groups.size) {
            val group = currentTracks.groups[track.groupIndex]
            val trackGroup = group.mediaTrackGroup
            val override = androidx.media3.common.TrackSelectionOverride(trackGroup, track.trackIndex)
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(type)
                .addOverride(override)
                .setTrackTypeDisabled(type, false)
                .build()
        }
    }

    fun disableSubtitles() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun enableSubtitles() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    fun playVideoWithQueue(item: MediaItemEntity, queue: List<MediaItemEntity>) {
        Log.d("MediaPlayerVM", "playVideoWithQueue: video='${item.title}', queueSize=${queue.size}")
        _playlistQueue.value = queue

        val mediaItems = queue.map { entity ->
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(entity.title)
                .setArtist("Local Video")
                .build()
            MediaItem.Builder()
                .setUri(Uri.parse(entity.uri))
                .setMediaId(entity.id)
                .setMediaMetadata(metadata)
                .build()
        }

        val index = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _currentQueueIndex.value = index
        _currentMediaItem.value = item

        exoPlayer.setMediaItems(mediaItems, index, 0L)

        val effectivePosition = if (item.lastPosition > 0L && !item.completed) item.lastPosition else 0L
        if (effectivePosition > 0L) {
            exoPlayer.seekTo(index, effectivePosition)
            _currentPosition.value = effectivePosition
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun stopAndClearIfDeleted(mediaId: String) {
        val current = _currentMediaItem.value
        if (current?.id == mediaId) {
            pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _currentMediaItem.value = null
            _playbackState.value = PlayerState.Idle
        }
    }

    fun stopAndClearPlaylistIfDeleted(playlistId: String) {
        val current = _currentMediaItem.value
        if (current != null && current.id.startsWith("pl_chan_${playlistId}_")) {
            pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _currentMediaItem.value = null
            _playbackState.value = PlayerState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveProgress()
        stopProgressJob()
        try {
            getApplication<Application>().unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
        try {
            mediaSession?.release()
            activeSession = null
        } catch (e: Exception) {
            Log.e("MediaPlayerVM", "Error releasing mediaSession", e)
        }
        exoPlayer.release()
    }

    companion object {
        var activeSession: MediaSession? = null
    }
}

sealed class PlayerState {
    data object Idle : PlayerState()
    data object Buffering : PlayerState()
    data object Playing : PlayerState()
    data object Paused : PlayerState()
    data object Ended : PlayerState()
    data class Error(val message: String) : PlayerState()
}
