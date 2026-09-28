#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/media/MediaDao.kt
package com.example.core.database.dao.media
import androidx.room.*
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import kotlinx.coroutines.flow.Flow
@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY lastPlayed DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItemEntity)
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistMediaCrossRef(crossRef: PlaylistMediaCrossRef)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/notes/NoteDao.kt
package com.example.core.database.dao.notes
import androidx.room.*
import com.example.core.database.entity.notes.NoteEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, timestamp DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    @Delete
    suspend fun deleteNote(note: NoteEntity)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/checklist/ChecklistDao.kt
package com.example.core.database.dao.checklist
import androidx.room.*
import com.example.core.database.entity.checklist.ChecklistEntity
import com.example.core.database.entity.checklist.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY timestamp DESC")
    fun getAllChecklists(): Flow<List<ChecklistEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity)
    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY itemOrder ASC")
    fun getChecklistItems(checklistId: String): Flow<List<ChecklistItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItemEntity)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/calendar/EventDao.kt
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
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/finance/ExpenseDao.kt
package com.example.core.database.dao.finance
import androidx.room.*
import com.example.core.database.entity.finance.ExpenseTransactionEntity
import com.example.core.database.entity.finance.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<ExpenseTransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: ExpenseTransactionEntity)
    @Query("SELECT * FROM expense_categories")
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ExpenseCategoryEntity)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/core/database/dao/vault/VaultDao.kt
package com.example.core.database.dao.vault
import androidx.room.*
import com.example.core.database.entity.vault.VaultEntryEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY name ASC")
    fun getAllEntries(): Flow<List<VaultEntryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntryEntity)
}
INNER_EOF
