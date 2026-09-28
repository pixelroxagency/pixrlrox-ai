package com.example.ui.screens.islamic

import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test

class IslamicHubConsolidationTest {

    @Test
    fun `ISLAMIC_HUB_CONSOLIDATION_TEST - phase cleanup V1 verification`() {
        // 1. ToolCatalog preserves Islamic Hub
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        val islamicHub = allTools["util_islamic"]
        assertNotNull("util_islamic must exist in ToolCatalog", islamicHub)
        assertEquals("Islamic Hub", islamicHub?.title)
        assertEquals(Screen.IslamicHub.route, islamicHub?.route)

        // 2. Standalone cards util_prayer and util_tasbih are removed from ToolCatalog
        assertNull("util_prayer must be removed from top-level ToolCatalog", allTools["util_prayer"])
        assertNull("util_tasbih must be removed from top-level ToolCatalog", allTools["util_tasbih"])

        // 3. Search for util_prayer and util_tasbih returns empty
        val prayerSearch = ToolCatalog.getAllTools().filter {
            it.id.equals("util_prayer", ignoreCase = true) || it.title.equals("Prayer Times", ignoreCase = true)
        }
        assertTrue("Top-level catalog search for util_prayer must be empty", prayerSearch.isEmpty())

        val tasbihSearch = ToolCatalog.getAllTools().filter {
            it.id.equals("util_tasbih", ignoreCase = true) || it.title.equals("Tasbih Counter", ignoreCase = true)
        }
        assertTrue("Top-level catalog search for util_tasbih must be empty", tasbihSearch.isEmpty())

        // 4. Routes for Prayer, Tasbih, and IslamicHub are preserved
        assertEquals("prayer", Screen.Prayer.route)
        assertEquals("tasbih", Screen.Tasbih.route)
        assertEquals("islamic_hub", Screen.IslamicHub.route)

        // 5. Verify catalog counts
        val totalToolsInCategories = ToolCatalog.categories.sumOf { it.tools.size }
        assertEquals(33, totalToolsInCategories)
        assertEquals(33, allTools.size)
        val uniqueIds = ToolCatalog.getAllTools().map { it.id }.toSet()
        assertEquals(33, uniqueIds.size)
    }
}
