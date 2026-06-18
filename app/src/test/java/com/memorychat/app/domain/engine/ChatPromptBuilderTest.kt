package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {

    @Test
    fun buildAddsMemoryPersonaAndRuntimeContext() {
        val prompt = ChatPromptBuilder.build(
            memories = listOf(
                Memory(
                    id = "pref-1",
                    type = MemoryType.PREFERENCE,
                    content = "回答先给结论",
                    createdAt = 1_000L,
                    updatedAt = 2_000L,
                    lastUsedAt = 2_500L
                ),
                Memory(
                    id = "profile-1",
                    type = MemoryType.PROFILE,
                    content = "用户在做 Android 架构改造",
                    createdAt = 1_100L,
                    updatedAt = 2_100L
                )
            ),
            persona = Persona(
                name = "技术伙伴",
                mission = "帮助用户把想法推进成可验证的软件改动"
            ),
            toolResults = listOf("recall_memory: 命中 2 条"),
            appliedActionLines = listOf("persona.name: 技术伙伴 -> 架构伙伴"),
            temporaryResponseFormat = "markdown",
            rollingSummary = "上轮讨论了记忆检索边界",
            nowMillis = 3_000L
        )

        assertTrue(prompt.contains("[Persona Contract]"))
        assertTrue(prompt.contains("Name: 技术伙伴"))
        assertTrue(prompt.contains("[User Preferences]"))
        assertTrue(prompt.contains("created_at=1970-01-01T00:00:01Z"))
        assertTrue(prompt.contains("updated_at=1970-01-01T00:00:02Z"))
        assertTrue(prompt.contains("last_used_at=1970-01-01T00:00:02.500Z"))
        assertTrue(prompt.contains("content=回答先给结论"))
        assertTrue(prompt.contains("[User Profile]"))
        assertTrue(prompt.contains("[Rolling Conversation Summary]"))
        assertTrue(prompt.contains("上轮讨论了记忆检索边界"))
        assertTrue(prompt.contains("[Environment]"))
        assertTrue(prompt.contains("Current time: 1970-01-01T00:00:03Z"))
        assertTrue(prompt.contains("Temporary response format for this answer: markdown"))
        assertTrue(prompt.contains("[Tool Results]"))
        assertTrue(prompt.contains("- recall_memory: 命中 2 条"))
        assertTrue(prompt.contains("[Applied Actions]"))
        assertTrue(prompt.contains("Treat them as observations, not suggestions."))
        assertTrue(prompt.contains("- persona.name: 技术伙伴 -> 架构伙伴"))
    }

    @Test
    fun decorateAddsRuntimeContextToExistingPromptWithoutRebuildingMemory() {
        val prompt = ChatPromptBuilder.decorate(
            basePrompt = "BASE PROMPT",
            toolResults = listOf("search_history: 找到 1 条"),
            appliedActionLines = listOf("memory.write: 已保存"),
            temporaryResponseFormat = null,
            rollingSummary = "",
            nowMillis = 4_000L
        )

        assertTrue(prompt.startsWith("BASE PROMPT\n"))
        assertTrue(prompt.contains("[Environment]"))
        assertTrue(prompt.contains("Current time: 1970-01-01T00:00:04Z"))
        assertTrue(prompt.contains("- search_history: 找到 1 条"))
        assertTrue(prompt.contains("- memory.write: 已保存"))
        assertFalse(prompt.contains("[User Preferences]"))
        assertFalse(prompt.contains("Temporary response format for this answer"))
    }

    @Test
    fun buildBasePromptKeepsLegacyPathFreeOfRuntimeDecoration() {
        val prompt = ChatPromptBuilder.buildBasePrompt(
            memories = listOf(
                Memory(
                    id = "pref-legacy",
                    type = MemoryType.PREFERENCE,
                    content = "保留直接广播路径的基础记忆注入"
                )
            ),
            persona = Persona(name = "技术伙伴")
        )

        assertTrue(prompt.contains("[Persona Contract]"))
        assertTrue(prompt.contains("[User Preferences]"))
        assertTrue(prompt.contains("保留直接广播路径的基础记忆注入"))
        assertFalse(prompt.contains("[Environment]"))
        assertFalse(prompt.contains("[Rolling Conversation Summary]"))
        assertFalse(prompt.contains("[Tool Results]"))
        assertFalse(prompt.contains("[Applied Actions]"))
    }
}
