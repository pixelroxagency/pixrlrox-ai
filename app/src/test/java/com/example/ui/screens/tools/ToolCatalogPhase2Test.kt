package com.example.ui.screens.tools

import com.example.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class ToolCatalogPhase2Test {

    private val removedIds = listOf(
        "media_photo_grid",
        "media_thumbnail_maker",
        "media_story_canvas",
        "media_gif_maker",
        "media_gif_to_video",
        "doc_pdf_text_editor",
        "doc_scanned_pdf_text_editor",
        "text_deeplink",
        "dev_screen_light",
        "dev_flashlight",
        "prod_scanner",
        "media_collage",
        "media_image_text_editor",
        "util_prayer",
        "util_tasbih",
        "prod_clipboard",
        "prod_secure_notes",
        "prod_checklist",
        "prod_habit",
        "prod_pomodoro"
    )

    private val requiredPreserved = listOf(
        "media_image_studio" to Screen.ImageStudio.route,
        "media_video_studio" to Screen.VideoStudio.route,
        "media_audio_studio" to Screen.AudioStudio.route,
        "doc_pdf_studio" to Screen.PdfStudio.route,
        "text_dev_toolkit" to Screen.DevTextToolkit.route,
        "qr_share_hub" to Screen.QrShareHub.route,
        "media_quote_maker" to Screen.PostQuoteMaker.route,
        "media_image_ocr" to Screen.ImageToTextOcr.route,
        "media_ai_images" to Screen.AIImageTools.route,
        "media_text_to_speech" to Screen.TextToSpeech.route,
        "util_islamic" to Screen.IslamicHub.route,
        "util_timer_hub" to Screen.TimerClockHub.route,
        "prod_tasks" to Screen.Tasks.route,
        "prod_notes" to Screen.Notes.route
    )

    @Test
    fun `tool catalog visible count is exactly 33 and categories count is 5 max`() {
        val allCategories = ToolCatalog.categories
        assertTrue("Categories must be 5 or fewer", allCategories.size <= 5)
        val totalToolsInCategories = allCategories.sumOf { it.tools.size }
        assertEquals(33, totalToolsInCategories)
    }

    @Test
    fun `tool catalog searchable count is exactly 33 with all unique IDs`() {
        val allTools = ToolCatalog.getAllTools()
        assertEquals(33, allTools.size)

        val uniqueIds = allTools.map { it.id }.toSet()
        assertEquals(33, uniqueIds.size)
    }

    @Test
    fun `all removed placeholder tools are absent from catalog and search`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        for (removedId in removedIds) {
            assertNull("Tool $removedId should not exist in catalog", allTools[removedId])
        }
    }

    @Test
    fun `all required preserved tools exist with correct routes`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        for ((id, expectedRoute) in requiredPreserved) {
            val tool = allTools[id]
            assertNotNull("Preserved tool $id must exist in catalog", tool)
            assertEquals("Route mismatch for $id", expectedRoute, tool?.route)
        }
    }

    @Test
    fun `home ToolSearchEngine cannot return any of the five removed placeholder tools`() {
        val allTools = ToolCatalog.getAllTools()
        val categories = ToolCatalog.categories

        val searchQueries = listOf("clipboard", "secure", "checklist", "habit", "pomodoro")
        for (query in searchQueries) {
            val results = ToolSearchEngine.search(query, allTools, categories)
            val removedIdsSet = setOf("prod_clipboard", "prod_secure_notes", "prod_checklist", "prod_habit", "prod_pomodoro")
            assertTrue("Search query '$query' should not return any removed placeholder tools", results.none { it.id in removedIdsSet })
        }
    }

    @Test
    fun `existing routes and screens for removed productivity tools remain accessible in navigation`() {
        assertEquals("clipboard_history", Screen.ClipboardHistory.route)
        assertEquals("secure_notes", Screen.SecureNotes.route)
        assertEquals("daily_checklist", Screen.DailyChecklist.route)
        assertEquals("habit_counter", Screen.HabitCounter.route)
        assertEquals("pomodoro_timer", Screen.PomodoroTimer.route)
    }
}
