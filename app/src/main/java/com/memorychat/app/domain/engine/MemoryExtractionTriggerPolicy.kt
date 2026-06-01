package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage

enum class MemoryExtractionTrigger {
    EXPLICIT_MEMORY,
    TURN_BATCH,
    CONVERSATION_EXIT,
    MANUAL
}

class MemoryExtractionTriggerPolicy(
    private val batchTurnThreshold: Int = 20
) {
    fun afterAssistantTurn(
        unextractedMessages: List<ChatMessage>,
        latestUserMessage: ChatMessage
    ): MemoryExtractionTrigger? {
        if (ExplicitMemorySignal.extractContent(latestUserMessage.content) != null) {
            return MemoryExtractionTrigger.EXPLICIT_MEMORY
        }

        val userCount = unextractedMessages.count { it.role == "user" }
        val assistantCount = unextractedMessages.count { it.role == "assistant" }
        val completedTurns = minOf(userCount, assistantCount)
        return if (completedTurns >= batchTurnThreshold) {
            MemoryExtractionTrigger.TURN_BATCH
        } else {
            null
        }
    }

    fun onConversationExit(unextractedMessages: List<ChatMessage>): MemoryExtractionTrigger? {
        return if (unextractedMessages.any { it.role == "user" }) {
            MemoryExtractionTrigger.CONVERSATION_EXIT
        } else {
            null
        }
    }
}

object ExplicitMemorySignal {
    private val patterns = listOf(
        Regex("(?i)please\\s+remember(?:\\s+that)?[:：\\s]+(.+)"),
        Regex("(?i)remember(?:\\s+that|\\s+this)?[:：\\s]+(.+)"),
        Regex("请?帮?我?记住[:：\\s]*(.+)"),
        Regex("记一下[:：\\s]*(.+)"),
        Regex("以后记得[:：\\s]*(.+)"),
        Regex("把(.+?)(?:记下来|加入记忆|加到记忆|写入记忆|存到记忆)")
    )

    fun extractContent(content: String): String? {
        val trimmed = content.trim()
        val raw = patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(trimmed)?.groupValues?.getOrNull(1)
        } ?: return null

        return raw
            .trim()
            .trim('。', '.', '，', ',', '：', ':', '"', '\'', '“', '”')
            .takeIf { it.isNotBlank() }
    }
}
