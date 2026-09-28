package com.example.core.text

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TimestampUnit(val displayName: String) {
    SECONDS("Seconds (10 digits)"),
    MILLISECONDS("Milliseconds (13 digits)")
}

data class TimestampConvertResult(
    val unixSeconds: Long,
    val unixMilliseconds: Long,
    val iso8601Utc: String,
    val formattedLocal: String,
    val formattedUtc: String,
    val relativeTime: String
)

object TimestampConverterEngine {

    fun fromEpoch(value: Long, unit: TimestampUnit): TimestampConvertResult {
        val millis = if (unit == TimestampUnit.SECONDS) value * 1000L else value
        val seconds = millis / 1000L
        val date = Date(millis)

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val localFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (z)", Locale.getDefault())
        val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val relative = formatRelativeTime(millis)

        return TimestampConvertResult(
            unixSeconds = seconds,
            unixMilliseconds = millis,
            iso8601Utc = isoFormat.format(date),
            formattedLocal = localFormat.format(date),
            formattedUtc = utcFormat.format(date),
            relativeTime = relative
        )
    }

    fun fromDateString(dateStr: String, isUtc: Boolean = false): TimestampConvertResult {
        val trimmed = dateStr.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy"
        )

        var parsedDate: Date? = null
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (isUtc) timeZone = TimeZone.getTimeZone("UTC")
                }
                parsedDate = sdf.parse(trimmed)
                if (parsedDate != null) break
            } catch (_: Exception) {}
        }

        if (parsedDate == null) {
            throw IllegalArgumentException("Could not parse date string '$dateStr'. Try format 'YYYY-MM-DD HH:MM:SS'")
        }

        return fromEpoch(parsedDate.time, TimestampUnit.MILLISECONDS)
    }

    fun getCurrentTimestamp(): TimestampConvertResult {
        return fromEpoch(System.currentTimeMillis(), TimestampUnit.MILLISECONDS)
    }

    private fun formatRelativeTime(millis: Long): String {
        val diffMs = System.currentTimeMillis() - millis
        val absSec = Math.abs(diffMs) / 1000
        val isPast = diffMs >= 0

        val suffix = if (isPast) "ago" else "from now"
        return when {
            absSec < 5 -> "just now"
            absSec < 60 -> "$absSec seconds $suffix"
            absSec < 3600 -> "${absSec / 60} minutes $suffix"
            absSec < 86400 -> "${absSec / 3600} hours $suffix"
            else -> "${absSec / 86400} days $suffix"
        }
    }
}
