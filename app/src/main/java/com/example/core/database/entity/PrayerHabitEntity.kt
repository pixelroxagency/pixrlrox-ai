package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_habits")
data class PrayerHabitEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val fajr: Boolean = false,
    val dhuhr: Boolean = false,
    val asr: Boolean = false,
    val maghrib: Boolean = false,
    val isha: Boolean = false,
    val notes: String = "",
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val completedCount: Int
        get() = (if (fajr) 1 else 0) +
                (if (dhuhr) 1 else 0) +
                (if (asr) 1 else 0) +
                (if (maghrib) 1 else 0) +
                (if (isha) 1 else 0)

    val isAllCompleted: Boolean
        get() = completedCount == 5
}
