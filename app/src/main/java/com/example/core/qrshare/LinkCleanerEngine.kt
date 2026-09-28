package com.example.core.qrshare

import java.net.URI
import java.net.URLDecoder

data class LinkCleanResult(
    val originalUrl: String,
    val cleanedUrl: String,
    val removedParameters: List<String>,
    val errorMessage: String? = null
)

object LinkCleanerEngine {
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term",
        "utm_content", "utm_id", "gclid", "fbclid", "mc_cid", "mc_eid"
    )

    fun cleanUrl(
        rawUrl: String,
        removeTracking: Boolean = true,
        removeFragment: Boolean = false
    ): LinkCleanResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return LinkCleanResult(rawUrl, "", emptyList(), "URL cannot be empty")
        }

        val normalized = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(normalized)
            val query = uri.query
            val removedParams = mutableListOf<String>()
            val keptParams = mutableMapOf<String, MutableList<String>>()

            if (!query.isNullOrBlank()) {
                val pairs = query.split("&")
                for (pair in pairs) {
                    val idx = pair.indexOf('=')
                    val key = if (idx > 0) pair.substring(0, idx) else pair
                    val value = if (idx > 0 && idx < pair.length - 1) pair.substring(idx + 1) else ""
                    val decodedKey = try { URLDecoder.decode(key, "UTF-8") } catch (_: Exception) { key }

                    if (removeTracking && TRACKING_PARAMS.contains(decodedKey.lowercase())) {
                        removedParams.add("$decodedKey=$value")
                    } else {
                        keptParams.getOrPut(key) { mutableListOf() }.add(value)
                    }
                }
            }

            val newQuery = if (keptParams.isNotEmpty()) {
                keptParams.entries.joinToString("&") { (k, vals) ->
                    if (vals.size == 1 && vals[0].isEmpty()) k
                    else vals.joinToString("&") { v -> if (v.isEmpty()) k else "$k=$v" }
                }
            } else {
                null
            }

            val newFragment = if (removeFragment) null else uri.fragment

            val reconstructed = URI(
                uri.scheme,
                uri.authority,
                uri.path,
                newQuery,
                newFragment
            ).toString()

            LinkCleanResult(
                originalUrl = rawUrl,
                cleanedUrl = reconstructed,
                removedParameters = removedParams
            )
        } catch (e: Exception) {
            LinkCleanResult(
                originalUrl = rawUrl,
                cleanedUrl = rawUrl,
                emptyList(),
                "Invalid URL format: ${e.localizedMessage}"
            )
        }
    }
}
