package com.example.data.repository

import com.example.core.ai.AutoAiRouter
import com.example.core.ai.RouteTarget
import com.example.core.database.dao.ConversationDao
import com.example.core.database.dao.MessageDao
import com.example.core.database.entity.ConversationEntity
import com.example.core.database.entity.MessageEntity
import com.example.core.network.ChatCompletionRequest
import com.example.core.network.ChatMessageDto
import com.example.core.network.DirectAiApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val directAiApiClient: DirectAiApiClient,
    val directAiRepository: DirectAiRepository
) {

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    @Volatile
    private var currentGenerationJob: Job? = null

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    fun getConversation(id: String): Flow<ConversationEntity?> =
        conversationDao.getConversation(id)

    suspend fun getConversationSync(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
        conversationDao.getConversationSync(id)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(
        title: String = "New Conversation",
        profileId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val activeProfile = profileId ?: "default"
        val convId = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = convId,
            title = title,
            profileId = activeProfile,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastMessagePreview = ""
        )
        conversationDao.insert(conversation)
        convId
    }

    suspend fun renameConversation(id: String, title: String) = withContext(Dispatchers.IO) {
        conversationDao.rename(id, title.trim())
    }

    suspend fun pinConversation(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.setPinned(id, isPinned)
    }

    suspend fun updateConversationProfile(id: String, profileId: String) = withContext(Dispatchers.IO) {
        conversationDao.updateProfile(id, profileId)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        stopActiveGeneration()
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteById(id)
    }

    fun stopActiveGeneration() {
        directAiApiClient.cancelActiveCall()
        currentGenerationJob?.cancel()
        _isGenerating.value = false
    }

    suspend fun sendMessage(
        conversationId: String,
        userPrompt: String,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty"))

        if (_isGenerating.value) {
            return@withContext Result.failure(IllegalStateException("Generation already in progress"))
        }

        // Auto-generate title from first prompt if title is default
        val conv = conversationDao.getConversationSync(conversationId)
        if (conv != null && (conv.title == "New Conversation" || conv.title.isBlank())) {
            val previewTitle = if (trimmedPrompt.length > 35) trimmedPrompt.take(35) + "..." else trimmedPrompt
            conversationDao.rename(conversationId, previewTitle)
        }

        // 1. Insert User Message
        val userMsgId = UUID.randomUUID().toString()
        val userMsg = MessageEntity(
            id = userMsgId,
            conversationId = conversationId,
            role = "user",
            content = trimmedPrompt,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insert(userMsg)
        conversationDao.updateLastMessage(conversationId, trimmedPrompt)

        return@withContext executeAssistantResponse(conversationId, modelOverride)
    }

    suspend fun editAndResendMessage(
        conversationId: String,
        messageId: String,
        newContent: String,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty"))

        stopActiveGeneration()

        val targetMsg = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

        // Delete all messages strictly after this message timestamp to maintain coherence
        messageDao.deleteMessagesAfter(conversationId, targetMsg.timestamp)

        // Update target message with new content
        val updatedUserMsg = targetMsg.copy(
            content = trimmed,
            timestamp = System.currentTimeMillis()
        )
        messageDao.update(updatedUserMsg)
        conversationDao.updateLastMessage(conversationId, trimmed)

        return@withContext executeAssistantResponse(conversationId, modelOverride)
    }

    suspend fun regenerateAssistantResponse(
        conversationId: String,
        messageId: String,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (_isGenerating.value) {
            return@withContext Result.failure(IllegalStateException("Generation already in progress"))
        }

        val targetMsg = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

        // Delete this assistant message and any subsequent messages
        messageDao.deleteMessagesAfter(conversationId, targetMsg.timestamp - 1)

        val history = messageDao.getMessagesForConversation(conversationId).firstOrNull() ?: emptyList()
        val lastUserMsg = history.lastOrNull { it.role == "user" }
            ?: return@withContext Result.failure(IllegalStateException("No preceding user message to answer"))

        return@withContext executeAssistantResponse(conversationId, modelOverride)
    }

    private suspend fun executeAssistantResponse(
        conversationId: String,
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        _isGenerating.value = true

        val currentProvider = directAiRepository.currentProvider.value
        val currentMode = directAiRepository.aiMode.value

        // Prepare assistant placeholder in Room
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = MessageEntity(
            id = assistantMsgId,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        messageDao.insert(assistantMsg)

        // Build chat context strictly belonging to this conversation
        val previousHistory = messageDao.getMessagesForConversation(conversationId).firstOrNull() ?: emptyList()
        val dtoList = previousHistory
            .filter { it.content.isNotBlank() && !it.isError && it.id != assistantMsgId }
            .map { ChatMessageDto(role = it.role, content = it.content) }

        val activeModel = modelOverride
            ?: currentProvider?.selectedModel
            ?: "gpt-4o-mini"

        val request = ChatCompletionRequest(
            model = activeModel,
            messages = dtoList,
            stream = true
        )

        val fullTextBuilder = StringBuilder()
        return@withContext try {
            val selectedRoute: RouteTarget = RouteTarget.DIRECT_AI

            val isDirectAiRoute = selectedRoute == RouteTarget.DIRECT_AI
            if (isDirectAiRoute) {
                if (!directAiRepository.isCurrentProviderConfigured()) {
                    val configError = "Direct AI mode is active but not configured. Please enter your API key in Settings -> Direct AI."
                    messageDao.updateContent(
                        id = assistantMsgId,
                        content = configError,
                        isStreaming = false,
                        isError = true
                    )
                    conversationDao.updateLastMessage(conversationId, configError)
                    _isGenerating.value = false
                    return@withContext Result.failure(IllegalStateException(configError))
                }

                val chatStream: Flow<String> = directAiRepository.streamDirectAiChat(request)
                coroutineScope {
                    currentGenerationJob = coroutineContext[Job]
                    chatStream.collect { chunk ->
                        fullTextBuilder.append(chunk)
                        messageDao.updateContent(
                            id = assistantMsgId,
                            content = fullTextBuilder.toString(),
                            isStreaming = true,
                            isError = false
                        )
                    }
                }

                val finalContent = fullTextBuilder.toString()
                val baseCleaned = finalContent.ifEmpty { "Empty response received." }
                val cleanedFinal = if (currentMode == AiMode.AUTO) "$baseCleaned\n\n_Auto · Direct AI_" else baseCleaned
                messageDao.updateContent(
                    id = assistantMsgId,
                    content = cleanedFinal,
                    isStreaming = false,
                    isError = false
                )
                conversationDao.updateLastMessage(conversationId, cleanedFinal)
                _isGenerating.value = false
                Result.success(cleanedFinal)
            } else {
                val configError = "Direct AI is not configured. Please enter your API key in Settings -> Direct AI."
                messageDao.updateContent(
                    id = assistantMsgId,
                    content = configError,
                    isStreaming = false,
                    isError = true
                )
                conversationDao.updateLastMessage(conversationId, configError)
                _isGenerating.value = false
                Result.failure(IllegalStateException(configError))
            }
        } catch (e: CancellationException) {
            val partialContent = fullTextBuilder.toString()
            val preserved = if (partialContent.isBlank()) "Generation stopped." else partialContent
            messageDao.updateContent(
                id = assistantMsgId,
                content = preserved,
                isStreaming = false,
                isError = false
            )
            conversationDao.updateLastMessage(conversationId, preserved)
            _isGenerating.value = false
            Result.failure(e)
        } catch (e: Exception) {
            val errorText = "Error communicating with AI service: ${e.localizedMessage ?: "Unknown error"}"
            messageDao.updateContent(
                id = assistantMsgId,
                content = errorText,
                isStreaming = false,
                isError = true
            )
            conversationDao.updateLastMessage(conversationId, errorText)
            _isGenerating.value = false
            Result.failure(e)
        } finally {
            currentGenerationJob = null
            _isGenerating.value = false
        }
    }

    suspend fun retryMessage(conversationId: String, messageId: String): Result<String> = withContext(Dispatchers.IO) {
        val msg = messageDao.getMessageById(messageId) ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))
        if (msg.role == "assistant") {
            messageDao.deleteById(messageId)
            val history = messageDao.getMessagesForConversation(conversationId).firstOrNull() ?: emptyList()
            val lastUserMsg = history.lastOrNull { it.role == "user" }
            if (lastUserMsg != null) {
                return@withContext executeAssistantResponse(conversationId)
            }
        }
        Result.failure(IllegalStateException("Cannot retry"))
    }
}
