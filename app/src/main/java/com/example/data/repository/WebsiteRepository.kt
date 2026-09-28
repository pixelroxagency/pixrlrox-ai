package com.example.data.repository

import com.example.core.database.dao.website.WebsiteDao
import com.example.core.database.entity.website.WebsiteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class WebsiteRepository(
    private val websiteDao: WebsiteDao,
    private val alertRepository: AlertRepository
) {
    fun getAllWebsites(): Flow<List<WebsiteEntity>> = websiteDao.getAllWebsites()

    fun getWebsitesForClient(clientId: String): Flow<List<WebsiteEntity>> =
        websiteDao.getWebsitesForClient(clientId)

    suspend fun getWebsiteById(id: String): WebsiteEntity? = withContext(Dispatchers.IO) {
        websiteDao.getWebsiteById(id)
    }

    suspend fun addWebsite(
        name: String,
        domain: String,
        url: String,
        environment: String = "Production",
        notes: String = "",
        isMonitoringEnabled: Boolean = true,
        clientId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url

        val entity = WebsiteEntity(
            id = id,
            name = name,
            domain = domain.ifBlank { URL(normalizedUrl).host ?: name },
            url = normalizedUrl,
            environment = environment,
            notes = notes,
            isMonitoringEnabled = isMonitoringEnabled,
            clientId = clientId,
            createdAt = System.currentTimeMillis()
        )
        websiteDao.insert(entity)
        id
    }

    suspend fun updateWebsite(website: WebsiteEntity) = withContext(Dispatchers.IO) {
        websiteDao.update(website)
    }

    suspend fun deleteWebsite(id: String) = withContext(Dispatchers.IO) {
        websiteDao.deleteById(id)
    }

    suspend fun checkWebsiteStatus(website: WebsiteEntity): WebsiteEntity = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var isOnline = false
        var statusCode = 0
        var responseTime = 0L
        var sslValid = true
        var sslExpiryDate = 0L
        var sslIssuer = ""

        try {
            val urlObj = URL(website.url)
            val connection = urlObj.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = true

            if (connection is HttpsURLConnection) {
                try {
                    connection.connect()
                    val certs = connection.serverCertificates
                    if (certs.isNotEmpty() && certs[0] is java.security.cert.X509Certificate) {
                        val x509 = certs[0] as java.security.cert.X509Certificate
                        sslExpiryDate = x509.notAfter.time
                        sslIssuer = x509.issuerDN.name
                        val now = System.currentTimeMillis()
                        sslValid = now < sslExpiryDate
                    }
                } catch (e: Exception) {
                    sslValid = false
                }
            }

            statusCode = connection.responseCode
            responseTime = System.currentTimeMillis() - startTime
            isOnline = statusCode in 200..399
            connection.disconnect()
        } catch (e: Exception) {
            isOnline = false
            statusCode = 0
            responseTime = System.currentTimeMillis() - startTime
        }

        val updated = website.copy(
            lastCheckTime = System.currentTimeMillis(),
            isOnline = isOnline,
            httpStatus = statusCode,
            responseTimeMs = responseTime,
            sslValid = sslValid,
            sslExpiryDate = sslExpiryDate,
            sslIssuer = sslIssuer
        )

        websiteDao.update(updated)

        // Generate Alert if Website is DOWN or SSL Expiring
        if (!isOnline) {
            alertRepository.addNormalizedAlert(
                source = "WEBSITES",
                title = "Website Offline: ${website.name}",
                message = "${website.url} returned HTTP $statusCode.",
                severity = "High",
                deduplicationKey = "web_offline_${website.id}",
                actionPayload = "website_manager"
            )
        } else if (sslExpiryDate > 0) {
            val daysToExpiry = (sslExpiryDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
            if (daysToExpiry in 0..14) {
                alertRepository.addNormalizedAlert(
                    source = "WEBSITES",
                    title = "SSL Expiring Soon: ${website.name}",
                    message = "SSL certificate for ${website.domain} expires in $daysToExpiry days.",
                    severity = "Medium",
                    deduplicationKey = "web_ssl_expiring_${website.id}",
                    actionPayload = "website_manager"
                )
            }
        }

        return@withContext updated
    }
}
