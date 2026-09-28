package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val profileId: String = "cheap",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val lastMessagePreview: String = ""
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isStreaming: Boolean = false
)

@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class MessageAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val remotePath: String,
    val localUri: String,
    val mimeType: String?,
    val originalFilename: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_tasks")
data class PersonalTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateEpoch: Long = 0L, // UTC midnight epoch timestamp
    val dueTimeMinutes: Int = -1, // Minutes from midnight (e.g. 14*60 + 30 = 870 for 2:30 PM), or -1
    val hasReminder: Boolean = false,
    val status: String = "PENDING", // "PENDING" or "COMPLETED"
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // AI, TASKS, UPTIME, BACKUP, SECURITY, WEBSITES, N8N, VPS, DOCKER, SYSTEM
    val title: String,
    val message: String,
    val severity: String = "Medium", // "Critical", "High", "Medium", "Low", "Info"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val actionPayload: String? = null,
    val deduplicationKey: String? = null
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String = "default",
    val isDefault: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsRuns: Boolean = true,
    val enabled: Boolean = true
)

@Entity(tableName = "saved_ludo_game")
data class LudoGameEntity(
    @PrimaryKey val id: Int = 1,
    val playerCount: Int,
    val currentTurnIndex: Int,
    val diceValue: Int,
    val hasRolledDice: Boolean,
    val consecutiveSixes: Int,
    val tokensJson: String,
    val winnerIndex: Int?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hermes_runs")
data class HermesRunReferenceEntity(
    @PrimaryKey val runId: String,
    val profileId: String,
    val title: String,
    val prompt: String,
    val status: String, // "QUEUED", "RUNNING", "WAITING_FOR_APPROVAL", "COMPLETED", "FAILED", "STOPPED"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val output: String? = null,
    val error: String? = null
)

@Entity(tableName = "direct_providers")
data class DirectProviderEntity(
    @PrimaryKey val id: String, // e.g. "openai", "gemini", "ollama", "custom"
    val displayName: String,
    val providerType: String, // "OPENAI_COMPATIBLE", "GEMINI", "OLLAMA"
    val baseUrl: String,
    val selectedModel: String,
    val isConfigured: Boolean = false,
    val isDefault: Boolean = false
)
