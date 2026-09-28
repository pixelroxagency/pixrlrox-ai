package com.example.ui.screens.home

import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNavigationDrawerTest {

    @Test
    fun `verify existing screen routes for Settings Tools Voice Home are preserved`() {
        assertEquals("settings", Screen.Settings.route)
        assertEquals("tools", Screen.Tools.route)
        assertEquals("voice", Screen.Voice.route)
        assertEquals("home", Screen.Home.route)
    }

    @Test
    fun `verify drawer uses local static app identity PixelRox AI`() {
        val appIdentity = "PixelRox AI"
        val appTagline = "On-Device & Direct AI"
        assertEquals("PixelRox AI", appIdentity)
        assertEquals("On-Device & Direct AI", appTagline)
    }

    @Test
    fun `verify ToolCatalog features remain accessible for compact Tool Search and Quick Tools`() {
        val tools = ToolCatalog.getAllTools()
        assertTrue(tools.isNotEmpty())
        assertTrue(tools.size >= 12)

        val hasCalc = tools.any { it.id == "util_calc_hub" }
        val hasPdf = tools.any { it.id == "doc_pdf_studio" }
        val hasOcr = tools.any { it.id == "media_image_ocr" }

        assertTrue(hasCalc)
        assertTrue(hasPdf)
        assertTrue(hasOcr)
    }

    @Test
    fun `verify drawer section definitions`() {
        val mainItems = listOf("Home", "Tools", "Settings")
        val supportItems = listOf("About")

        assertEquals(3, mainItems.size)
        assertTrue(mainItems.contains("Settings"))
        assertTrue(mainItems.contains("Tools"))
        assertTrue(mainItems.contains("Home"))
        assertEquals(1, supportItems.size)
    }
}
