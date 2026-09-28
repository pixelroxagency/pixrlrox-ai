package com.example.ui.screens.media

import com.example.core.database.entity.media.MediaItemEntity

enum class ImageSortOption(val displayName: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_A_Z("Name A–Z"),
    NAME_Z_A("Name Z–A"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first")
}

enum class VideoSortOption(val displayName: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_A_Z("Name A–Z"),
    NAME_Z_A("Name Z–A"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first"),
    DURATION_LONGEST("Longest duration"),
    DURATION_SHORTEST("Shortest duration")
}

enum class AudioSortOption(val displayName: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_A_Z("Name A–Z"),
    NAME_Z_A("Name Z–A"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first"),
    DURATION_LONGEST("Longest duration"),
    DURATION_SHORTEST("Shortest duration")
}

enum class IptvSortOption(val displayName: String) {
    DEFAULT("Playlist order / Default"),
    NAME_A_Z("Channel Name A–Z"),
    NAME_Z_A("Channel Name Z–A"),
    GROUP("Group/Category")
}

data class IptvChannel(
    val id: String,
    val uri: String,
    val title: String,
    val group: String? = null,
    val logoUrl: String? = null,
    val rawItem: MediaItemEntity
) {
    companion object {
        fun fromEntity(entity: MediaItemEntity): IptvChannel {
            if (entity.title.contains("|||")) {
                val parts = entity.title.split("|||")
                val cleanTitle = parts.getOrNull(0)?.trim().orEmpty().ifEmpty { "Channel" }
                val group = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                val logo = parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                return IptvChannel(
                    id = entity.id,
                    uri = entity.uri,
                    title = cleanTitle,
                    group = group,
                    logoUrl = logo,
                    rawItem = entity
                )
            }
            return IptvChannel(
                id = entity.id,
                uri = entity.uri,
                title = entity.title,
                group = null,
                logoUrl = null,
                rawItem = entity
            )
        }
    }
}

object MediaSortUtil {

    fun sortImages(items: List<MediaItemEntity>, sortOption: ImageSortOption): List<MediaItemEntity> {
        return when (sortOption) {
            ImageSortOption.NEWEST -> items.sortedByDescending { it.lastPlayed }
            ImageSortOption.OLDEST -> items.sortedBy { it.lastPlayed }
            ImageSortOption.NAME_A_Z -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            ImageSortOption.NAME_Z_A -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            ImageSortOption.LARGEST -> items.sortedByDescending { it.duration } // duration holds size for images
            ImageSortOption.SMALLEST -> items.sortedBy { it.duration }
        }
    }

    fun sortVideos(items: List<MediaItemEntity>, sortOption: VideoSortOption): List<MediaItemEntity> {
        return when (sortOption) {
            VideoSortOption.NEWEST -> items.sortedByDescending { it.lastPlayed }
            VideoSortOption.OLDEST -> items.sortedBy { it.lastPlayed }
            VideoSortOption.NAME_A_Z -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            VideoSortOption.NAME_Z_A -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            VideoSortOption.LARGEST -> items.sortedByDescending { it.duration }
            VideoSortOption.SMALLEST -> items.sortedBy { it.duration }
            VideoSortOption.DURATION_LONGEST -> items.sortedByDescending { it.duration }
            VideoSortOption.DURATION_SHORTEST -> items.sortedBy { it.duration }
        }
    }

    fun sortAudio(items: List<MediaItemEntity>, sortOption: AudioSortOption): List<MediaItemEntity> {
        return when (sortOption) {
            AudioSortOption.NEWEST -> items.sortedByDescending { it.lastPlayed }
            AudioSortOption.OLDEST -> items.sortedBy { it.lastPlayed }
            AudioSortOption.NAME_A_Z -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            AudioSortOption.NAME_Z_A -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            AudioSortOption.LARGEST -> items.sortedByDescending { it.duration }
            AudioSortOption.SMALLEST -> items.sortedBy { it.duration }
            AudioSortOption.DURATION_LONGEST -> items.sortedByDescending { it.duration }
            AudioSortOption.DURATION_SHORTEST -> items.sortedBy { it.duration }
        }
    }

    fun sortIptvChannels(channels: List<IptvChannel>, sortOption: IptvSortOption): List<IptvChannel> {
        return when (sortOption) {
            IptvSortOption.DEFAULT -> channels
            IptvSortOption.NAME_A_Z -> channels.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            IptvSortOption.NAME_Z_A -> channels.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            IptvSortOption.GROUP -> channels.sortedWith(
                compareBy<IptvChannel, String>(String.CASE_INSENSITIVE_ORDER) { it.group ?: "ZZZZ" }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        }
    }

    fun getNextChannel(channels: List<IptvChannel>, currentChannel: IptvChannel?): IptvChannel? {
        if (channels.isEmpty()) return null
        if (currentChannel == null) return channels.first()
        val currentIndex = channels.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex == -1) return channels.first()
        val nextIndex = (currentIndex + 1) % channels.size
        return channels[nextIndex]
    }

    fun getPreviousChannel(channels: List<IptvChannel>, currentChannel: IptvChannel?): IptvChannel? {
        if (channels.isEmpty()) return null
        if (currentChannel == null) return channels.last()
        val currentIndex = channels.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex == -1) return channels.last()
        val prevIndex = if (currentIndex - 1 < 0) channels.size - 1 else currentIndex - 1
        return channels[prevIndex]
    }
}
