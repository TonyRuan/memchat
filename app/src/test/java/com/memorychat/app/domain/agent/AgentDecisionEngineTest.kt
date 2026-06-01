package com.memorychat.app.domain.agent

import com.memorychat.app.domain.model.Persona
import com.memorychat.app.testutil.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDecisionEngineTest {
    @Test
    fun parsesPersonaToolCallAndTemporaryFormat() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "tool_calls": [
                    {
                      "name": "update_persona",
                      "arguments": {
                        "name": "噜噜",
                        "tone": "活泼"
                      }
                    }
                  ],
                  "temporary_response_format": "markdown",
                  "should_continue_chat": true
                }
                """.trimIndent()
            )
        )

        val decision = AgentDecisionEngine(provider, "fake-model").decide(
            userMessage = "给你改名叫噜噜，这次用 Markdown 回复",
            currentPersona = Persona(name = "牛牛")
        )

        assertEquals("markdown", decision.temporaryResponseFormat)
        assertEquals(true, decision.shouldContinueChat)
        assertEquals(1, decision.toolCalls.size)
        assertEquals("update_persona", decision.toolCalls.first().name)
        assertEquals("噜噜", decision.toolCalls.first().stringArg("name"))
        assertEquals("活泼", decision.toolCalls.first().stringArg("tone"))
    }

    @Test
    fun stripsUnknownToolCalls() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "tool_calls": [
                    {"name": "shell", "arguments": {"command": "rm -rf /"}},
                    {"name": "get_current_time", "arguments": {}}
                  ],
                  "should_continue_chat": true
                }
                """.trimIndent()
            )
        )

        val decision = AgentDecisionEngine(provider, "fake-model").decide(
            userMessage = "现在几点",
            currentPersona = Persona(name = "牛牛")
        )

        assertEquals(listOf("get_current_time"), decision.toolCalls.map { it.name })
    }

    @Test
    fun returnsEmptyDecisionForMalformedJson() = runTest {
        val provider = FakeLlmProvider(completeResponses = listOf("不是 JSON"))

        val decision = AgentDecisionEngine(provider, "fake-model").decide(
            userMessage = "你好",
            currentPersona = Persona(name = "牛牛")
        )

        assertTrue(decision.toolCalls.isEmpty())
        assertEquals(true, decision.shouldContinueChat)
    }

    @Test
    fun promptDefinesAllowedToolsAndSemanticRouting() = runTest {
        val provider = FakeLlmProvider(completeResponses = listOf("""{"tool_calls":[]}"""))

        AgentDecisionEngine(provider, "fake-model").decide(
            userMessage = "你叫我大王吧",
            currentPersona = Persona(name = "牛牛")
        )

        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("Allowed tools"))
        assertTrue(prompt.contains("update_persona"))
        assertTrue(prompt.contains("set_user_addressing_preference"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("Decide by semantic meaning"))
        assertTrue(prompt.contains("Current assistant persona"))
    }

    @Test
    fun keepsWebSearchToolCall() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "tool_calls": [
                    {"name": "web_search", "arguments": {"query": "latest Android news"}}
                  ],
                  "should_continue_chat": true
                }
                """.trimIndent()
            )
        )

        val decision = AgentDecisionEngine(provider, "fake-model").decide(
            userMessage = "查一下 Android 最新新闻",
            currentPersona = Persona(name = "牛牛")
        )

        assertEquals("web_search", decision.toolCalls.single().name)
        assertEquals("latest Android news", decision.toolCalls.single().stringArg("query"))
    }
}
