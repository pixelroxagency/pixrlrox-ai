package com.example.ui.screens.media

import com.example.core.database.entity.media.MediaItemEntity
import org.junit.Assert.*
import org.junit.Test

class MediaCenterModernLibraryTest {

    // 1. Tab enum verification
    @Test
    fun `MediaLibraryTab has four distinct destinations`() {
        val tabs = MediaLibraryTab.entries
        assertEquals(4, tabs.size)
        assertEquals(listOf("Audio", "Video", "Images", "IPTV"), tabs.map { it.title })
    }

    // 2. Image Sorting Tests
    @Test
    fun `sortImages correctly sorts by Newest and Oldest`() {
        val img1 = MediaItemEntity("1", "uri1", "Sunset", 1000L, 10000L, 0L, false, "IMAGE")
        val img2 = MediaItemEntity("2", "uri2", "Beach", 2000L, 20000L, 0L, false, "IMAGE")
        val img3 = MediaItemEntity("3", "uri3", "Mountains", 500L, 5000L, 0L, false, "IMAGE")

        val newest = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.NEWEST)
        assertEquals(listOf("2", "1", "3"), newest.map { it.id })

        val oldest = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.OLDEST)
        assertEquals(listOf("3", "1", "2"), oldest.map { it.id })
    }

    @Test
    fun `sortImages correctly sorts by Name A-Z and Z-A`() {
        val img1 = MediaItemEntity("1", "uri1", "Sunset", 1000L, 10000L, 0L, false, "IMAGE")
        val img2 = MediaItemEntity("2", "uri2", "Beach", 2000L, 20000L, 0L, false, "IMAGE")
        val img3 = MediaItemEntity("3", "uri3", "Mountains", 500L, 5000L, 0L, false, "IMAGE")

        val az = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.NAME_A_Z)
        assertEquals(listOf("Beach", "Mountains", "Sunset"), az.map { it.title })

        val za = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.NAME_Z_A)
        assertEquals(listOf("Sunset", "Mountains", "Beach"), za.map { it.title })
    }

    @Test
    fun `sortImages correctly sorts by Largest and Smallest file size`() {
        val img1 = MediaItemEntity("1", "uri1", "Small", 1024L, 10000L, 0L, false, "IMAGE")
        val img2 = MediaItemEntity("2", "uri2", "Large", 10485760L, 20000L, 0L, false, "IMAGE")
        val img3 = MediaItemEntity("3", "uri3", "Medium", 524288L, 5000L, 0L, false, "IMAGE")

        val largest = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.LARGEST)
        assertEquals(listOf("Large", "Medium", "Small"), largest.map { it.title })

        val smallest = MediaSortUtil.sortImages(listOf(img1, img2, img3), ImageSortOption.SMALLEST)
        assertEquals(listOf("Small", "Medium", "Large"), smallest.map { it.title })
    }

    // 3. Video Sorting Tests
    @Test
    fun `sortVideos correctly sorts by Longest and Shortest duration`() {
        val vid1 = MediaItemEntity("1", "uri1", "Short Clip", 15000L, 10000L, 0L, false, "VIDEO")
        val vid2 = MediaItemEntity("2", "uri2", "Movie", 7200000L, 20000L, 0L, false, "VIDEO")
        val vid3 = MediaItemEntity("3", "uri3", "Trailer", 120000L, 5000L, 0L, false, "VIDEO")

        val longest = MediaSortUtil.sortVideos(listOf(vid1, vid2, vid3), VideoSortOption.DURATION_LONGEST)
        assertEquals(listOf("Movie", "Trailer", "Short Clip"), longest.map { it.title })

        val shortest = MediaSortUtil.sortVideos(listOf(vid1, vid2, vid3), VideoSortOption.DURATION_SHORTEST)
        assertEquals(listOf("Short Clip", "Trailer", "Movie"), shortest.map { it.title })
    }

    // 4. Audio Sorting Tests
    @Test
    fun `sortAudio correctly sorts by Duration and Name`() {
        val aud1 = MediaItemEntity("1", "uri1", "Zebra Song", 180000L, 10000L, 0L, false, "AUDIO")
        val aud2 = MediaItemEntity("2", "uri2", "Alpha Track", 320000L, 20000L, 0L, false, "AUDIO")

        val nameAz = MediaSortUtil.sortAudio(listOf(aud1, aud2), AudioSortOption.NAME_A_Z)
        assertEquals(listOf("Alpha Track", "Zebra Song"), nameAz.map { it.title })

        val durLong = MediaSortUtil.sortAudio(listOf(aud1, aud2), AudioSortOption.DURATION_LONGEST)
        assertEquals(listOf("Alpha Track", "Zebra Song"), durLong.map { it.title })
    }

    // 5. IPTV Channel Metadata Parsing Tests
    @Test
    fun `IptvChannel fromEntity correctly extracts title, group, and logoUrl when encoded`() {
        val raw = MediaItemEntity(
            id = "pl_chan_1",
            uri = "https://example.com/stream.m3u8",
            title = "Sky Sports 1|||Sports|||https://example.com/logo.png",
            duration = 0L,
            lastPlayed = 0L,
            lastPosition = 0L,
            completed = false,
            mediaType = "STREAM"
        )

        val channel = IptvChannel.fromEntity(raw)
        assertEquals("Sky Sports 1", channel.title)
        assertEquals("Sports", channel.group)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
        assertEquals("https://example.com/stream.m3u8", channel.uri)
    }

    @Test
    fun `IptvChannel fromEntity handles plain unencoded title gracefully`() {
        val raw = MediaItemEntity(
            id = "stream_1",
            uri = "https://example.com/stream.m3u8",
            title = "BBC News Live",
            duration = 0L,
            lastPlayed = 0L,
            lastPosition = 0L,
            completed = false,
            mediaType = "STREAM"
        )

        val channel = IptvChannel.fromEntity(raw)
        assertEquals("BBC News Live", channel.title)
        assertNull(channel.group)
        assertNull(channel.logoUrl)
    }

    // 6. IPTV Sorting Tests
    @Test
    fun `sortIptvChannels sorts by Name A-Z and Group`() {
        val ch1 = IptvChannel("1", "u1", "CNN", "News", null, MediaItemEntity("1", "u1", "CNN", 0L, 0L, 0L, false, "STREAM"))
        val ch2 = IptvChannel("2", "u2", "ESPN", "Sports", null, MediaItemEntity("2", "u2", "ESPN", 0L, 0L, 0L, false, "STREAM"))
        val ch3 = IptvChannel("3", "u3", "BBC", "News", null, MediaItemEntity("3", "u3", "BBC", 0L, 0L, 0L, false, "STREAM"))

        val sortedAz = MediaSortUtil.sortIptvChannels(listOf(ch1, ch2, ch3), IptvSortOption.NAME_A_Z)
        assertEquals(listOf("BBC", "CNN", "ESPN"), sortedAz.map { it.title })

        val sortedGroup = MediaSortUtil.sortIptvChannels(listOf(ch1, ch2, ch3), IptvSortOption.GROUP)
        assertEquals(listOf("BBC", "CNN", "ESPN"), sortedGroup.map { it.title })
        assertEquals(listOf("News", "News", "Sports"), sortedGroup.map { it.group })
    }

    // 7. IPTV Previous / Next Navigation with wrap-around
    @Test
    fun `getNextChannel advances forward and wraps around from last to first`() {
        val ch1 = IptvChannel("1", "u1", "Ch1", null, null, MediaItemEntity("1", "u1", "Ch1", 0L, 0L, 0L, false, "STREAM"))
        val ch2 = IptvChannel("2", "u2", "Ch2", null, null, MediaItemEntity("2", "u2", "Ch2", 0L, 0L, 0L, false, "STREAM"))
        val ch3 = IptvChannel("3", "u3", "Ch3", null, null, MediaItemEntity("3", "u3", "Ch3", 0L, 0L, 0L, false, "STREAM"))
        val list = listOf(ch1, ch2, ch3)

        val nextFromCh1 = MediaSortUtil.getNextChannel(list, ch1)
        assertEquals("Ch2", nextFromCh1?.title)

        val nextFromCh2 = MediaSortUtil.getNextChannel(list, ch2)
        assertEquals("Ch3", nextFromCh2?.title)

        // Wrap around: Last -> First
        val nextFromCh3 = MediaSortUtil.getNextChannel(list, ch3)
        assertEquals("Ch1", nextFromCh3?.title)
    }

    @Test
    fun `getPreviousChannel steps backward and wraps around from first to last`() {
        val ch1 = IptvChannel("1", "u1", "Ch1", null, null, MediaItemEntity("1", "u1", "Ch1", 0L, 0L, 0L, false, "STREAM"))
        val ch2 = IptvChannel("2", "u2", "Ch2", null, null, MediaItemEntity("2", "u2", "Ch2", 0L, 0L, 0L, false, "STREAM"))
        val ch3 = IptvChannel("3", "u3", "Ch3", null, null, MediaItemEntity("3", "u3", "Ch3", 0L, 0L, 0L, false, "STREAM"))
        val list = listOf(ch1, ch2, ch3)

        val prevFromCh2 = MediaSortUtil.getPreviousChannel(list, ch2)
        assertEquals("Ch1", prevFromCh2?.title)

        // Wrap around: First -> Last
        val prevFromCh1 = MediaSortUtil.getPreviousChannel(list, ch1)
        assertEquals("Ch3", prevFromCh1?.title)
    }
}
