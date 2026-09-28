package com.example.data.repository

import com.example.core.database.dao.infra.InfraDao
import com.example.core.database.entity.infra.InfraConnectionConfigEntity
import com.example.core.database.entity.infra.InfraStatusCacheEntity
import com.example.core.security.KeystoreSecretManager
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class N8nWorkflow(
    val id: String,
    val name: String,
    val active: Boolean,
    val updatedAt: Long
)

data class N8nExecution(
    val id: String,
    val workflowId: String,
    val workflowName: String = "",
    val status: String, // success, error, running, waiting
    val startTime: Long,
    val durationMs: Long
)

data class N8nStatusData(
    val connectionState: String, // NOT_CONFIGURED, CONNECTED, UNAUTHORIZED, OFFLINE, ERROR
    val workflows: List<N8nWorkflow> = emptyList(),
    val executions: List<N8nExecution> = emptyList(),
    val isCached: Boolean = false,
    val lastUpdated: Long = 0L,
    val errorMessage: String? = null
)

class N8nRepository(
    private val infraDao: InfraDao,
    private val secretManager: KeystoreSecretManager,
    private val auditLogRepository: AuditLogRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun observeConfig(): Flow<InfraConnectionConfigEntity?> = infraDao.observeConfig("n8n")

    suspend fun saveConfig(baseUrl: String, apiKey: String) {
        val cleanUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
        infraDao.insertConfig(
            InfraConnectionConfigEntity(
                serviceType = "n8n",
                baseUrl = cleanUrl,
                isEnabled = true,
                lastCheckTimestamp = System.currentTimeMillis(),
                lastStatus = if (cleanUrl.isBlank()) "NOT_CONFIGURED" else "CONNECTING"
            )
        )
        if (apiKey.isNotBlank()) {
            secretManager.storeApiKey("n8n_api_key", apiKey)
        }
    }

    suspend fun fetchStatus(): N8nStatusData = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig("n8n")
        val apiKey = secretManager.getApiKey("n8n_api_key")

        if (config == null || config.baseUrl.isBlank()) {
            return@withContext N8nStatusData(connectionState = "NOT_CONFIGURED")
        }

        try {
            val requestBuilder = Request.Builder()
                .url("${config.baseUrl}api/v1/workflows")
                .get()

            if (apiKey.isNotBlank()) {
                requestBuilder.header("X-N8N-API-KEY", apiKey)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code == 401 || response.code == 403) {
                updateConfigStatus("UNAUTHORIZED")
                return@withContext N8nStatusData(
                    connectionState = "UNAUTHORIZED",
                    errorMessage = "Invalid API Key or unauthorized access (HTTP ${response.code})"
                )
            }

            if (!response.isSuccessful) {
                updateConfigStatus("ERROR")
                return@withContext loadCachedData("HTTP Error ${response.code}")
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val workflows = mutableListOf<N8nWorkflow>()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                workflows.add(
                    N8nWorkflow(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Unnamed Workflow"),
                        active = obj.optBoolean("active", false),
                        updatedAt = parseTimestamp(obj.optString("updatedAt"))
                    )
                )
            }

            // Fetch executions
            val execWorkflows = fetchExecutions(config.baseUrl, apiKey, workflows)

            val now = System.currentTimeMillis()
            updateConfigStatus("CONNECTED")

            // Save cache
            val cachePayload = JSONObject().apply {
                put("workflows", JSONArray().apply {
                    workflows.forEach { wf ->
                        put(JSONObject().apply {
                            put("id", wf.id)
                            put("name", wf.name)
                            put("active", wf.active)
                            put("updatedAt", wf.updatedAt)
                        })
                    }
                })
                put("executions", JSONArray().apply {
                    execWorkflows.forEach { ex ->
                        put(JSONObject().apply {
                            put("id", ex.id)
                            put("workflowId", ex.workflowId)
                            put("workflowName", ex.workflowName)
                            put("status", ex.status)
                            put("startTime", ex.startTime)
                            put("durationMs", ex.durationMs)
                        })
                    }
                })
                put("timestamp", now)
            }.toString()

            infraDao.insertCache(InfraStatusCacheEntity("n8n_workflows", cachePayload, now))

            N8nStatusData(
                connectionState = "CONNECTED",
                workflows = workflows,
                executions = execWorkflows,
                isCached = false,
                lastUpdated = now
            )
        } catch (e: Exception) {
            updateConfigStatus("OFFLINE")
            loadCachedData(e.localizedMessage ?: "Connection failed")
        }
    }

    private fun fetchExecutions(baseUrl: String, apiKey: String, workflows: List<N8nWorkflow>): List<N8nExecution> {
        return try {
            val requestBuilder = Request.Builder()
                .url("${baseUrl}api/v1/executions?limit=20")
                .get()

            if (apiKey.isNotBlank()) {
                requestBuilder.header("X-N8N-API-KEY", apiKey)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<N8nExecution>()

            val wfNameMap = workflows.associateBy({ it.id }, { it.name })

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val wfId = obj.optString("workflowId")
                list.add(
                    N8nExecution(
                        id = obj.optString("id"),
                        workflowId = wfId,
                        workflowName = wfNameMap[wfId] ?: "Workflow #$wfId",
                        status = if (obj.optBoolean("finished", true)) (if (obj.optBoolean("stoppedAt", false)) "stopped" else "success") else "running",
                        startTime = parseTimestamp(obj.optString("startedAt")),
                        durationMs = obj.optLong("executionTime", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("N8nRepository", "Failed to fetch executions", e)
            emptyList()
        }
    }

    private suspend fun loadCachedData(errMsg: String): N8nStatusData {
        val cache = infraDao.getCache("n8n_workflows") ?: return N8nStatusData(
            connectionState = "OFFLINE",
            errorMessage = errMsg
        )

        return try {
            val json = JSONObject(cache.jsonPayload)
            val wfArray = json.optJSONArray("workflows") ?: JSONArray()
            val execArray = json.optJSONArray("executions") ?: JSONArray()

            val workflows = mutableListOf<N8nWorkflow>()
            for (i in 0 until wfArray.length()) {
                val obj = wfArray.getJSONObject(i)
                workflows.add(
                    N8nWorkflow(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        active = obj.optBoolean("active"),
                        updatedAt = obj.optLong("updatedAt")
                    )
                )
            }

            val executions = mutableListOf<N8nExecution>()
            for (i in 0 until execArray.length()) {
                val obj = execArray.getJSONObject(i)
                executions.add(
                    N8nExecution(
                        id = obj.optString("id"),
                        workflowId = obj.optString("workflowId"),
                        workflowName = obj.optString("workflowName"),
                        status = obj.optString("status"),
                        startTime = obj.optLong("startTime"),
                        durationMs = obj.optLong("durationMs")
                    )
                )
            }

            N8nStatusData(
                connectionState = "OFFLINE",
                workflows = workflows,
                executions = executions,
                isCached = true,
                lastUpdated = cache.timestamp,
                errorMessage = errMsg
            )
        } catch (e: Exception) {
            Log.e("N8nRepository", "Failed to parse cached N8n data", e)
            N8nStatusData(connectionState = "OFFLINE", errorMessage = errMsg)
        }
    }

    suspend fun toggleWorkflowState(workflowId: String, activate: Boolean): Boolean = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig("n8n") ?: return@withContext false
        val apiKey = secretManager.getApiKey("n8n_api_key")
        val endpoint = if (activate) "activate" else "deactivate"

        try {
            val req = Request.Builder()
                .url("${config.baseUrl}api/v1/workflows/$workflowId/$endpoint")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .apply {
                    if (apiKey.isNotBlank()) header("X-N8N-API-KEY", apiKey)
                }
                .build()

            val resp = client.newCall(req).execute()
            val success = resp.isSuccessful
            auditLogRepository.logAction(
                action = if (activate) "Activate Workflow" else "Deactivate Workflow",
                target = "Workflow ID: $workflowId",
                status = if (success) "SUCCESS" else "FAILED",
                details = "HTTP ${resp.code}"
            )
            success
        } catch (e: Exception) {
            auditLogRepository.logAction(
                action = if (activate) "Activate Workflow" else "Deactivate Workflow",
                target = "Workflow ID: $workflowId",
                status = "FAILED",
                details = e.localizedMessage ?: "Network error"
            )
            false
        }
    }

    private suspend fun updateConfigStatus(status: String) {
        val config = infraDao.getConfig("n8n")
        if (config != null) {
            infraDao.insertConfig(config.copy(lastCheckTimestamp = System.currentTimeMillis(), lastStatus = status))
        }
    }

    private fun parseTimestamp(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("N8nRepository", "Failed to parse timestamp: $isoString", e)
            System.currentTimeMillis()
        }
    }
}
