package com.example.data.repository
import com.example.core.database.dao.notes.NoteDao
import com.example.core.database.entity.notes.NoteEntity
import kotlinx.coroutines.flow.Flow
open class NoteRepository(private val noteDao: NoteDao) {
    open fun getActiveNotes(): Flow<List<NoteEntity>> = noteDao.getActiveNotes()
    open suspend fun insertNote(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun saveNote(title: String, content: String) {
        val note = NoteEntity(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            content = content,
            timestamp = System.currentTimeMillis(),
            pinned = false,
            archived = false
        )
        noteDao.insertNote(note)
    }

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
}
