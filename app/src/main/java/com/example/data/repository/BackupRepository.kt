package com.example.data.repository

import com.example.core.database.dao.infra.InfraDao
import com.example.core.database.entity.infra.AuditLogEntity
import com.example.core.database.entity.infra.InfraStatusCacheEntity
import com.example.core.security.KeystoreSecretManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BackupSnapshot(
    val id: String,
    val timestamp: Long,
    val status: String, // "SUCCESS", "FAILED", "IN_PROGRESS"
    val sizeBytes: Long,
    val durationSeconds: Long
)

data class BackupStatusData(
    val connectionState: String = "NOT_CONFIGURED", // "CONNECTED", "OFFLINE", "UNAUTHORIZED", "NOT_CONFIGURED"
    val isCached: Boolean = false,
    val lastUpdated: Long = 0L,
    val health: String = "UNKNOWN", // "HEALTHY", "WARNING", "FAILED", "UNKNOWN"
    val healthReason: String = "No data available",
    val repositoryReachable: Boolean = false,
    val repositoryName: String = "",
    val latestSnapshotTime: Long = 0L,
    val latestSuccessfulBackupTime: Long = 0L,
    val snapshotCount: Int = 0,
    val backupAgeHours: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val lastError: String = "",
    val nextScheduledTime: Long = 0L,
    val snapshots: List<BackupSnapshot> = emptyList()
)

