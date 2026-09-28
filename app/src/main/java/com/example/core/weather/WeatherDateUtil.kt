package com.example.core.weather

import com.example.data.repository.DailyForecast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object WeatherDateUtil {

    private fun getIsoDateFormat(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    fun getDayOfWeekAbbreviation(dateStr: String): String {
        return try {
            val date = getIsoDateFormat().parse(dateStr)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                SimpleDateFormat("EEE", Locale.US).format(cal.time).uppercase(Locale.US)
            } else {
                "---"
            }
        } catch (_: Exception) {
            "---"
        }
    }

    fun formatDisplayDate(dateStr: String = "", currentTimeMillis: Long = System.currentTimeMillis()): String {
        return try {
            val date = if (dateStr.isNotBlank()) getIsoDateFormat().parse(dateStr) else null
            if (date != null) {
                SimpleDateFormat("EEEE, d MMM", Locale.US).format(date)
            } else {
                SimpleDateFormat("EEEE, d MMM", Locale.US).format(Calendar.getInstance().apply { timeInMillis = currentTimeMillis }.time)
            }
        } catch (_: Exception) {
            SimpleDateFormat("EEEE, d MMM", Locale.US).format(Calendar.getInstance().apply { timeInMillis = currentTimeMillis }.time)
        }
    }

    /**
     * Filters out Today's daily forecast entry and returns the next [count] future days (e.g. 6 days)
     * in chronological order.
     * Correctly handles month boundaries, year boundaries, leap days, and timezone differences.
     */
    fun filterFutureDays(
        allDaily: List<DailyForecast>,
        todayDateStr: String,
        count: Int = 6
    ): List<DailyForecast> {
        if (allDaily.isEmpty()) return emptyList()

        val format = getIsoDateFormat()
        val filtered = if (todayDateStr.isNotBlank()) {
            val todayDate = try {
                format.parse(todayDateStr)
            } catch (_: Exception) {
                null
            }
            if (todayDate != null) {
                allDaily.filter { item ->
                    try {
                        val itemDate = format.parse(item.date)
                        itemDate != null && itemDate.after(todayDate)
                    } catch (_: Exception) {
                        item.date > todayDateStr
                    }
                }
            } else {
                allDaily.filter { it.date > todayDateStr }
            }
        } else {
            // If todayDateStr is not provided, drop the first entry (assuming index 0 is today)
            allDaily.drop(1)
        }

        // Sort chronologically
        val sorted = filtered.sortedWith { a, b ->
            try {
                val dateA = format.parse(a.date)
                val dateB = format.parse(b.date)
                if (dateA != null && dateB != null) dateA.compareTo(dateB) else a.date.compareTo(b.date)
            } catch (_: Exception) {
                a.date.compareTo(b.date)
            }
        }

        return sorted.take(count)
    }
}
