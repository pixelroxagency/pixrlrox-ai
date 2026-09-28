package com.example.core.database.dao.infra

import androidx.room.*
import com.example.core.database.entity.infra.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InfraDao {
    // Connection Config
    @Query("SELECT * FROM infra_connection_configs WHERE serviceType = :serviceType")
    suspend fun getConfig(serviceType: String): InfraConnectionConfigEntity?

    @Query("SELECT * FROM infra_connection_configs WHERE serviceType = :serviceType")
    fun observeConfig(serviceType: String): Flow<InfraConnectionConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: InfraConnectionConfigEntity)

    // Status Cache
    @Query("SELECT * FROM infra_status_caches WHERE serviceType = :serviceType")
    suspend fun getCache(serviceType: String): InfraStatusCacheEntity?

    @Query("SELECT * FROM infra_status_caches WHERE serviceType = :serviceType")
    fun observeCache(serviceType: String): Flow<InfraStatusCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: InfraStatusCacheEntity)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAuditLogs(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)
}
