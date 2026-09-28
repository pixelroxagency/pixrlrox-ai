package com.example.data.repository

import com.example.core.database.dao.media.MediaDao
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {
    fun getAllMedia(): Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()
    suspend fun getMediaByUri(uri: String): MediaItemEntity? = mediaDao.getMediaByUri(uri)
    fun getAllStreams(): Flow<List<MediaItemEntity>> = mediaDao.getAllStreams()
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylists()
    fun getMediaForPlaylist(playlistId: String): Flow<List<MediaItemEntity>> = mediaDao.getMediaForPlaylist(playlistId)
    fun getChannelCountForPlaylist(playlistId: String): Flow<Int> = mediaDao.getChannelCountForPlaylist(playlistId)

    suspend fun insertMedia(media: MediaItemEntity) = mediaDao.insertMedia(media)
    suspend fun deleteMedia(mediaId: String) = mediaDao.deleteMediaById(mediaId)
    suspend fun deleteDemoMedia() = mediaDao.deleteDemoMedia()

    suspend fun importPlaylist(
        playlist: PlaylistEntity,
        channels: List<MediaItemEntity>,
        crossRefs: List<PlaylistMediaCrossRef>
    ) = mediaDao.importPlaylistTransaction(playlist, channels, crossRefs)

    suspend fun deletePlaylist(playlistId: String) = mediaDao.deletePlaylistCompletely(playlistId)
}
