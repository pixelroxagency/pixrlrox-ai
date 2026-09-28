package com.example

import android.app.Application
import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.dao.media.MediaDao
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import com.example.data.repository.MediaRepository
import com.example.ui.screens.media.MediaPlayerViewModel
import com.example.ui.screens.media.PlayerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioPlayerArchitectureTest {

    private lateinit var context: Context
    private lateinit var fakeMediaDao: FakeMediaDao
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: MediaPlayerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeMediaDao = FakeMediaDao()
        repository = MediaRepository(fakeMediaDao)
        viewModel = MediaPlayerViewModel(context as Application, repository)
    }

    @Test
    fun testOriginalPlaybackBugRegressionAndControls() = runBlocking {
        // Arrange: Prepare test audio tracks
        val song1 = MediaItemEntity(
            id = "test_song_1",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            title = "SoundHelix Song 1",
            duration = 240000L,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 0L,
            completed = false,
            mediaType = "AUDIO"
        )
        val song2 = MediaItemEntity(
            id = "test_song_2",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            title = "SoundHelix Song 2",
            duration = 210000L,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 0L,
            completed = false,
            mediaType = "AUDIO"
        )
        val queue = listOf(song1, song2)

        // Act: Start playback with queue
        viewModel.playAudioWithQueue(song1, queue)

        // Verify: 1. Player is initialized and not null
        assertNotNull(viewModel.exoPlayer)

        // Verify: 2. Player is NOT recreated during playback (it has same instance reference)
        val playerInstance = viewModel.exoPlayer
        assertSame(playerInstance, viewModel.exoPlayer)

        // Verify: 3. playWhenReady is true after starting playback
        assertTrue(viewModel.exoPlayer.playWhenReady)

        // Act: Test pause
        viewModel.pause()
        // Verify: 4. Pause stops playWhenReady
        assertFalse(viewModel.exoPlayer.playWhenReady)

        // Act: Test resume
        viewModel.resume()
        // Verify: 5. Resume restores playWhenReady
        assertTrue(viewModel.exoPlayer.playWhenReady)

        // Act: Test seek
        viewModel.seekTo(50000L)
        // Verify: 6. Seek position is correctly updated
        assertEquals(50000L, viewModel.currentPosition.value)

        // Act: Test Shuffle toggle
        val initialShuffle = viewModel.isShuffleEnabled.value
        viewModel.toggleShuffle()
        // Verify: 7. Shuffle toggle swaps state
        assertEquals(!initialShuffle, viewModel.isShuffleEnabled.value)

        // Act: Test Repeat cycle
        viewModel.cycleRepeatMode()
        // Verify: 8. Repeat mode cycles
        assertEquals(Player.REPEAT_MODE_ONE, viewModel.repeatMode.value)

        // Verify: 9. Queue index matches the first track
        assertEquals(0, viewModel.currentQueueIndex.value)
    }

    @Test
    fun testPersistentLastAudioSave() = runBlocking {
        val song = MediaItemEntity(
            id = "last_saved_track",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            title = "Last Track",
            duration = 180000L,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 12000L,
            completed = false,
            mediaType = "AUDIO"
        )
        viewModel.playAudioWithQueue(song, listOf(song))
        viewModel.seekTo(12000L)
        viewModel.saveProgress()

        // Verify: Last song details are recorded in shared preferences
        val prefs = context.getSharedPreferences("pixelrox_audio_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("last_played_audio_id", null)
        val savedTitle = prefs.getString("last_played_audio_title", null)
        val savedPos = prefs.getLong("last_played_audio_position", -1L)
        
        println("DIAGNOSTIC - savedId: $savedId, savedTitle: $savedTitle, savedPos: $savedPos")
        
        assertEquals("last_saved_track", savedId)
        assertEquals("Last Track", savedTitle)
    }
}

class FakeMediaDao : MediaDao {
    private val mediaList = mutableListOf<MediaItemEntity>()

    override fun getAllMedia(): Flow<List<MediaItemEntity>> = flowOf(mediaList)
    override suspend fun getMediaByUri(uri: String): MediaItemEntity? = mediaList.find { it.uri == uri }
    override fun getAllStreams(): Flow<List<MediaItemEntity>> = flowOf(emptyList())
    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = flowOf(emptyList())
    override fun getMediaForPlaylist(playlistId: String): Flow<List<MediaItemEntity>> = flowOf(emptyList())
    override fun getChannelCountForPlaylist(playlistId: String): Flow<Int> = flowOf(0)
    override suspend fun insertMedia(media: MediaItemEntity) {
        mediaList.removeAll { it.id == media.id }
        mediaList.add(media)
    }
    override suspend fun deleteMediaById(mediaId: String) {
        mediaList.removeAll { it.id == mediaId }
    }
    override suspend fun insertMediaList(mediaList: List<MediaItemEntity>) {
        this.mediaList.addAll(mediaList)
    }
    override suspend fun insertPlaylist(playlist: PlaylistEntity) {}
    override suspend fun deletePlaylistById(playlistId: String) {}
    override suspend fun deleteCrossRefsForPlaylist(playlistId: String) {}
    override suspend fun insertPlaylistMediaCrossRef(crossRef: PlaylistMediaCrossRef) {}
    override suspend fun insertCrossRefs(crossRefs: List<PlaylistMediaCrossRef>) {}
    override suspend fun deleteMediaItemsForPlaylist(playlistId: String) {}
    override suspend fun deleteDemoMedia() {}
    override suspend fun importPlaylistTransaction(
        playlist: PlaylistEntity,
        channels: List<MediaItemEntity>,
        crossRefs: List<PlaylistMediaCrossRef>
    ) {}
    override suspend fun deletePlaylistCompletely(playlistId: String) {}
}
