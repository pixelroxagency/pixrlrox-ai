package com.example.core.tools

import com.example.ui.screens.tools.ToolCatalog
import com.example.ui.screens.tools.ToolItem
import com.example.ui.screens.tools.ToolSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToolsManagerTest {

    @Test
    fun testDefaultQuickToolsContains12ValidIds() {
        val defaults = QuickToolsManager.DEFAULT_TOOL_IDS
        assertEquals(12, defaults.size)

        val catalog = ToolCatalog.getAllTools()
        val catalogIds = catalog.map { it.id }.toSet()

        defaults.forEach { id ->
            assertTrue("Default tool ID $id should exist in ToolCatalog", catalogIds.contains(id))
        }
    }

    @Test
    fun testNoDuplicateSelectedIdsInResolution() {
        val csvWithDuplicates = "prod_notes,doc_pdf_studio,prod_notes,util_calc_hub"
        val resolved = QuickToolsManager.resolveQuickTools(csvWithDuplicates)
        val ids = resolved.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
        assertEquals(3, resolved.size)
    }

    @Test
    fun testUserCanAddTool() {
        val current = listOf("prod_notes", "doc_pdf_studio")
        val updated = QuickToolsManager.addTool(current, "prod_downloads")
        assertEquals(3, updated.size)
        assertTrue(updated.contains("prod_downloads"))
    }

    @Test
    fun testUserCanRemoveTool() {
        val current = listOf("prod_notes", "doc_pdf_studio", "prod_downloads")
        val updated = QuickToolsManager.removeTool(current, "doc_pdf_studio")
        assertEquals(2, updated.size)
        assertFalse(updated.contains("doc_pdf_studio"))
    }

    @Test
    fun testUserCannotExceed12() {
        val current12 = QuickToolsManager.DEFAULT_TOOL_IDS
        assertEquals(12, current12.size)

        // Try adding a 13th tool
        val updated = QuickToolsManager.addTool(current12, "biz_projects")
        assertEquals(12, updated.size)
        assertFalse(updated.contains("biz_projects"))
    }

    @Test
    fun testUserCanSaveAndRestoreCustomOrder() {
        val customOrder = listOf("util_weather", "prod_notes", "doc_pdf_studio")
        val csv = customOrder.joinToString(",")

        val resolved = QuickToolsManager.resolveQuickTools(csv)
        val resolvedIds = resolved.map { it.id }

        assertEquals(customOrder, resolvedIds)
    }

    @Test
    fun testResetRestoresDefault12() {
        val reset = QuickToolsManager.resetToDefault()
        assertEquals(12, reset.size)
        assertEquals(QuickToolsManager.DEFAULT_TOOL_IDS, reset)
    }

    @Test
    fun testStaleOrRemovedToolIdDoesNotCrashAndFiltersOut() {
        val csvWithStale = "prod_notes,stale_fake_tool_id,doc_pdf_studio,another_removed_id"
        val resolved = QuickToolsManager.resolveQuickTools(csvWithStale)
        assertEquals(2, resolved.size)
        val ids = resolved.map { it.id }
        assertTrue(ids.contains("prod_notes"))
        assertTrue(ids.contains("doc_pdf_studio"))
        assertFalse(ids.contains("stale_fake_tool_id"))
    }

    @Test
    fun testFewerThan12ToolsPreservedWhenCustomized() {
        val custom5 = listOf("prod_notes", "doc_pdf_studio", "util_weather", "util_islamic", "prod_downloads")
        val csv = custom5.joinToString(",")
        val resolved = QuickToolsManager.resolveQuickTools(csv)
        assertEquals(5, resolved.size)
    }

    @Test
    fun testSearchSearchesFullCatalogNotOnlySelectedTools() {
        val selected3 = listOf("prod_notes", "doc_pdf_studio", "util_weather")
        val csv = selected3.joinToString(",")
        val resolvedSelected = QuickToolsManager.resolveQuickTools(csv)
        assertEquals(3, resolvedSelected.size)

        // "PDF" or "Docker" or "Projects" or "Image"
        val allTools = ToolCatalog.getAllTools()
        val categories = ToolCatalog.categories

        val searchResults = ToolSearchEngine.search("Docker", allTools, categories)
        assertTrue(searchResults.isNotEmpty())
        assertEquals("infra_docker", searchResults.first().id)
        assertNotNull(searchResults.first().route)
    }

    @Test
    fun testMoveToolUpAndDown() {
        val tools = listOf("A", "B", "C")
        val movedUp = QuickToolsManager.moveToolUp(tools, "B")
        assertEquals(listOf("B", "A", "C"), movedUp)

        val movedDown = QuickToolsManager.moveToolDown(movedUp, "B")
        assertEquals(listOf("A", "B", "C"), movedDown)
    }

    @Test
    fun testMaximum12ToolsGridLimit() {
        val resolved = QuickToolsManager.resolveQuickTools(null)
        assertTrue(resolved.size <= 12)
    }

    @Test
    fun testNoDuplicateToolCatalogDefinitions() {
        val catalog = ToolCatalog.getAllTools()
        val distinctIds = catalog.map { it.id }.distinct()
        assertEquals(catalog.size, distinctIds.size)
    }
}
