package com.example.core.database.dao.voice

import androidx.room.*
import com.example.core.database.entity.voice.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {

    @Query("SELECT * FROM voice_notes ORDER BY timestamp DESC")
    fun getAllVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getVoiceNoteById(id: String): VoiceNoteEntity?

    @Query("SELECT * FROM voice_notes WHERE title LIKE '%' || :query || '%' OR transcript LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchVoiceNotes(query: String): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceNote: VoiceNoteEntity)

    @Update
    suspend fun update(voiceNote: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
