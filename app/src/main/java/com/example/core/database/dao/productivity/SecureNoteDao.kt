package com.example.core.database.dao.productivity

import androidx.room.*
import com.example.core.database.entity.SecureNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureNoteDao {
    @Query("SELECT * FROM secure_notes ORDER BY updatedTimestamp DESC")
    fun getAllSecureNotes(): Flow<List<SecureNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecureNote(note: SecureNoteEntity)

    @Delete
    suspend fun deleteSecureNote(note: SecureNoteEntity)
}
