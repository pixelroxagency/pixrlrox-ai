package com.example.ui.screens.home

import com.squareup.moshi.JsonClass

enum class HomeWidgetType(val displayName: String) {
    AI_STATUS("AI Agent Status"),
    NEXT_PRAYER("Next Prayer & Islamic Hub"),
    PERSONAL_TASKS("Personal Tasks & Reminders"),
    UNIFIED_ALERTS("Unified Alerts Center"),
    QUICK_ACTIONS("Quick Actions Hub"),
    SYSTEM_INFRA("System Infrastructure & Uptime"),
    BUSINESS_OPERATIONS("Business & Invoices")
}

data class HomeWidgetState(
    val type: HomeWidgetType,
    val isVisible: Boolean = true,
    val order: Int
)

object HomeWidgetConfig {
    val defaultList = listOf(
        HomeWidgetState(HomeWidgetType.AI_STATUS, true, 0),
        HomeWidgetState(HomeWidgetType.NEXT_PRAYER, true, 1),
        HomeWidgetState(HomeWidgetType.PERSONAL_TASKS, true, 2),
        HomeWidgetState(HomeWidgetType.UNIFIED_ALERTS, true, 3),
        HomeWidgetState(HomeWidgetType.QUICK_ACTIONS, true, 4),
        HomeWidgetState(HomeWidgetType.SYSTEM_INFRA, true, 5),
        HomeWidgetState(HomeWidgetType.BUSINESS_OPERATIONS, true, 6)
    )

    fun parseLayoutString(saved: String?): List<HomeWidgetState> {
        if (saved.isNullOrBlank()) return defaultList
        return try {
            val parts = saved.split(";")
            val list = mutableListOf<HomeWidgetState>()
            parts.forEachIndexed { index, item ->
                val tokens = item.split(":")
                if (tokens.size == 2) {
                    val type = HomeWidgetType.valueOf(tokens[0])
                    val isVisible = tokens[1].toBoolean()
                    list.add(HomeWidgetState(type, isVisible, index))
                }
            }
            // Ensure any missing types are appended
            val missing = HomeWidgetType.values().filter { type -> list.none { it.type == type } }
            missing.forEachIndexed { idx, type ->
                list.add(HomeWidgetState(type, true, list.size + idx))
            }
            list.sortedBy { it.order }
        } catch (_: Exception) {
            defaultList
        }
    }

    fun serializeLayoutList(list: List<HomeWidgetState>): String {
        return list.sortedBy { it.order }.joinToString(";") { "${it.type.name}:${it.isVisible}" }
    }
}
