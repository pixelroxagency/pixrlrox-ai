package com.example.core.network

data class ChatMessageDto(
    val role: String,
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val max_tokens: Int? = null,
    val temperature: Double? = null
)

data class ConnectionTestResult(
    val isSuccess: Boolean = true,
    val statusCode: Int = 200,
    val statusMessage: String = "Connected successfully",
    val errorBody: String? = null,
    val diagnosticSummary: String? = null,
    val keyFingerprint: String = "none",
    val discoveredModels: List<String> = listOf("gpt-4o-mini", "gemini-2.5-flash"),
    val supportsStreaming: Boolean = true,
    val supportsRuns: Boolean = false
)
