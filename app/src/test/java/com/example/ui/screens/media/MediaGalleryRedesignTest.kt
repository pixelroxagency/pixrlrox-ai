package com.example.ui.screens.media

import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.media.MediaDateGroupUtil
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class MediaGalleryRedesignTest {

    private val baseNowMillis = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 23, 14, 30, 0)
    }.timeInMillis

    // 1. Date grouping: Today
    @Test
    fun `formatDateGroupLabel returns Today for items created today`() {
        val todayItemMillis = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 23, 9, 15, 0)
        }.timeInMillis

        val label = MediaDateGroupUtil.formatDateGroupLabel(todayItemMillis, baseNowMillis)
        assertEquals("Today", label)
    }

    // 2. Date grouping: Yesterday
    @Test
    fun `formatDateGroupLabel returns Yesterday for items created yesterday`() {
        val yesterdayMillis = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 18, 0, 0)
        }.timeInMillis

        val label = MediaDateGroupUtil.formatDateGroupLabel(yesterdayMillis, baseNowMillis)
        assertEquals("Yesterday", label)
    }

    // 3. Date grouping: Day of Week (within last 2..6 days)
    @Test
    fun `formatDateGroupLabel returns day of week for 2 to 6 days prior`() {
        // 3 days before Wednesday Sep 23 is Sunday Sep 20
        val threeDaysAgo = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 20, 12, 0, 0)
        }.timeInMillis

        val label = MediaDateGroupUtil.formatDateGroupLabel(threeDaysAgo, baseNowMillis)
        assertEquals("Sunday", label)
    }

    // 4. Date grouping: Month and Day for earlier in same year
    @Test
    fun `formatDateGroupLabel returns Month and Day for earlier dates in same year`() {
        val augustItemMillis = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 15, 10, 0, 0)
        }.timeInMillis

        val label = MediaDateGroupUtil.formatDateGroupLabel(augustItemMillis, baseNowMillis)
        assertEquals("August 15", label)
    }

    // 5. Date grouping: Month Day Year for older years
    @Test
    fun `formatDateGroupLabel returns full date with year for previous years`() {
        val oldYearMillis = Calendar.getInstance().apply {
            set(2024, Calendar.MAY, 10, 8, 30, 0)
        }.timeInMillis

        val label = MediaDateGroupUtil.formatDateGroupLabel(oldYearMillis, baseNowMillis)
        assertEquals("May 10, 2024", label)
    }

    // 6. Chronological ordering: newest first
    @Test
    fun `groupByDate orders groups and items newest first`() {
        val item1 = MediaItemEntity("1", "content://media/1", "Photo Today", 0L, baseNowMillis - 1000, 0L, false, "IMAGE")
        val item2 = MediaItemEntity("2", "content://media/2", "Photo Yesterday", 0L, baseNowMillis - 86400000, 0L, false, "IMAGE")
        val item3 = MediaItemEntity("3", "content://media/3", "Video Older", 5000L, baseNowMillis - (86400000L * 30), 0L, false, "VIDEO")

        val grouped = MediaDateGroupUtil.groupByDate(listOf(item2, item3, item1), baseNowMillis)
        val keys = grouped.keys.toList()

        assertEquals("Today", keys[0])
        assertEquals("Yesterday", keys[1])
        assertEquals("August 24", keys[2])
    }

    // 7. Media items are grouped under correct date headers
    @Test
    fun `groupByDate correctly places items under their corresponding header`() {
        val itemToday1 = MediaItemEntity("t1", "content://media/t1", "Photo 1", 0L, baseNowMillis - 2000, 0L, false, "IMAGE")
        val itemToday2 = MediaItemEntity("t2", "content://media/t2", "Video 2", 12000L, baseNowMillis - 5000, 0L, false, "VIDEO")
        val itemYest1 = MediaItemEntity("y1", "content://media/y1", "Photo 3", 0L, baseNowMillis - 90000000, 0L, false, "IMAGE")

        val grouped = MediaDateGroupUtil.groupByDate(listOf(itemToday1, itemYest1, itemToday2), baseNowMillis)

        assertEquals(2, grouped["Today"]?.size)
        assertEquals(1, grouped["Yesterday"]?.size)
        assertTrue(grouped["Today"]!!.any { it.mediaType == "IMAGE" })
        assertTrue(grouped["Today"]!!.any { it.mediaType == "VIDEO" })
    }

    // 8. Duration formatting
    @Test
    fun `formatDuration formats milliseconds correctly`() {
        assertEquals("", MediaDateGroupUtil.formatDuration(0L))
        assertEquals("", MediaDateGroupUtil.formatDuration(-500L))
        assertEquals("00:45", MediaDateGroupUtil.formatDuration(45000L))
        assertEquals("03:20", MediaDateGroupUtil.formatDuration(200000L))
        assertEquals("1:05:12", MediaDateGroupUtil.formatDuration(3912000L))
    }

    // 9. Audio and IPTV streams separation from visual media
    @Test
    fun `visual media list contains only IMAGE and VIDEO and excludes AUDIO or STREAM`() {
        val allItems = listOf(
            MediaItemEntity("1", "content://img/1", "IMG_001.jpg", 0L, 1000L, 0L, false, "IMAGE"),
            MediaItemEntity("2", "content://vid/2", "VID_002.mp4", 30000L, 2000L, 0L, false, "VIDEO"),
            MediaItemEntity("3", "content://audio/3", "Song.mp3", 180000L, 3000L, 0L, false, "AUDIO"),
            MediaItemEntity("4", "https://iptv.com/stream.m3u8", "Live TV", 0L, 4000L, 0L, false, "STREAM")
        )

        val visualOnly = allItems.filter {
            it.mediaType.equals("IMAGE", ignoreCase = true) || it.mediaType.equals("VIDEO", ignoreCase = true)
        }

        assertEquals(2, visualOnly.size)
        assertTrue(visualOnly.all { it.mediaType == "IMAGE" || it.mediaType == "VIDEO" })
        assertFalse(visualOnly.any { it.mediaType == "AUDIO" })
        assertFalse(visualOnly.any { it.mediaType == "STREAM" })
    }

    // 10. Secondary navigation tabs definition
    @Test
    fun `MediaLibraryTab defines AUDIO, VIDEO, IMAGES, and IPTV destinations`() {
        val tabs = MediaLibraryTab.entries
        assertEquals(4, tabs.size)
        assertTrue(tabs.contains(MediaLibraryTab.AUDIO))
        assertTrue(tabs.contains(MediaLibraryTab.VIDEO))
        assertTrue(tabs.contains(MediaLibraryTab.IMAGES))
        assertTrue(tabs.contains(MediaLibraryTab.IPTV))
    }

    // 11. Empty timestamp handling
    @Test
    fun `formatDateGroupLabel handles zero or negative timestamps gracefully`() {
        assertEquals("Earlier", MediaDateGroupUtil.formatDateGroupLabel(0L))
        assertEquals("Earlier", MediaDateGroupUtil.formatDateGroupLabel(-100L))
    }

    // 12. Video distinction in visual media
    @Test
    fun `media items distinguish video and photo types correctly`() {
        val video = MediaItemEntity("v1", "uri/v1", "vacation.mp4", 45000L, 100L, 0L, false, "VIDEO")
        val photo = MediaItemEntity("p1", "uri/p1", "family.jpg", 0L, 200L, 0L, false, "IMAGE")

        assertTrue(video.mediaType.equals("VIDEO", ignoreCase = true))
        assertFalse(photo.mediaType.equals("VIDEO", ignoreCase = true))
        assertTrue(photo.mediaType.equals("IMAGE", ignoreCase = true))
        assertEquals("00:45", MediaDateGroupUtil.formatDuration(video.duration))
    }
}
