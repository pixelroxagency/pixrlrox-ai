package com.example.ui.screens.islamic

import com.example.core.prayer.PrayerCalculationEngine
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class IslamicHubPhase1Test {

    @Test
    fun `ISLAMIC_HUB_PHASE1 - groups and feature counts`() {
        // 4 Hub groups
        val groups = HubGroup.entries
        assertEquals(4, groups.size)
        assertTrue(groups.contains(HubGroup.ESSENTIALS))
        assertTrue(groups.contains(HubGroup.QURAN_SUNNAH))
        assertTrue(groups.contains(HubGroup.WORSHIP_GUIDES))
        assertTrue(groups.contains(HubGroup.EXPLORE))
    }

    @Test
    fun `ISLAMIC_HUB_PHASE1 - tool catalog preservation`() {
        // Islamic Hub is the single entry point in ToolCatalog
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        assertNotNull("util_islamic must exist", allTools["util_islamic"])
        assertEquals("Islamic Hub", allTools["util_islamic"]?.title)
        assertEquals(Screen.IslamicHub.route, allTools["util_islamic"]?.route)

        // No new top-level tool catalog items created
        assertNull(allTools["util_quran"])
        assertNull(allTools["util_hadith"])
        assertNull(allTools["util_dua"])
        assertNull(allTools["util_zakat"])
        assertNull(allTools["util_hajj"])
        assertNull(allTools["util_names"])
        assertNull(allTools["util_events"])
        assertNull(allTools["util_mosque"])
    }

    @Test
    fun `ISLAMIC_HUB_PHASE1 - existing prayer and tasbih calculation engines preserved`() {
        // PrayerCalculationEngine continues to provide calculations without duplicates
        val schedule = PrayerCalculationEngine.calculateTimes(
            latitude = 21.4225, // Makkah
            longitude = 39.8262,
            calendar = Calendar.getInstance()
        )
        assertNotNull(schedule)
        assertTrue(schedule.fajr > 0)
        assertTrue(schedule.maghrib > 0)
        assertTrue(schedule.sehriEnds > 0)
        assertTrue(schedule.iftarTime > 0)
        assertTrue(schedule.hijriDateString.isNotEmpty())

        // Routes preserved
        assertEquals("prayer", Screen.Prayer.route)
        assertEquals("tasbih", Screen.Tasbih.route)
        assertEquals("islamic_hub", Screen.IslamicHub.route)
    }
}
