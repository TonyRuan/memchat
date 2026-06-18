package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestPreparerTest {

    @Test
    fun prepareCompressesContextPersistsSummaryAndInjectsSystemPrompt() = runTest {
        val store = FakeRollingSummaryStore()
        val preparer = ChatRequestPreparer(
            contextWindowManager = ChatContextWindowManager(FakeSummarizer("压缩后的摘要")),
            rollingSummaryStore = store
        )
        val messages = (1L..5L).map { index ->
            ChatMessage(
                id = "msg-$index",
                conversationId = "conv-1",
                role = if (index % 2L == 0L) "assistant" else "user",
                content = "message-$index",
                createdAt = index
            )
        }

        val result = preparer.prepare(
            conversationId = "conv-1",
            sourceMessages = messages,
            memories = listOf(
                Memory(
                    id = "pref-1",
                    type = MemoryType.PREFERENCE,
                    content = "尽量使用中文",
                    createdAt = 1_000L,
                    updatedAt = 2_000L
                )
            ),
            persona = Persona(name = "技术伙伴"),
            toolResults = listOf("recall_memory: 命中 1 条"),
            appliedActionLines = listOf("memory.write: 已保存"),
            temporaryResponseFormat = "markdown",
            contextConfig = ChatContextWindowConfig(
                contextWindowTokens = 10_000,
                maxCompletionTokens = 100,
                safetyMarginTokens = 100,
                compressionMessageTurnThreshold = 3,
                recentMessageCount = 2
            ),
            nowMillis = 3_000L
        )

        assertEquals(listOf("system", "assistant", "user"), result.messages.map { it.role })
        assertEquals(listOf("message-4", "message-5"), result.messages.drop(1).map { it.content })
        assertEquals("压缩后的摘要", result.contextWindow.summary)
        assertEquals("压缩后的摘要", store.savedSummary)
        assertEquals(3L, store.savedWatermark)
        val systemPrompt = result.messages.first().content
        assertTrue(systemPrompt.contains("[Persona Contract]"))
        assertTrue(systemPrompt.contains("[User Preferences]"))
        assertTrue(systemPrompt.contains("[Rolling Conversation Summary]"))
        assertTrue(systemPrompt.contains("压缩后的摘要"))
        assertTrue(systemPrompt.contains("Current time: 1970-01-01T00:00:03Z"))
        assertTrue(systemPrompt.contains("Temporary response format for this answer: markdown"))
        assertTrue(systemPrompt.contains("- recall_memory: 命中 1 条"))
        assertTrue(systemPrompt.contains("- memory.write: 已保存"))
    }

    @Test
    fun prepareDoesNotPersistWhenSummaryIsUnchanged() = runTest {
        val store = FakeRollingSummaryStore(summary = "已有摘要", watermark = 10L)
        val preparer = ChatRequestPreparer(
            contextWindowManager = ChatContextWindowManager(FakeSummarizer("不应使用")),
            rollingSummaryStore = store
        )

        val result = preparer.prepare(
            conversationId = "conv-1",
            sourceMessages = listOf(
                ChatMessage(role = "user", content = "new message", createdAt = 20L)
            ),
            memories = emptyList(),
            persona = null,
            contextConfig = ChatContextWindowConfig(
                contextWindowTokens = 10_000,
                maxCompletionTokens = 100,
                safetyMarginTokens = 100,
                compressionMessageTurnThreshold = 10,
                recentMessageCount = 2
            ),
            nowMillis = 4_000L
        )

        assertEquals(null, store.savedSummary)
        assertEquals(null, store.savedWatermark)
        assertEquals(listOf("system", "user"), result.messages.map { it.role })
        assertTrue(result.messages.first().content.contains("已有摘要"))
    }

    private class FakeRollingSummaryStore(
        private val summary: String = "",
        private val watermark: Long = 0L
    ) : ConversationRollingSummaryStore {
        var savedSummary: String? = null
        var savedWatermark: Long? = null

        override suspend fun getSummary(conversationId: String): String = summary

        override suspend fun getSummaryWatermark(conversationId: String): Long = watermark

        override suspend fun saveSummary(conversationId: String, summary: String) {
            savedSummary = summary
        }

        override suspend fun saveSummaryWatermark(conversationId: String, watermark: Long) {
            savedWatermark = watermark
        }
    }

    private class FakeSummarizer(private val response: String) : ConversationContextSummarizer {
        override suspend fun summarize(existingSummary: String, messages: List<ChatMessage>): String = response
    }
}
