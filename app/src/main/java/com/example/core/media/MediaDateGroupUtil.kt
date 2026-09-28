package com.example.core.media

import com.example.core.database.entity.media.MediaItemEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MediaDateGroupUtil {

    /**
     * Groups media items by date label in chronological order (newest first).
     */
    fun groupByDate(
        items: List<MediaItemEntity>,
        referenceNowMillis: Long = System.currentTimeMillis()
    ): Map<String, List<MediaItemEntity>> {
        val sorted = items.sortedByDescending { it.lastPlayed }
        val grouped = LinkedHashMap<String, MutableList<MediaItemEntity>>()

        for (item in sorted) {
            val label = formatDateGroupLabel(item.lastPlayed, referenceNowMillis)
            grouped.getOrPut(label) { mutableListOf() }.add(item)
        }

        return grouped
    }

    /**
     * Formats a timestamp into a human-readable date section header:
     * - "Today"
     * - "Yesterday"
     * - Day of week (e.g. "Monday") if within last 2..6 days
     * - Month and Day (e.g. "September 18") if within same year
     * - Month Day, Year (e.g. "October 12, 2025") if older year
     */
    fun formatDateGroupLabel(
        timestampMillis: Long,
        referenceNowMillis: Long = System.currentTimeMillis()
    ): String {
        if (timestampMillis <= 0L) return "Earlier"

        val nowCal = Calendar.getInstance().apply { timeInMillis = referenceNowMillis }
        val itemCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val nowYear = nowCal.get(Calendar.YEAR)
        val itemYear = itemCal.get(Calendar.YEAR)
        val nowDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)
        val itemDayOfYear = itemCal.get(Calendar.DAY_OF_YEAR)

        if (nowYear == itemYear) {
            val dayDiff = nowDayOfYear - itemDayOfYear
            return when {
                dayDiff == 0 -> "Today"
                dayDiff == 1 -> "Yesterday"
                dayDiff in 2..6 -> {
                    SimpleDateFormat("EEEE", Locale.US).format(Date(timestampMillis))
                }
                dayDiff < 0 -> {
                    SimpleDateFormat("MMMM d", Locale.US).format(Date(timestampMillis))
                }
                else -> {
                    SimpleDateFormat("MMMM d", Locale.US).format(Date(timestampMillis))
                }
            }
        } else {
            // Check cross-year yesterday edge case (e.g. Dec 31 to Jan 1)
            val calPrev = Calendar.getInstance().apply {
                timeInMillis = referenceNowMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }
            if (calPrev.get(Calendar.YEAR) == itemYear && calPrev.get(Calendar.DAY_OF_YEAR) == itemDayOfYear) {
                return "Yesterday"
            }
            return SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(timestampMillis))
        }
    }

    /**
     * Formats media duration in milliseconds to mm:ss or hh:mm:ss.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
