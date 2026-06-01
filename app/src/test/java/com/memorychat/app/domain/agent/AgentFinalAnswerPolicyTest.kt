package com.memorychat.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentFinalAnswerPolicyTest {
    @Test
    fun purePersonaUpdateUsesDeterministicAnswer() {
        val result = AgentFinalAnswerPolicy.resolve(
            decision = AgentDecision(toolCalls = listOf(AgentToolCall("update_persona"))),
            appliedActions = listOf(
                AppliedAgentAction(
                    type = AppliedAgentActionType.PERSONA_UPDATED,
                    target = "persona.name",
                    before = "牛牛",
                    after = "豆包",
                    userVisibleText = "好的，已经改名为「豆包」。"
                )
            )
        )

        assertEquals("好的，已经改名为「豆包」。", result.directAnswer)
        assertEquals(false, result.shouldCallModel)
    }

    @Test
    fun personaUpdateWithWebSearchContinuesModelWithAppliedActionContext() {
        val result = AgentFinalAnswerPolicy.resolve(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall("update_persona"),
                    AgentToolCall("web_search", mapOf("query" to "深圳天气"))
                )
            ),
            appliedActions = listOf(
                AppliedAgentAction(
                    type = AppliedAgentActionType.PERSONA_UPDATED,
                    target = "persona.name",
                    before = "牛牛",
                    after = "豆包",
                    userVisibleText = "好的，已经改名为「豆包」。"
                )
            )
        )

        assertNull(result.directAnswer)
        assertEquals(true, result.shouldCallModel)
        assertEquals("PERSONA_UPDATED persona.name: 牛牛 -> 豆包", result.appliedActionLines.single())
    }
}
