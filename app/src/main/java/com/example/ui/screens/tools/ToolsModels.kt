package com.example.ui.screens.tools

import androidx.compose.ui.graphics.vector.ImageVector

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val route: String? = null,
    val categoryId: String,
    val keywords: List<String> = emptyList()
)

data class ToolCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tools: List<ToolItem>
)
