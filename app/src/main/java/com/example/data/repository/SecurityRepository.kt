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

data class SecurityFinding(
    val id: String,
    val checkName: String,
    val severity: String, // "Critical", "High", "Medium", "Low", "Info"
    val evidence: String,
    val impact: String,
    val recommendation: String
)

data class SecurityStatusData(
    val connectionState: String = "NOT_CONFIGURED", // "CONNECTED", "OFFLINE", "UNAUTHORIZED", "NOT_CONFIGURED"
    val isCached: Boolean = false,
    val lastUpdated: Long = 0L,
    val securityScore: Int = 100,
    val firewallUfwActive: Boolean = true,
    val fail2banActive: Boolean = true,
    val sshRootLoginDisabled: Boolean = true,
    val sshPasswordAuthDisabled: Boolean = true,
    val dockerSocketProtected: Boolean = true,
    val sslCertificatesValid: Boolean = true,
    val expiringCertsCount: Int = 0,
    val openPortsCount: Int = 0,
    val findings: List<SecurityFinding> = emptyList()
)

class SecurityRepository(
    private val infraDao: InfraDao,
    private val secretManager: KeystoreSecretManager,
    private val alertRepository: AlertRepository
) {
    private val serviceKey = "security_endpoint"
    private val apiKeySecretAlias = "infra_security_api_key"

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

    suspend fun fetchSecurityStatus(): SecurityStatusData = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig(serviceKey)
        val apiKey = secretManager.getApiKey(apiKeySecretAlias)

        if (config == null || config.baseUrl.isBlank()) {
            val cached = loadCachedSecurity()
            return@withContext cached ?: SecurityStatusData("NOT_CONFIGURED")
        }

        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val requestBuilder = okhttp3.Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/api/security")
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                infraDao.insertCache(
                    InfraStatusCacheEntity(
                        serviceType = "security_status",
                        jsonPayload = bodyStr,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val parsed = parseSecurityJson(bodyStr, isCached = false, lastUpdated = System.currentTimeMillis(), connectionState = "CONNECTED")
                checkAndGenerateAlerts(parsed)
                return@withContext parsed
            } else if (response.code == 401 || response.code == 403) {
                val cached = loadCachedSecurity()
                return@withContext cached?.copy(connectionState = "UNAUTHORIZED") ?: SecurityStatusData("UNAUTHORIZED")
            } else {
                val cached = loadCachedSecurity()
                return@withContext cached?.copy(connectionState = "ERROR") ?: SecurityStatusData("ERROR")
            }
        } catch (_: Exception) {
            val cached = loadCachedSecurity()
            return@withContext cached ?: SecurityStatusData("OFFLINE")
        }
    }

    suspend fun refreshSslCertificates(): Boolean = withContext(Dispatchers.IO) {
        val config = infraDao.getConfig(serviceKey)
        val apiKey = secretManager.getApiKey(apiKeySecretAlias)
        if (config == null || config.baseUrl.isBlank()) return@withContext false

        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/api/security/refresh-ssl")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            infraDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    action = "REFRESH_SSL_CERTS",
                    target = config.baseUrl,
                    timestamp = System.currentTimeMillis(),
                    status = if (success) "SUCCESS" else "FAILED",
                    details = "HTTP ${response.code}"
                )
            )
            return@withContext success
        } catch (e: Exception) {
            return@withContext false
        }
    }

    private suspend fun checkAndGenerateAlerts(data: SecurityStatusData) {
        data.findings.filter { it.severity == "Critical" || it.severity == "High" }.forEach { f ->
            alertRepository.addNormalizedAlert(
                source = "SECURITY",
                title = "Security Issue: ${f.checkName}",
                message = "${f.evidence}. Impact: ${f.impact}",
                severity = f.severity,
                deduplicationKey = "sec_finding_${f.id}",
                actionPayload = "security_center"
            )
        }
    }

    private suspend fun loadCachedSecurity(): SecurityStatusData? {
        val cache = infraDao.getCache("security_status") ?: return null
        return parseSecurityJson(
            json = cache.jsonPayload,
            isCached = true,
            lastUpdated = cache.timestamp,
            connectionState = "OFFLINE"
        )
    }

    fun parseSecurityJson(
        json: String,
        isCached: Boolean,
        lastUpdated: Long,
        connectionState: String
    ): SecurityStatusData {
        var ufw = true
        var fail2ban = true
        var sshRoot = false
        var sshPass = false
        var dockerProt = true
        var sslValid = true
        var expiringCerts = 0
        var openPorts = 0
        val findings = mutableListOf<SecurityFinding>()

        try {
            val root = JSONObject(json)
            ufw = root.optBoolean("firewallUfwActive", root.optBoolean("ufw", true))
            fail2ban = root.optBoolean("fail2banActive", root.optBoolean("fail2ban", true))
            sshRoot = root.optBoolean("sshRootLoginDisabled", true)
            sshPass = root.optBoolean("sshPasswordAuthDisabled", true)
            dockerProt = root.optBoolean("dockerSocketProtected", true)
            sslValid = root.optBoolean("sslCertificatesValid", true)
            expiringCerts = root.optInt("expiringCertsCount", 0)
            openPorts = root.optInt("openPortsCount", 0)

            val arr = root.optJSONArray("findings") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                findings.add(
                    SecurityFinding(
                        id = obj.optString("id", "$i"),
                        checkName = obj.optString("checkName", "Check $i"),
                        severity = obj.optString("severity", "Medium"),
                        evidence = obj.optString("evidence", ""),
                        impact = obj.optString("impact", ""),
                        recommendation = obj.optString("recommendation", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SecurityRepository", "Failed to parse security JSON", e)
        }

        // Centralized Transparent Rule-Based Security Score Calculation
        val score = calculateSecurityScore(
            ufw = ufw,
            fail2ban = fail2ban,
            sshRootDisabled = sshRoot,
            sshPassDisabled = sshPass,
            dockerProtected = dockerProt,
            sslValid = sslValid,
            expiringCerts = expiringCerts,
            findings = findings
        )

        return SecurityStatusData(
            connectionState = connectionState,
            isCached = isCached,
            lastUpdated = lastUpdated,
            securityScore = score,
            firewallUfwActive = ufw,
            fail2banActive = fail2ban,
            sshRootLoginDisabled = sshRoot,
            sshPasswordAuthDisabled = sshPass,
            dockerSocketProtected = dockerProt,
            sslCertificatesValid = sslValid,
            expiringCertsCount = expiringCerts,
            openPortsCount = openPorts,
            findings = findings
        )
    }

    fun calculateSecurityScore(
        ufw: Boolean,
        fail2ban: Boolean,
        sshRootDisabled: Boolean,
        sshPassDisabled: Boolean,
        dockerProtected: Boolean,
        sslValid: Boolean,
        expiringCerts: Int,
        findings: List<SecurityFinding>
    ): Int {
        var baseScore = 100

        if (!ufw) baseScore -= 20
        if (!fail2ban) baseScore -= 15
        if (!sshRootDisabled) baseScore -= 15
        if (!sshPassDisabled) baseScore -= 10
        if (!dockerProtected) baseScore -= 25
        if (!sslValid) baseScore -= 15
        if (expiringCerts > 0) baseScore -= (expiringCerts * 5)

        findings.forEach { f ->
            when (f.severity) {
                "Critical" -> baseScore -= 25
                "High" -> baseScore -= 15
                "Medium" -> baseScore -= 10
                "Low" -> baseScore -= 5
            }
        }

        return baseScore.coerceIn(0, 100)
    }
}
