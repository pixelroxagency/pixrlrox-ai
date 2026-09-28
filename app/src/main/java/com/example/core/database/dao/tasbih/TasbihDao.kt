package com.example.core.database.dao.tasbih

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entity.tasbih.TasbihSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TasbihSessionEntity)

    @Update
    suspend fun updateSession(session: TasbihSessionEntity)

    @Query("SELECT * FROM tasbih_sessions ORDER BY lastUpdated DESC")
    fun getAllSessions(): Flow<List<TasbihSessionEntity>>

    @Query("SELECT * FROM tasbih_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): TasbihSessionEntity?

    @Query("DELETE FROM tasbih_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
