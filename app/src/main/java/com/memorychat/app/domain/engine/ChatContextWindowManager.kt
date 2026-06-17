package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.provider.LlmProvider
import com.memorychat.app.util.AppLogger

data class ChatContextWindowConfig(
    val contextWindowTokens: Int,
    val maxCompletionTokens: Int,
    val safetyMarginTokens: Int,
    val compressionMessageTurnThreshold: Int,
    val recentMessageCount: Int = 12,
    val forceCompression: Boolean = false
)

data class ChatContextWindowResult(
    val messages: List<ChatMessage>,
    val summary: String,
    val summaryWatermark: Long,
    val summaryUpdated: Boolean
)

interface ConversationContextSummarizer {
    suspend fun summarize(existingSummary: String, messages: List<ChatMessage>): String
}

class ChatContextWindowManager(
    private val summarizer: ConversationContextSummarizer
) {
    suspend fun build(
        messages: List<ChatMessage>,
        existingSummary: String,
        summaryWatermark: Long,
        config: ChatContextWindowConfig
    ): ChatContextWindowResult {
        val sortedMessages = messages.sortedBy { it.createdAt }
        val recentCount = config.recentMessageCount.coerceAtLeast(2)
        val availablePromptTokens = (config.contextWindowTokens - config.maxCompletionTokens - config.safetyMarginTokens)
            .coerceAtLeast(1_000)
        var summary = existingSummary.trim()
        var watermark = if (summary.isBlank()) 0L else summaryWatermark
        var summaryUpdated = false

        val visibleWithoutNewCompression = if (summary.isNotBlank()) {
            sortedMessages.filter { it.createdAt > watermark }
        } else {
            sortedMessages
        }
        val overTurnThreshold = visibleWithoutNewCompression.size > config.compressionMessageTurnThreshold
        val overTokenBudget = estimateTokens(summary, visibleWithoutNewCompression) > availablePromptTokens
        if (config.forceCompression || overTurnThreshold || overTokenBudget) {
            val candidates = visibleWithoutNewCompression.dropLast(recentCount)
            if (candidates.isNotEmpty()) {
                val newSummary = summarizer.summarize(summary, candidates).trim()
                if (newSummary.isNotBlank()) {
                    summary = newSummary
                    watermark = candidates.maxOf { it.createdAt }
                    summaryUpdated = true
                    AppLogger.i(
                        "ChatContext",
                        "Compressed context: messages=${candidates.size}, watermark=$watermark"
                    )
                }
            }
        }

        val visibleMessages = if (summary.isNotBlank()) {
            sortedMessages.filter { it.createdAt > watermark }
        } else {
            sortedMessages
        }
        val trimmed = trimToBudget(visibleMessages, summary, availablePromptTokens)
        return ChatContextWindowResult(
            messages = trimmed,
            summary = summary,
            summaryWatermark = watermark,
            summaryUpdated = summaryUpdated
        )
    }

    private fun trimToBudget(
        messages: List<ChatMessage>,
        summary: String,
        availablePromptTokens: Int
    ): List<ChatMessage> {
        var visible = messages
        while (visible.size > 1 && estimateTokens(summary, visible) > availablePromptTokens) {
            visible = visible.drop(1)
        }
        return visible
    }

    private fun estimateTokens(summary: String, messages: List<ChatMessage>): Int {
        val chars = summary.length + messages.sumOf { it.content.length }
        return (chars / 4) + messages.size * 4
    }
}

class LlmConversationContextSummarizer(
    private val provider: LlmProvider,
    private val modelName: String
) : ConversationContextSummarizer {
    override suspend fun summarize(existingSummary: String, messages: List<ChatMessage>): String {
        val fallback = buildExtractiveSummary(existingSummary, messages)
        return try {
            val prompt = buildPrompt(existingSummary, messages)
            provider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = prompt)),
                    model = modelName,
                    stream = false
                )
            ).content.trim().ifBlank { fallback }
        } catch (e: Exception) {
            AppLogger.w("ChatContext", "LLM summary failed: ${e.javaClass.simpleName}: ${e.message}")
            fallback
        }
    }

    private fun buildPrompt(existingSummary: String, messages: List<ChatMessage>): String {
        val conversation = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val prior = existingSummary.ifBlank { "(none)" }
        return """
            Summarize the conversation context for future chat turns.

            Keep stable facts, user goals, decisions, unresolved tasks, and context needed to continue the conversation.
            Do not add assistant persona settings as user memory.
            Be concise but specific. Return plain text only.

            Existing rolling summary:
            $prior

            New messages to merge:
            $conversation
        """.trimIndent()
    }
}

private fun buildExtractiveSummary(existingSummary: String, messages: List<ChatMessage>): String {
    val lines = messages
        .takeLast(20)
        .map { "${it.role}: ${it.content.take(240)}" }
    return buildString {
        if (existingSummary.isNotBlank()) {
            appendLine(existingSummary.trim())
            appendLine()
        }
        appendLine("Recent compressed conversation:")
        lines.forEach { appendLine("- $it") }
    }.trim()
}
