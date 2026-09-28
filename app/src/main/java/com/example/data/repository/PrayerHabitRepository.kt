package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.core.database.dao.PrayerHabitDao
import com.example.core.database.entity.PrayerHabitEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HabitSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedTime: String? = null,
    val error: String? = null
)

class PrayerHabitRepository(
    private val context: Context,
    private val habitDao: PrayerHabitDao,
    private val scope: CoroutineScope
) {

    private val databaseId = "ai-studio-pixelroxai-66887a5b-0d13-4114-aecf-136c71c359f2"

    private val firestore: FirebaseFirestore? by lazy {
        com.example.core.firebase.FirebaseInitializer.getFirestore(context, databaseId)
    }

    private val _syncStatus = MutableStateFlow(HabitSyncStatus())
    val syncStatus: StateFlow<HabitSyncStatus> = _syncStatus.asStateFlow()

    fun getTodayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun getHabitForDateFlow(dateKey: String): Flow<PrayerHabitEntity> {
        return habitDao.getHabitForDateFlow(dateKey).map {
            it ?: PrayerHabitEntity(date = dateKey)
        }
    }

    fun getAllHabitsFlow(): Flow<List<PrayerHabitEntity>> {
        return habitDao.getAllHabitsFlow()
    }

    fun getStreakFlow(): Flow<Int> {
        return habitDao.getAllHabitsFlow().map { list ->
            calculateStreak(list)
        }
    }

    fun togglePrayer(dateKey: String, prayerName: String) {
        scope.launch(Dispatchers.IO) {
            val existing = habitDao.getHabitForDate(dateKey) ?: PrayerHabitEntity(date = dateKey)
            val updated = when (prayerName.lowercase(Locale.US)) {
                "fajr" -> existing.copy(fajr = !existing.fajr, updatedAt = System.currentTimeMillis())
                "dhuhr" -> existing.copy(dhuhr = !existing.dhuhr, updatedAt = System.currentTimeMillis())
                "asr" -> existing.copy(asr = !existing.asr, updatedAt = System.currentTimeMillis())
                "maghrib" -> existing.copy(maghrib = !existing.maghrib, updatedAt = System.currentTimeMillis())
                "isha" -> existing.copy(isha = !existing.isha, updatedAt = System.currentTimeMillis())
                else -> existing
            }

            habitDao.insertOrUpdate(updated)
            pushToCloud(updated)
        }
    }

    suspend fun pushToCloud(habit: PrayerHabitEntity) {
        val db = firestore ?: return
        val collectionPath = "prayer_habits"

        _syncStatus.update { it.copy(isSyncing = true, error = null) }

        try {
            val map = hashMapOf<String, Any>(
                "date" to habit.date,
                "fajr" to habit.fajr,
                "dhuhr" to habit.dhuhr,
                "asr" to habit.asr,
                "maghrib" to habit.maghrib,
                "isha" to habit.isha,
                "notes" to habit.notes,
                "completedCount" to habit.completedCount,
                "updatedAt" to habit.updatedAt
            )

            db.collection(collectionPath)
                .document(habit.date)
                .set(map, SetOptions.merge())
                .await()

            habitDao.markSynced(habit.date)

            val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedTime = timeFormatted,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e("PrayerHabitRepo", "Failed to push habit to Firestore", e)
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    error = "Cloud sync error: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }
    }

    suspend fun pullFromCloud() {
        val db = firestore ?: return
        val collectionPath = "prayer_habits"

        _syncStatus.update { it.copy(isSyncing = true, error = null) }

        try {
            val snapshot = db.collection(collectionPath)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val date = doc.getString("date") ?: doc.id
                val fajr = doc.getBoolean("fajr") ?: false
                val dhuhr = doc.getBoolean("dhuhr") ?: false
                val asr = doc.getBoolean("asr") ?: false
                val maghrib = doc.getBoolean("maghrib") ?: false
                val isha = doc.getBoolean("isha") ?: false
                val notes = doc.getString("notes") ?: ""
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                val entity = PrayerHabitEntity(
                    date = date,
                    fajr = fajr,
                    dhuhr = dhuhr,
                    asr = asr,
                    maghrib = maghrib,
                    isha = isha,
                    notes = notes,
                    isSynced = true,
                    updatedAt = updatedAt
                )
                habitDao.insertOrUpdate(entity)
            }

            val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedTime = timeFormatted,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e("PrayerHabitRepo", "Failed to pull habits from Firestore", e)
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    error = "Cloud pull error: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }
    }

    private fun calculateStreak(list: List<PrayerHabitEntity>): Int {
        if (list.isEmpty()) return 0
        val mapByDate = list.associateBy { it.date }
        var streak = 0
        val cal = Calendar.getInstance()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Start checking from today, if today completed >= 1 prayer or all prayers, count today. Else check yesterday.
        val todayKey = sdf.format(cal.time)
        val todayHabit = mapByDate[todayKey]

        if (todayHabit != null && todayHabit.completedCount > 0) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Check yesterday
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateKey = sdf.format(cal.time)
            val habit = mapByDate[dateKey]
            if (habit != null && habit.completedCount > 0) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }
}
