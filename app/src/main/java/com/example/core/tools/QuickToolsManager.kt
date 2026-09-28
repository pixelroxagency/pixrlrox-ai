package com.example.core.tools

import com.example.ui.screens.tools.ToolCatalog
import com.example.ui.screens.tools.ToolItem

object QuickToolsManager {
    const val MAX_QUICK_TOOLS = 12

    val DEFAULT_TOOL_IDS = listOf(
        "prod_notes",
        "doc_pdf_studio",
        "prod_downloads",
        "util_calc_hub",
        "util_islamic",
        "util_weather",
        "util_system",
        "media_gallery",
        "media_image_studio",
        "media_ai_images",
        "text_dev_toolkit",
        "qr_share_hub"
    )

    fun resolveQuickTools(
        savedLayoutCsv: String?,
        catalogTools: List<ToolItem> = ToolCatalog.getAllTools()
    ): List<ToolItem> {
        val validIdsMap = catalogTools.associateBy { it.id }
        if (savedLayoutCsv.isNullOrBlank()) {
            return DEFAULT_TOOL_IDS.mapNotNull { validIdsMap[it] }
        }

        val savedIds = savedLayoutCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val resolved = savedIds.mapNotNull { validIdsMap[it] }
        if (resolved.isEmpty() && savedIds.isNotEmpty()) {
            return DEFAULT_TOOL_IDS.mapNotNull { validIdsMap[it] }
        }
        return resolved.take(MAX_QUICK_TOOLS)
    }

    fun addTool(
        currentIds: List<String>,
        toolId: String,
        maxLimit: Int = MAX_QUICK_TOOLS
    ): List<String> {
        if (currentIds.contains(toolId)) return currentIds
        if (currentIds.size >= maxLimit) return currentIds
        return currentIds + toolId
    }

    fun removeTool(currentIds: List<String>, toolId: String): List<String> {
        return currentIds.filter { it != toolId }
    }

    fun moveToolUp(currentIds: List<String>, toolId: String): List<String> {
        val index = currentIds.indexOf(toolId)
        if (index <= 0) return currentIds
        val mutable = currentIds.toMutableList()
        val temp = mutable[index]
        mutable[index] = mutable[index - 1]
        mutable[index - 1] = temp
        return mutable.toList()
    }

    fun moveToolDown(currentIds: List<String>, toolId: String): List<String> {
        val index = currentIds.indexOf(toolId)
        if (index < 0 || index >= currentIds.size - 1) return currentIds
        val mutable = currentIds.toMutableList()
        val temp = mutable[index]
        mutable[index] = mutable[index + 1]
        mutable[index + 1] = temp
        return mutable.toList()
    }

    fun resetToDefault(): List<String> = DEFAULT_TOOL_IDS
}
