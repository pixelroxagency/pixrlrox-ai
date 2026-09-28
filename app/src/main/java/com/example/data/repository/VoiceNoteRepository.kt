package com.example.data.repository

import com.example.core.database.dao.voice.VoiceNoteDao
import com.example.core.database.entity.voice.VoiceNoteEntity
import com.example.core.network.ChatCompletionRequest
import com.example.core.network.ChatMessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class VoiceNoteRepository(
    private val voiceNoteDao: VoiceNoteDao,
    private val directAiRepository: DirectAiRepository,
    private val personalTaskRepository: PersonalTaskRepository
) {

    fun getAllVoiceNotes(): Flow<List<VoiceNoteEntity>> = voiceNoteDao.getAllVoiceNotes()

    fun searchVoiceNotes(query: String): Flow<List<VoiceNoteEntity>> = voiceNoteDao.searchVoiceNotes(query)

    suspend fun saveVoiceNote(
        title: String,
        durationSeconds: Long,
        audioPath: String,
        transcript: String
    ): VoiceNoteEntity = withContext(Dispatchers.IO) {
        val entity = VoiceNoteEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Voice Note ${System.currentTimeMillis() / 1000}" },
            timestamp = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            audioPath = audioPath,
            transcript = transcript,
            isTranscribed = transcript.isNotBlank()
        )
        voiceNoteDao.insert(entity)
        entity
    }

    suspend fun updateVoiceNote(voiceNote: VoiceNoteEntity) = withContext(Dispatchers.IO) {
        voiceNoteDao.update(voiceNote)
    }

    suspend fun deleteVoiceNote(id: String) = withContext(Dispatchers.IO) {
        voiceNoteDao.deleteById(id)
    }

    suspend fun processTranscriptWithAi(
        transcript: String,
        action: String,
        customInstruction: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Transcript is empty"))
        }

        if (!directAiRepository.isCurrentProviderConfigured()) {
            return@withContext Result.failure(IllegalStateException("Direct AI is not configured. Please add an API key in Settings."))
        }
        val provider = directAiRepository.currentProvider.value
        val actionPrompt = when (action) {
            "Summarize" -> "Summarize this voice note transcript concisely:"
            "Extract Tasks" -> "Extract actionable tasks from this voice note transcript. Output each task on a new line starting with '- ':"
            "Clean Up Notes" -> "Clean up this spoken transcript into polished, well-formatted notes with clear headings:"
            "Key Points" -> "Extract key points from this transcript as bullet points:"
            "Custom AI Instruction" -> customInstruction.ifBlank { "Process this transcript:" }
            else -> "Process this voice note transcript:"
        }

        val fullPrompt = "$actionPrompt\n\nTranscript:\n$transcript"

        try {
            val request = ChatCompletionRequest(
                model = provider?.selectedModel ?: "gpt-4o-mini",
                messages = listOf(ChatMessageDto(role = "user", content = fullPrompt)),
                stream = true
            )
            val sb = StringBuilder()
            directAiRepository.streamDirectAiChat(request).collect { chunk ->
                sb.append(chunk)
            }
            Result.success(sb.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTasksFromProposed(tasks: List<String>) = withContext(Dispatchers.IO) {
        tasks.forEach { taskText ->
            val cleanTitle = taskText.trim().removePrefix("- ").removePrefix("* ").trim()
            if (cleanTitle.isNotBlank()) {
                personalTaskRepository.saveTask(
                    title = cleanTitle,
                    description = "Created from voice note analysis",
                    dueDateEpoch = System.currentTimeMillis() + 86400000L,
                    dueTimeMinutes = 540,
                    hasReminder = false
                )
            }
        }
    }
}
