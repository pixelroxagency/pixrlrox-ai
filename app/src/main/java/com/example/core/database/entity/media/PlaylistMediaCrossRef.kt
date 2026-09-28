package com.example.core.database.entity.media
import androidx.room.Entity
@Entity(tableName = "playlist_media_cross_ref", primaryKeys = ["playlistId", "mediaId"])
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: String,
    val itemOrder: Int
)
