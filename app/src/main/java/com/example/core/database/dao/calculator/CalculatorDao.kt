package com.example.core.database.dao.calculator

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.calculator.CalcHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: CalcHistoryEntity)

    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CalcHistoryEntity>>

    @Query("DELETE FROM calc_history")
    suspend fun clearHistory()
}
