package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.Persona

data class PreparedChatRequest(
    val messages: List<ChatMessage>,
    val contextWindow: ChatContextWindowResult
)

interface ConversationRollingSummaryStore {
    suspend fun getSummary(conversationId: String): String

    suspend fun getSummaryWatermark(conversationId: String): Long

    suspend fun saveSummary(conversationId: String, summary: String)

    suspend fun saveSummaryWatermark(conversationId: String, watermark: Long)
}

class ChatRequestPreparer(
    private val contextWindowManager: ChatContextWindowManager,
    private val rollingSummaryStore: ConversationRollingSummaryStore
) {
    suspend fun prepare(
        conversationId: String,
        sourceMessages: List<ChatMessage>,
        memories: List<Memory>,
        persona: Persona?,
        contextConfig: ChatContextWindowConfig,
        toolResults: List<String> = emptyList(),
        appliedActionLines: List<String> = emptyList(),
        temporaryResponseFormat: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): PreparedChatRequest {
        val contextWindow = contextWindowManager.build(
            messages = sourceMessages,
            existingSummary = rollingSummaryStore.getSummary(conversationId),
            summaryWatermark = rollingSummaryStore.getSummaryWatermark(conversationId),
            config = contextConfig
        )
        if (contextWindow.summaryUpdated) {
            rollingSummaryStore.saveSummary(conversationId, contextWindow.summary)
            rollingSummaryStore.saveSummaryWatermark(conversationId, contextWindow.summaryWatermark)
        }

        val systemPrompt = ChatPromptBuilder.build(
            memories = memories,
            persona = persona,
            toolResults = toolResults,
            appliedActionLines = appliedActionLines,
            temporaryResponseFormat = temporaryResponseFormat,
            rollingSummary = contextWindow.summary,
            nowMillis = nowMillis
        )
        val requestMessages = contextWindow.messages
            .map { ChatMessage(role = it.role, content = it.content) }
            .toMutableList()
        if (systemPrompt.isNotBlank()) {
            requestMessages.add(0, ChatMessage(role = "system", content = systemPrompt))
        }
        return PreparedChatRequest(requestMessages, contextWindow)
    }
}
