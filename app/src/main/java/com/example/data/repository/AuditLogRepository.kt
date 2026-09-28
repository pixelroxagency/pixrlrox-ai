package com.example.data.repository

import com.example.core.database.dao.infra.InfraDao
import com.example.core.database.entity.infra.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AuditLogRepository(private val infraDao: InfraDao) {

    val recentAuditLogs: Flow<List<AuditLogEntity>> = infraDao.getRecentAuditLogs(100)

    suspend fun logAction(action: String, target: String, status: String = "SUCCESS", details: String = "") {
        val log = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            action = action,
            target = target,
            timestamp = System.currentTimeMillis(),
            status = status,
            details = details
        )
        infraDao.insertAuditLog(log)
    }
}