class BackupRepository(
    private val infraDao: InfraDao,
    private val secretManager: KeystoreSecretManager,
    private val alertRepository: AlertRepository
) {
    private val serviceKey = "backup_endpoint"
    private val apiKeySecretAlias = "infra_backup_api_key"

    suspend fun saveConfig(baseUrl: String, apiKey: String) = withContext(Dispatchers.IO) {
        if (apiKey.isNotBlank()) {
            secretManager.storeApiKey(apiKeySecretAlias, apiKey)
        }
        infraDao.insertConfig(
            com.example.core.database.entity.infra.InfraConnectionConfigEntity(
                serviceType = serviceKey,
                baseUrl = baseUrl,
                lastCheckTimestamp = System.currentTimeMillis(),
                lastStatus = if (baseUrl.isNotBlank()) "CONFIGURED" else "NOT_CONFIGURED"
            )
        )
    }

    suspend fun fetchBackupStatus(): BackupStatusData = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig(serviceKey)
        val apiKey = secretManager.getApiKey(apiKeySecretAlias)

        if (config == null || config.baseUrl.isBlank()) {
            val cached = loadCachedBackup()
            return@withContext cached ?: BackupStatusData("NOT_CONFIGURED")
        }

        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val requestBuilder = okhttp3.Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/api/backups")
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                infraDao.insertCache(
                    InfraStatusCacheEntity(
                        serviceType = "backup_status",
                        jsonPayload = bodyStr,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val parsed = parseBackupJson(bodyStr, isCached = false, lastUpdated = System.currentTimeMillis(), connectionState = "CONNECTED")
                checkAndGenerateAlerts(parsed)
                return@withContext parsed
            } else if (response.code == 401 || response.code == 403) {
                val cached = loadCachedBackup()
                return@withContext cached?.copy(connectionState = "UNAUTHORIZED") ?: BackupStatusData("UNAUTHORIZED")
            } else {
                val cached = loadCachedBackup()
                return@withContext cached?.copy(connectionState = "ERROR") ?: BackupStatusData("ERROR")
            }
        } catch (e: Exception) {
            Log.e("BackupRepository", "Failed to fetch backup status", e)
            val cached = loadCachedBackup()
            return@withContext cached ?: BackupStatusData("OFFLINE")
        }
    }

    suspend fun triggerBackupRun(): Boolean = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig(serviceKey)
        val apiKey = secretManager.getApiKey(apiKeySecretAlias)
        if (config == null || config.baseUrl.isBlank()) return@withContext false

        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/api/backups/run")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            infraDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    action = "TRIGGER_BACKUP_RUN",
                    target = config.baseUrl,
                    timestamp = System.currentTimeMillis(),
                    status = if (success) "SUCCESS" else "FAILED",
                    details = "Backup execution HTTP ${response.code}"
                )
            )
            return@withContext success
        } catch (e: Exception) {
            infraDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    action = "TRIGGER_BACKUP_RUN",
                    target = config.baseUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "FAILED",
                    details = e.localizedMessage ?: "Network failure"
                )
            )
            return@withContext false
        }
    }

    private suspend fun checkAndGenerateAlerts(data: BackupStatusData) {
        if (data.health == "FAILED") {
            alertRepository.addNormalizedAlert(
                source = "BACKUP",
                title = "Backup Health Failed",
                message = data.healthReason,
                severity = "Critical",
                deduplicationKey = "backup_health_failed",
                actionPayload = "backup_dashboard"
            )
        } else if (data.health == "WARNING") {
            alertRepository.addNormalizedAlert(
                source = "BACKUP",
                title = "Backup Warning",
                message = data.healthReason,
                severity = "High",
                deduplicationKey = "backup_health_warning",
                actionPayload = "backup_dashboard"
            )
        }
    }

    private suspend fun loadCachedBackup(): BackupStatusData? {
        val cache = infraDao.getCache("backup_status") ?: return null
        return parseBackupJson(
            json = cache.jsonPayload,
            isCached = true,
            lastUpdated = cache.timestamp,
            connectionState = "OFFLINE"
        )
    }

    fun parseBackupJson(
        json: String,
        isCached: Boolean,
        lastUpdated: Long,
        connectionState: String
    ): BackupStatusData {
        var repoReachable = false
        var repoName = ""
        var latestSnapshotTime = 0L
        var latestSuccessTime = 0L
        var snapshotCount = 0
        var storageUsedBytes = 0L
        var lastError = ""
        var nextScheduledTime = 0L
        val snapshots = mutableListOf<BackupSnapshot>()

        try {
            val root = JSONObject(json)
            repoReachable = root.optBoolean("repositoryReachable", root.optBoolean("reachable", true))
            repoName = root.optString("repositoryName", root.optString("repository", "Default Repo"))
            latestSnapshotTime = root.optLong("latestSnapshotTime", root.optLong("latest_snapshot", System.currentTimeMillis()))
            latestSuccessTime = root.optLong("latestSuccessfulBackupTime", latestSnapshotTime)
            snapshotCount = root.optInt("snapshotCount", root.optInt("count", 0))
            storageUsedBytes = root.optLong("storageUsedBytes", root.optLong("size_bytes", 0L))
            lastError = root.optString("lastError", root.optString("error", ""))
            nextScheduledTime = root.optLong("nextScheduledTime", 0L)

            val arr = root.optJSONArray("snapshots") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                snapshots.add(
                    BackupSnapshot(
                        id = obj.optString("id", "$i"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "SUCCESS"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        durationSeconds = obj.optLong("durationSeconds", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BackupRepository", "Failed to parse backup JSON", e)
        }

        val now = System.currentTimeMillis()
        val ageMs = if (latestSuccessTime > 0) (now - latestSuccessTime).coerceAtLeast(0L) else 0L
        val backupAgeHours = ageMs / (1000 * 60 * 60)

        val (health, healthReason) = calculateBackupHealth(
            repoReachable = repoReachable,
            lastError = lastError,
            backupAgeHours = backupAgeHours,
            latestSnapshotTime = latestSnapshotTime
        )

        return BackupStatusData(
            connectionState = connectionState,
            isCached = isCached,
            lastUpdated = lastUpdated,
            health = health,
            healthReason = healthReason,
            repositoryReachable = repoReachable,
            repositoryName = repoName,
            latestSnapshotTime = latestSnapshotTime,
            latestSuccessfulBackupTime = latestSuccessTime,
            snapshotCount = if (snapshots.isNotEmpty()) snapshots.size else snapshotCount,
            backupAgeHours = backupAgeHours,
            storageUsedBytes = storageUsedBytes,
            lastError = lastError,
            nextScheduledTime = nextScheduledTime,
            snapshots = snapshots
        )
    }

    fun calculateBackupHealth(
        repoReachable: Boolean,
        lastError: String,
        backupAgeHours: Long,
        latestSnapshotTime: Long
    ): Pair<String, String> {
        if (!repoReachable) {
            return Pair("FAILED", "Backup repository is unreachable or offline.")
        }
        if (lastError.isNotBlank()) {
            return Pair("WARNING", "Last backup run reported an error: $lastError")
        }
        if (latestSnapshotTime == 0L) {
            return Pair("WARNING", "No backup snapshots have been recorded yet.")
        }
        if (backupAgeHours > 48) {
            return Pair("WARNING", "Latest backup snapshot is older than 48 hours ($backupAgeHours hrs old).")
        }
        return Pair("HEALTHY", "Repository reachable and backup snapshots are up to date.")
    }
}
