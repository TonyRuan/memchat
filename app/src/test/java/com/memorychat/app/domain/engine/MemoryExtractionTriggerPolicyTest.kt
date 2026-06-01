package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryExtractionTriggerPolicyTest {
    private val policy = MemoryExtractionTriggerPolicy(batchTurnThreshold = 20)

    @Test
    fun explicitMemoryCommandTriggersImmediately() {
        val latestUser = user("u1", "记住，第一阶段不做云端")
        val messages = listOf(latestUser, assistant("a1"))

        val trigger = policy.afterAssistantTurn(messages, latestUser)

        assertEquals(MemoryExtractionTrigger.EXPLICIT_MEMORY, trigger)
    }

    @Test
    fun ordinaryConversationDoesNotTriggerBeforeTwentyCompletedTurns() {
        val messages = (1..19).flatMap { index ->
            listOf(user("u$index", "普通消息 $index"), assistant("a$index"))
        }

        val trigger = policy.afterAssistantTurn(messages, messages.filter { it.role == "user" }.last())

        assertNull(trigger)
    }

    @Test
    fun ordinaryConversationTriggersAtTwentyCompletedTurns() {
        val messages = (1..20).flatMap { index ->
            listOf(user("u$index", "普通消息 $index"), assistant("a$index"))
        }

        val trigger = policy.afterAssistantTurn(messages, messages.filter { it.role == "user" }.last())

        assertEquals(MemoryExtractionTrigger.TURN_BATCH, trigger)
    }

    @Test
    fun conversationExitTriggersWhenThereAreUnextractedUserMessages() {
        val messages = listOf(user("u1", "我在做一个永久记忆聊天 APP"), assistant("a1"))

        val trigger = policy.onConversationExit(messages)

        assertEquals(MemoryExtractionTrigger.CONVERSATION_EXIT, trigger)
    }

    @Test
    fun conversationExitDoesNotTriggerWithoutNewMessages() {
        assertNull(policy.onConversationExit(emptyList()))
    }

    private fun user(id: String, content: String): ChatMessage {
        return ChatMessage(id = id, conversationId = "conv-1", role = "user", content = content)
    }

    private fun assistant(id: String, content: String = "ok"): ChatMessage {
        return ChatMessage(id = id, conversationId = "conv-1", role = "assistant", content = content)
    }
}
