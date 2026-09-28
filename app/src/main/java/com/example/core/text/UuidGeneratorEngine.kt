package com.example.core.text

import java.util.UUID

data class UuidConfig(
    val uppercase: Boolean = false,
    val includeHyphens: Boolean = true,
    val includeBraces: Boolean = false
)

object UuidGeneratorEngine {

    fun generateSingle(config: UuidConfig = UuidConfig()): String {
        var uuid = UUID.randomUUID().toString()
        if (!config.includeHyphens) {
            uuid = uuid.replace("-", "")
        }
        if (config.uppercase) {
            uuid = uuid.uppercase()
        }
        if (config.includeBraces) {
            uuid = "{$uuid}"
        }
        return uuid
    }

    fun generateBatch(count: Int, config: UuidConfig = UuidConfig()): List<String> {
        val safeCount = count.coerceIn(1, 200)
        return (1..safeCount).map { generateSingle(config) }
    }
}
