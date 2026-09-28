#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/MediaRepository.kt
package com.example.data.repository
import com.example.core.database.dao.media.MediaDao
import com.example.core.database.entity.media.MediaItemEntity
import com.example.core.database.entity.media.PlaylistEntity
import com.example.core.database.entity.media.PlaylistMediaCrossRef
import kotlinx.coroutines.flow.Flow
class MediaRepository(private val mediaDao: MediaDao) {
    fun getAllMedia(): Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()
    suspend fun insertMedia(media: MediaItemEntity) = mediaDao.insertMedia(media)
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylists()
    suspend fun insertPlaylist(playlist: PlaylistEntity) = mediaDao.insertPlaylist(playlist)
    suspend fun insertPlaylistMediaCrossRef(crossRef: PlaylistMediaCrossRef) = mediaDao.insertPlaylistMediaCrossRef(crossRef)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/NoteRepository.kt
package com.example.data.repository
import com.example.core.database.dao.notes.NoteDao
import com.example.core.database.entity.notes.NoteEntity
import kotlinx.coroutines.flow.Flow
class NoteRepository(private val noteDao: NoteDao) {
    fun getActiveNotes(): Flow<List<NoteEntity>> = noteDao.getActiveNotes()
    suspend fun insertNote(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/ChecklistRepository.kt
package com.example.data.repository
import com.example.core.database.dao.checklist.ChecklistDao
import com.example.core.database.entity.checklist.ChecklistEntity
import com.example.core.database.entity.checklist.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow
class ChecklistRepository(private val checklistDao: ChecklistDao) {
    fun getAllChecklists(): Flow<List<ChecklistEntity>> = checklistDao.getAllChecklists()
    suspend fun insertChecklist(checklist: ChecklistEntity) = checklistDao.insertChecklist(checklist)
    fun getChecklistItems(checklistId: String): Flow<List<ChecklistItemEntity>> = checklistDao.getChecklistItems(checklistId)
    suspend fun insertChecklistItem(item: ChecklistItemEntity) = checklistDao.insertChecklistItem(item)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/EventRepository.kt
package com.example.data.repository
import com.example.core.database.dao.calendar.EventDao
import com.example.core.database.entity.calendar.EventEntity
import kotlinx.coroutines.flow.Flow
class EventRepository(private val eventDao: EventDao) {
    fun getAllEvents(): Flow<List<EventEntity>> = eventDao.getAllEvents()
    suspend fun insertEvent(event: EventEntity) = eventDao.insertEvent(event)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/ExpenseRepository.kt
package com.example.data.repository
import com.example.core.database.dao.finance.ExpenseDao
import com.example.core.database.entity.finance.ExpenseTransactionEntity
import com.example.core.database.entity.finance.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow
class ExpenseRepository(private val expenseDao: ExpenseDao) {
    fun getAllTransactions(): Flow<List<ExpenseTransactionEntity>> = expenseDao.getAllTransactions()
    suspend fun insertTransaction(transaction: ExpenseTransactionEntity) = expenseDao.insertTransaction(transaction)
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>> = expenseDao.getAllCategories()
    suspend fun insertCategory(category: ExpenseCategoryEntity) = expenseDao.insertCategory(category)
}
INNER_EOF

cat << 'INNER_EOF' > app/src/main/java/com/example/data/repository/VaultRepository.kt
package com.example.data.repository
import com.example.core.database.dao.vault.VaultDao
import com.example.core.database.entity.vault.VaultEntryEntity
import kotlinx.coroutines.flow.Flow
import com.example.core.security.KeystoreSecretManager
class VaultRepository(private val vaultDao: VaultDao, private val secretManager: KeystoreSecretManager) {
    fun getAllEntries(): Flow<List<VaultEntryEntity>> = vaultDao.getAllEntries()
    suspend fun insertEntry(entry: VaultEntryEntity) = vaultDao.insertEntry(entry)
}
INNER_EOF
