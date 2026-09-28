package com.example.core.database.dao.media

import androidx.room.*
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY lastPlayed DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE uri = :uri LIMIT 1")
    suspend fun getMediaByUri(uri: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE uri LIKE '%BigBuckBunny%' OR uri LIKE '%exoplayer-test-media%' OR uri LIKE '%gtv-videos-bucket%' OR title LIKE '%Big Buck Bunny%' OR title LIKE '%Google Play (HLS Stream)%'")
    suspend fun deleteDemoMedia()

    @Query("SELECT * FROM media_items WHERE mediaType = 'STREAM' ORDER BY lastPlayed DESC")
    fun getAllStreams(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN playlist_media_cross_ref r ON m.id = r.mediaId
        WHERE r.playlistId = :playlistId
        ORDER BY r.itemOrder ASC
    """)
    fun getMediaForPlaylist(playlistId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    fun getChannelCountForPlaylist(playlistId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsForPlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistMediaCrossRef(crossRef: PlaylistMediaCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<PlaylistMediaCrossRef>)

    @Query("DELETE FROM media_items WHERE id = :mediaId")
    suspend fun deleteMediaById(mediaId: String)

    @Query("DELETE FROM media_items WHERE id IN (SELECT mediaId FROM playlist_media_cross_ref WHERE playlistId = :playlistId)")
    suspend fun deleteMediaItemsForPlaylist(playlistId: String)

    @Transaction
    suspend fun importPlaylistTransaction(
        playlist: PlaylistEntity,
        mediaList: List<MediaItemEntity>,
        crossRefs: List<PlaylistMediaCrossRef>
    ) {
        insertPlaylist(playlist)
        insertMediaList(mediaList)
        insertCrossRefs(crossRefs)
    }

    @Transaction
    suspend fun deletePlaylistCompletely(playlistId: String) {
        deleteMediaItemsForPlaylist(playlistId)
        deleteCrossRefsForPlaylist(playlistId)
        deletePlaylistById(playlistId)
    }
}
