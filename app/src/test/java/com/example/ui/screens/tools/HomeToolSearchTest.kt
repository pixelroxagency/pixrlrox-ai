package com.example.ui.screens.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeToolSearchTest {

    @Test
    fun `empty and blank queries return empty list`() {
        val tools = ToolCatalog.getAllTools()
        assertTrue(ToolSearchEngine.search("").isEmpty())
        assertTrue(ToolSearchEngine.search("   ").isEmpty())
    }

    @Test
    fun `exact title match ranks at top`() {
        val tools = ToolCatalog.getAllTools()
        val results = ToolSearchEngine.search("Smart Calculator", tools)
        assertFalse(results.isEmpty())
        assertEquals("util_calc_hub", results.first().id)
        assertEquals("Smart Calculator", results.first().title)
    }

    @Test
    fun `prefix title match ranks above description matches`() {
        val tools = ToolCatalog.getAllTools()
        val results = ToolSearchEngine.search("im", tools)
        assertFalse(results.isEmpty())
        val topIds = results.take(3).map { it.id }
        assertTrue(topIds.contains("media_image_studio") || topIds.contains("media_image_ocr"))
    }

    @Test
    fun `search by alias keywords finds relevant consolidated studios`() {
        val tools = ToolCatalog.getAllTools()

        // "photo" -> should match Image Studio and AI Image Tools
        val photoResults = ToolSearchEngine.search("photo", tools)
        assertTrue(photoResults.any { it.id == "media_image_studio" })

        // "ocr" -> should match Image to Text OCR
        val ocrResults = ToolSearchEngine.search("ocr", tools)
        assertTrue(ocrResults.any { it.id == "media_image_ocr" })

        // "pdf" -> should match PDF Studio
        val pdfResults = ToolSearchEngine.search("pdf", tools)
        assertTrue(pdfResults.any { it.id == "doc_pdf_studio" })

        // "qr" or "scan" -> should match QR & Share Hub
        val qrResults = ToolSearchEngine.search("qr", tools)
        assertTrue(qrResults.any { it.id == "qr_share_hub" })

        val scanResults = ToolSearchEngine.search("scan", tools)
        assertTrue(scanResults.any { it.id == "qr_share_hub" })

        // "video" -> should match Video Studio
        val videoResults = ToolSearchEngine.search("video", tools)
        assertTrue(videoResults.any { it.id == "media_video_studio" })

        // "audio" -> should match Audio Studio
        val audioResults = ToolSearchEngine.search("audio", tools)
        assertTrue(audioResults.any { it.id == "media_audio_studio" })

        // "password" -> should match Security & Privacy
        val passwordResults = ToolSearchEngine.search("password", tools)
        assertTrue(passwordResults.any { it.id == "sec_privacy_hub" })

        // "docker" -> should match Docker Dashboard
        val dockerResults = ToolSearchEngine.search("docker", tools)
        assertTrue(dockerResults.any { it.id == "infra_docker" })

        // "islamic" / "prayer" / "tasbih" -> should match Islamic Hub
        val islamicResults = ToolSearchEngine.search("prayer", tools)
        assertTrue(islamicResults.any { it.id == "util_islamic" })
    }

    @Test
    fun `search is strictly case-insensitive`() {
        val tools = ToolCatalog.getAllTools()
        val lower = ToolSearchEngine.search("notes", tools)
        val upper = ToolSearchEngine.search("NOTES", tools)
        val mixed = ToolSearchEngine.search("nOtEs", tools)

        assertEquals(lower.map { it.id }, upper.map { it.id })
        assertEquals(lower.map { it.id }, mixed.map { it.id })
    }

    @Test
    fun `removed standalone tools are never returned in search`() {
        val tools = ToolCatalog.getAllTools()
        val prayerResults = ToolSearchEngine.search("prayer", tools)
        // Only util_islamic should be returned, util_prayer was removed
        assertFalse(prayerResults.any { it.id == "util_prayer" })
        assertFalse(prayerResults.any { it.id == "util_tasbih" })
        assertFalse(prayerResults.any { it.id == "media_image_text_editor" })
    }
}
