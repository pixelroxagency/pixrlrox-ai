package com.example.ui.screens.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.voice.VoiceNoteEntity
import com.example.core.voice.VoiceManager
import com.example.data.repository.VoiceNoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ProposedTaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isSelected: Boolean = true
)

data class VoiceNotesUiState(
    val voiceNotes: List<VoiceNoteEntity> = emptyList(),
    val searchQuery: String = "",
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDurationSeconds: Long = 0,
    val currentTranscript: String = "",
    val selectedVoiceNote: VoiceNoteEntity? = null,
    val isProcessingAi: Boolean = false,
    val aiResultText: String? = null,
    val proposedTasks: List<ProposedTaskItem> = emptyList(),
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class VoiceNotesViewModel(
    private val repository: VoiceNoteRepository,
    private val voiceManager: VoiceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceNotesUiState())
    val uiState: StateFlow<VoiceNotesUiState> = _uiState.asStateFlow()

    private var durationJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null

    init {
        viewModelScope.launch {
            _uiState.flatMapLatest { state ->
                if (state.searchQuery.isBlank()) {
                    repository.getAllVoiceNotes()
                } else {
                    repository.searchVoiceNotes(state.searchQuery)
                }
            }.collect { list ->
                _uiState.update { it.copy(voiceNotes = list) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun startRecording(context: Context) {
        try {
            val audioDir = File(context.filesDir, "voice_notes")
            if (!audioDir.exists()) audioDir.mkdirs()
            val file = File(audioDir, "vn_${System.currentTimeMillis()}.m4a")
            currentAudioFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            _uiState.update {
                it.copy(
                    isRecording = true,
                    isPaused = false,
                    recordingDurationSeconds = 0,
                    currentTranscript = "",
                    errorMessage = null
                )
            }

            startTimer()

            // Also launch speech recognizer for live transcription stream
            voiceManager.startListeningForComposer(
                onPartial = { partial -> _uiState.update { it.copy(currentTranscript = partial) } },
                onComplete = { text -> _uiState.update { it.copy(currentTranscript = text) } },
                onError = { /* live STT optional fallback */ }
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to start recording: ${e.localizedMessage}") }
        }
    }

    private fun startTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                durationJob?.cancel()
                _uiState.update { it.copy(isPaused = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Pause not supported: ${e.message}") }
            }
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                startTimer()
                _uiState.update { it.copy(isPaused = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Resume failed: ${e.message}") }
            }
        }
    }

    fun stopRecordingAndSave(title: String) {
        durationJob?.cancel()
        voiceManager.stopListening()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (_: Exception) {}

        val audioPath = currentAudioFile?.absolutePath ?: ""
        val duration = _uiState.value.recordingDurationSeconds
        val transcript = _uiState.value.currentTranscript

        viewModelScope.launch {
            repository.saveVoiceNote(
                title = title.ifBlank { "Voice Note" },
                durationSeconds = duration,
                audioPath = audioPath,
                transcript = transcript
            )
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    recordingDurationSeconds = 0,
                    currentTranscript = "",
                    statusMessage = "Voice note saved locally."
                )
            }
        }
    }

    fun cancelRecording() {
        durationJob?.cancel()
        voiceManager.stopListening()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (_: Exception) {}
        currentAudioFile?.delete()
        currentAudioFile = null
        _uiState.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                recordingDurationSeconds = 0,
                currentTranscript = ""
            )
        }
    }

    fun selectVoiceNote(note: VoiceNoteEntity?) {
        _uiState.update {
            it.copy(
                selectedVoiceNote = note,
                aiResultText = null,
                proposedTasks = emptyList(),
                errorMessage = null
            )
        }
    }

    fun runAiActionOnTranscript(note: VoiceNoteEntity, action: String, customPrompt: String = "") {
        if (note.transcript.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Transcript is empty. Please add or generate a transcript first.") }
            return
        }

        _uiState.update { it.copy(isProcessingAi = true, errorMessage = null, aiResultText = null, proposedTasks = emptyList()) }

        viewModelScope.launch {
            val result = repository.processTranscriptWithAi(
                transcript = note.transcript,
                action = action,
                customInstruction = customPrompt
            )

            if (result.isSuccess) {
                val output = result.getOrThrow()
                if (action == "Extract Tasks") {
                    val lines = output.lines()
                        .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("☐ ").trim() }
                        .filter { it.isNotBlank() && !it.startsWith("AI found", ignoreCase = true) }
                    val tasks = lines.map { ProposedTaskItem(text = it, isSelected = true) }
                    _uiState.update { it.copy(isProcessingAi = false, aiResultText = output, proposedTasks = tasks) }
                } else {
                    _uiState.update { it.copy(isProcessingAi = false, aiResultText = output) }
                }
            } else {
                _uiState.update { it.copy(isProcessingAi = false, errorMessage = result.exceptionOrNull()?.message ?: "AI action failed") }
            }
        }
    }

    fun toggleTaskSelection(taskId: String) {
        _uiState.update { state ->
            val updated = state.proposedTasks.map {
                if (it.id == taskId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(proposedTasks = updated)
        }
    }

    fun addSelectedTasksToPersonalTasks() {
        val selected = _uiState.value.proposedTasks.filter { it.isSelected }.map { it.text }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            repository.createTasksFromProposed(selected)
            _uiState.update {
                it.copy(
                    proposedTasks = emptyList(),
                    statusMessage = "Added ${selected.size} task(s) to Personal Tasks!"
                )
            }
        }
    }

    fun deleteVoiceNote(note: VoiceNoteEntity) {
        viewModelScope.launch {
            if (note.audioPath.isNotBlank()) {
                File(note.audioPath).delete()
            }
            repository.deleteVoiceNote(note.id)
            if (_uiState.value.selectedVoiceNote?.id == note.id) {
                _uiState.update { it.copy(selectedVoiceNote = null) }
            }
        }
    }

    fun renameVoiceNote(note: VoiceNoteEntity, newTitle: String) {
        viewModelScope.launch {
            val updated = note.copy(title = newTitle.trim())
            repository.updateVoiceNote(updated)
            if (_uiState.value.selectedVoiceNote?.id == note.id) {
                _uiState.update { it.copy(selectedVoiceNote = updated) }
            }
        }
    }

    fun updateTranscript(note: VoiceNoteEntity, newTranscript: String) {
        viewModelScope.launch {
            val updated = note.copy(transcript = newTranscript, isTranscribed = newTranscript.isNotBlank())
            repository.updateVoiceNote(updated)
            if (_uiState.value.selectedVoiceNote?.id == note.id) {
                _uiState.update { it.copy(selectedVoiceNote = updated) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
    }
}
