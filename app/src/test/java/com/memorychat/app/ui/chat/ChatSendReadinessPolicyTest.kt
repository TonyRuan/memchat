package com.memorychat.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendReadinessPolicyTest {
    @Test
    fun rejectsSendUntilConversationAndProviderAreReady() {
        val result = ChatSendReadinessPolicy.evaluate(
            content = "你好",
            providerReady = false,
            conversationLoaded = true,
            generationActive = false
        )

        assertFalse(result.accepted)
        assertEquals("模型配置未就绪，请先检查设置", result.message)
    }

    @Test
    fun acceptsNonBlankMessageWhenReadyAndIdle() {
        val result = ChatSendReadinessPolicy.evaluate(
            content = "你好",
            providerReady = true,
            conversationLoaded = true,
            generationActive = false
        )

        assertTrue(result.accepted)
        assertEquals(null, result.message)
    }
}
