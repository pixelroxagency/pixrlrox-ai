package com.example.core.database.dao.productivity

import androidx.room.*
import com.example.core.database.entity.ClipboardEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    fun getAllClipboardEntries(): Flow<List<ClipboardEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboardEntry(entry: ClipboardEntryEntity)

    @Delete
    suspend fun deleteClipboardEntry(entry: ClipboardEntryEntity)

    @Query("DELETE FROM clipboard_entries")
    suspend fun clearClipboardHistory()
}
