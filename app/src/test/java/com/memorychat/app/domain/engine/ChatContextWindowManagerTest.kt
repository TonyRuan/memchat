package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextWindowManagerTest {
    @Test
    fun summarizesOlderMessagesWhenTurnThresholdIsExceeded() = runTest {
        val summarizer = FakeSummarizer("旧消息摘要")
        val manager = ChatContextWindowManager(summarizer)
        val messages = (1L..8L).map { index ->
            ChatMessage(
                id = "msg-$index",
                conversationId = "conv-1",
                role = if (index % 2L == 0L) "assistant" else "user",
                content = "message-$index",
                createdAt = index
            )
        }

        val result = manager.build(
            messages = messages,
            existingSummary = "",
            summaryWatermark = 0L,
            config = ChatContextWindowConfig(
                contextWindowTokens = 10_000,
                maxCompletionTokens = 100,
                safetyMarginTokens = 100,
                compressionMessageTurnThreshold = 6,
                recentMessageCount = 3
            )
        )

        assertEquals("旧消息摘要", result.summary)
        assertEquals(listOf("msg-1", "msg-2", "msg-3", "msg-4", "msg-5"), summarizer.requests.single().map { it.id })
        assertEquals(listOf("msg-6", "msg-7", "msg-8"), result.messages.map { it.id })
        assertEquals(5L, result.summaryWatermark)
        assertTrue(result.summaryUpdated)
    }

    @Test
    fun existingSummaryPreventsResendingAlreadyCompressedMessages() = runTest {
        val manager = ChatContextWindowManager(FakeSummarizer("不应调用"))
        val messages = (1L..5L).map { index ->
            ChatMessage(
                id = "msg-$index",
                conversationId = "conv-1",
                role = "user",
                content = "message-$index",
                createdAt = index * 10
            )
        }

        val result = manager.build(
            messages = messages,
            existingSummary = "已有摘要",
            summaryWatermark = 30L,
            config = ChatContextWindowConfig(
                contextWindowTokens = 10_000,
                maxCompletionTokens = 100,
                safetyMarginTokens = 100,
                compressionMessageTurnThreshold = 200,
                recentMessageCount = 3
            )
        )

        assertEquals("已有摘要", result.summary)
        assertEquals(listOf("msg-4", "msg-5"), result.messages.map { it.id })
        assertEquals(30L, result.summaryWatermark)
        assertEquals(false, result.summaryUpdated)
    }

    @Test
    fun forceCompressionSummarizesOlderMessagesEvenBelowNormalThreshold() = runTest {
        val summarizer = FakeSummarizer("强制压缩摘要")
        val manager = ChatContextWindowManager(summarizer)
        val messages = (1L..5L).map { index ->
            ChatMessage(
                id = "msg-$index",
                conversationId = "conv-1",
                role = "user",
                content = "message-$index",
                createdAt = index
            )
        }

        val result = manager.build(
            messages = messages,
            existingSummary = "",
            summaryWatermark = 0L,
            config = ChatContextWindowConfig(
                contextWindowTokens = 10_000,
                maxCompletionTokens = 100,
                safetyMarginTokens = 100,
                compressionMessageTurnThreshold = 200,
                recentMessageCount = 2,
                forceCompression = true
            )
        )

        assertEquals("强制压缩摘要", result.summary)
        assertEquals(listOf("msg-1", "msg-2", "msg-3"), summarizer.requests.single().map { it.id })
        assertEquals(listOf("msg-4", "msg-5"), result.messages.map { it.id })
        assertEquals(3L, result.summaryWatermark)
        assertTrue(result.summaryUpdated)
    }

    private class FakeSummarizer(private val response: String) : ConversationContextSummarizer {
        val requests = mutableListOf<List<ChatMessage>>()

        override suspend fun summarize(existingSummary: String, messages: List<ChatMessage>): String {
            requests += messages
            return response
        }
    }
}
