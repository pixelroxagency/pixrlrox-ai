package com.example.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.notes.NoteEntity
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getActiveNotes().collect {
                _notes.value = it
            }
        }
    }

    fun saveNote(id: String?, title: String, content: String, isPinned: Boolean) {
        val noteId = id ?: UUID.randomUUID().toString()
        val note = NoteEntity(
            id = noteId,
            title = title,
            content = content,
            timestamp = System.currentTimeMillis(),
            pinned = isPinned,
            archived = false
        )
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
