package com.example.core.database.entity.infra

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "infra_connection_configs")
data class InfraConnectionConfigEntity(
    @PrimaryKey val serviceType: String, // n8n, vps, docker
    val baseUrl: String,
    val isEnabled: Boolean = true,
    val lastCheckTimestamp: Long = 0L,
    val lastStatus: String = "NOT_CONFIGURED", // NOT_CONFIGURED, CONNECTING, CONNECTED, UNAUTHORIZED, OFFLINE, ERROR
    val notes: String = ""
)

@Entity(tableName = "infra_status_caches")
data class InfraStatusCacheEntity(
    @PrimaryKey val serviceType: String, // n8n_workflows, n8n_executions, vps_metrics, docker_containers
    val jsonPayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val target: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS", // SUCCESS, FAILED
    val details: String = ""
)
