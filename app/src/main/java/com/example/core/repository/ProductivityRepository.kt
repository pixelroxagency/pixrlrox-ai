package com.example.core.repository

import com.example.core.database.dao.productivity.ClipboardDao
import com.example.core.database.dao.productivity.SecureNoteDao
import com.example.core.database.dao.productivity.HabitDao
import com.example.core.database.entity.ClipboardEntryEntity
import com.example.core.database.entity.SecureNoteEntity
import com.example.core.database.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProductivityRepository @Inject constructor(
    private val clipboardDao: ClipboardDao,
    private val secureNoteDao: SecureNoteDao,
    private val habitDao: HabitDao
) {
    // Clipboard
    val clipboardHistory: Flow<List<ClipboardEntryEntity>> = clipboardDao.getAllClipboardEntries()
    suspend fun insertClipboard(entry: ClipboardEntryEntity) = clipboardDao.insertClipboardEntry(entry)
    suspend fun deleteClipboard(entry: ClipboardEntryEntity) = clipboardDao.deleteClipboardEntry(entry)
    suspend fun clearClipboard() = clipboardDao.clearClipboardHistory()

    // Secure Notes
    val secureNotes: Flow<List<SecureNoteEntity>> = secureNoteDao.getAllSecureNotes()
    suspend fun insertSecureNote(note: SecureNoteEntity) = secureNoteDao.insertSecureNote(note)
    suspend fun deleteSecureNote(note: SecureNoteEntity) = secureNoteDao.deleteSecureNote(note)

    // Habit
    val habits: Flow<List<HabitEntity>> = habitDao.getAllHabits()
    suspend fun insertHabit(habit: HabitEntity) = habitDao.insertHabit(habit)
    suspend fun deleteHabit(habit: HabitEntity) = habitDao.deleteHabit(habit)
    suspend fun updateHabit(habit: HabitEntity) = habitDao.updateHabit(habit)
}
