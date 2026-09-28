package com.example.core.database.entity.project

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val clientId: String? = null,
    val description: String = "",
    val status: String = "Planned", // Planned, Active, On Hold, Completed, Cancelled
    val priority: String = "Medium", // Low, Medium, High, Urgent
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val budget: Double = 0.0,
    val currency: String = "USD",
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "project_tasks")
data class ProjectTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val priority: String = "Medium", // Low, Medium, High, Urgent
    val dueDate: Long? = null,
    val position: Int = 0
)
