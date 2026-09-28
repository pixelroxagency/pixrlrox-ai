package com.example.data.repository

import com.example.core.database.dao.AlertDao
import com.example.core.database.entity.AlertEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AlertRepository(
    private val alertDao: AlertDao
) {
    fun getAllAlerts(): Flow<List<AlertEntity>> = alertDao.getAllAlerts()

    fun getUnreadCount(): Flow<Int> = alertDao.getUnreadCount()

    fun getAlertsBySource(source: String): Flow<List<AlertEntity>> = alertDao.getAlertsByCategory(source)

    fun getAlertsBySeverity(severity: String): Flow<List<AlertEntity>> = alertDao.getAlertsBySeverity(severity)

    suspend fun addAlert(category: String, title: String, message: String, actionPayload: String? = null): Long =
        addNormalizedAlert(category, title, message, "Medium", null, actionPayload)

    suspend fun addNormalizedAlert(
        source: String,
        title: String,
        message: String,
        severity: String = "Medium",
        deduplicationKey: String? = null,
        actionPayload: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val dedup = deduplicationKey
        if (!dedup.isNullOrBlank()) {
            val existing = alertDao.getAlertByDeduplicationKey(dedup)
            if (existing != null) {
                alertDao.updateExistingAlert(
                    id = existing.id,
                    title = title,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )
                return@withContext existing.id
            }
        }

        val alert = AlertEntity(
            category = source.uppercase(),
            title = title,
            message = message,
            severity = severity,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            actionPayload = actionPayload,
            deduplicationKey = deduplicationKey
        )
        alertDao.insert(alert)
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    suspend fun toggleReadState(id: Long, isRead: Boolean) = withContext(Dispatchers.IO) {
        alertDao.setReadState(id, isRead)
    }

    suspend fun markAsRead(id: Long) = withContext(Dispatchers.IO) {
        alertDao.markAsRead(id)
    }

    suspend fun markAsUnread(id: Long) = withContext(Dispatchers.IO) {
        alertDao.markAsUnread(id)
    }

    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        alertDao.markAllAsRead()
    }

    suspend fun deleteAlert(id: Long) = withContext(Dispatchers.IO) {
        alertDao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        alertDao.clearAll()
    }
}
