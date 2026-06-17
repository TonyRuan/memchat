package com.memorychat.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitleGeneratorTest {
    @Test
    fun localTitleUsesFirstUserMessageWithoutWaitingForModel() {
        val title = ConversationTitleGenerator.localTitleFromUserMessage(
            "  帮我设计一个长期记忆系统，重点是 Persona 和 Memory 分离  "
        )

        assertEquals("帮我设计一个长期记忆系统，重点是…", title)
    }

    @Test
    fun localTitleFallsBackForBlankMessage() {
        assertEquals("新会话", ConversationTitleGenerator.localTitleFromUserMessage("   "))
    }

    @Test
    fun modelTitleIsCleanedAndCapped() {
        val title = ConversationTitleGenerator.smartTitleFromModelOutput("标题：\"Persona 改名测试流程\"")

        assertEquals("Persona 改名测试", title)
    }

    @Test
    fun onlyPlaceholderOrKnownAutoTitleCanBeAutoReplaced() {
        assertTrue(ConversationTitleGenerator.canAutoReplace("新会话", "帮我设计记忆系统"))
        assertTrue(ConversationTitleGenerator.canAutoReplace("帮我设计记忆系统", "帮我设计记忆系统"))
        assertFalse(ConversationTitleGenerator.canAutoReplace("用户手动标题", "帮我设计记忆系统"))
    }
}
