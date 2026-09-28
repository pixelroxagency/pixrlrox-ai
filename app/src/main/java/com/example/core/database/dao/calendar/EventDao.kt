package com.example.core.database.dao.calendar
import androidx.room.*
import com.example.core.database.entity.calendar.EventEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp ASC")
    fun getAllEvents(): Flow<List<EventEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)
}
