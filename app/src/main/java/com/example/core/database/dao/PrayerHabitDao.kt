package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.PrayerHabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerHabitDao {

    @Query("SELECT * FROM prayer_habits WHERE date = :date")
    fun getHabitForDateFlow(date: String): Flow<PrayerHabitEntity?>

    @Query("SELECT * FROM prayer_habits WHERE date = :date")
    suspend fun getHabitForDate(date: String): PrayerHabitEntity?

    @Query("SELECT * FROM prayer_habits ORDER BY date DESC")
    fun getAllHabitsFlow(): Flow<List<PrayerHabitEntity>>

    @Query("SELECT * FROM prayer_habits ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentHabits(limit: Int = 30): List<PrayerHabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(habit: PrayerHabitEntity)

    @Query("UPDATE prayer_habits SET isSynced = 1 WHERE date = :date")
    suspend fun markSynced(date: String)
}
