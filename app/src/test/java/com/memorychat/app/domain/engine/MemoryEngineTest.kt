package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import com.memorychat.app.testutil.FakeLlmProvider
import com.memorychat.app.domain.provider.LlmProvider
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEngineTest {
    private val engine = MemoryEngine(NoopLlmProvider())

    @Test
    fun recallForProjectSceneUsesProjectSummaryAndPreferenceOnly() {
        val memories = listOf(
            memory("profile-1", MemoryType.PROFILE, "用户在北京工作"),
            memory("project-1", MemoryType.PROJECT, "第一阶段优先验证记忆系统"),
            memory("summary-1", MemoryType.SUMMARY, "本轮确认不做云同步"),
            memory("preference-1", MemoryType.PREFERENCE, "回答先给结论")
        )

        val result = engine.recall(
            userMessage = "这个项目的第一阶段重点是什么？",
            allActiveMemories = memories,
            persona = null
        )

        assertEquals("project", result.scene)
        assertEquals(listOf("project-1", "summary-1", "preference-1"), result.memories.map { it.id })
        assertFalse(result.memories.any { it.type == MemoryType.PROFILE })
    }

    @Test
    fun recallForGeneralSceneDoesNotInjectProjectMemories() {
        val memories = listOf(
            memory("profile-1", MemoryType.PROFILE, "用户是 Android 开发者"),
            memory("project-1", MemoryType.PROJECT, "第一阶段优先验证记忆系统"),
            memory("preference-1", MemoryType.PREFERENCE, "尽量使用中文"),
            memory("summary-1", MemoryType.SUMMARY, "最近聊过测试策略")
        )

        val result = engine.recall(
            userMessage = "今天随便聊两句",
            allActiveMemories = memories,
            persona = null
        )

        assertEquals("general", result.scene)
        assertEquals(listOf("profile-1", "preference-1", "summary-1"), result.memories.map { it.id })
        assertFalse(result.memories.any { it.type == MemoryType.PROJECT })
    }

    @Test
    fun recallForExplicitMemoryQuestionIncludesProjectMemories() {
        val memories = listOf(
            memory("project-1", MemoryType.PROJECT, "第一阶段优先验证记忆系统"),
            memory("preference-1", MemoryType.PREFERENCE, "尽量使用中文")
        )

        val result = engine.recall(
            userMessage = "还记得我让你记住的内容吗？",
            allActiveMemories = memories,
            persona = null
        )

        assertEquals("memory_query", result.scene)
        assertTrue(result.memories.any { it.id == "project-1" })
    }

    @Test
    fun recallDeduplicatesAndCapsResults() {
        val duplicate = memory("same-id", MemoryType.PROFILE, "用户偏好中文")
        val memories = listOf(duplicate, duplicate.copy(content = "重复内容")) +
            (1..10).map { memory("pref-$it", MemoryType.PREFERENCE, "偏好 $it") }

        val result = engine.recall(
            userMessage = "我喜欢什么回答方式？",
            allActiveMemories = memories,
            persona = null
        )

        assertEquals(result.memories.size, result.memories.map { it.id }.distinct().size)
        assertTrue(result.memories.size <= 8)
    }

    @Test
    fun buildRecallPromptSeparatesPersonaFromLongTermMemories() {
        val prompt = MemoryEngine.buildRecallPrompt(
            persona = Persona(
                name = "技术伙伴",
                role = "技术协作者",
                tone = "直接",
                behaviorRules = listOf("结论先行"),
                boundaries = listOf("不假装知道")
            ),
            preferences = listOf(memory("pref", MemoryType.PREFERENCE, "尽量使用中文")),
            profile = listOf(memory("profile", MemoryType.PROFILE, "用户是 Android 开发者")),
            projects = listOf(memory("project", MemoryType.PROJECT, "第一阶段优先验证记忆系统")),
            summaries = listOf(memory("summary", MemoryType.SUMMARY, "最近在重构测试"))
        )

        assertTrue(prompt.contains("[Current Persona]"))
        assertTrue(prompt.contains("Name: 技术伙伴"))
        assertTrue(prompt.contains("[User Preferences]"))
        assertTrue(prompt.contains("[User Profile]"))
        assertTrue(prompt.contains("[Project Memory]"))
        assertTrue(prompt.contains("[Recent Summaries]"))
    }

    @Test
    fun buildRecallPromptAsksForMarkdownWhenHelpful() {
        val prompt = MemoryEngine.buildRecallPrompt(
            persona = null,
            preferences = emptyList(),
            profile = emptyList(),
            projects = emptyList(),
            summaries = emptyList()
        )

        assertTrue(prompt.contains("Markdown"))
        assertTrue(prompt.contains("code fences"))
    }

    @Test
    fun extractionPromptIncludesExistingMemoryIdsForModelUpdates() = runTest {
        val provider = FakeLlmProvider(completeResponses = listOf("""{"new_memories":[]}"""))
        val engine = MemoryEngine(provider, "fake-model")

        engine.extractMemories(
            messages = listOf(
                com.memorychat.app.domain.model.ChatMessage(
                    role = "user",
                    content = "通信机部署在实验台 A"
                )
            ),
            existingMemories = listOf(
                memory("memory-1047", MemoryType.PROJECT, "通信机型号是 COM-1047")
            )
        )

        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("- id=memory-1047 type=PROJECT user_edited=false content=通信机型号是 COM-1047"))
        assertTrue(prompt.contains("Use updates when new information refines or expands an existing memory"))
    }

    private fun memory(id: String, type: MemoryType, content: String): Memory {
        return Memory(
            id = id,
            type = type,
            content = content,
            status = MemoryStatus.ACTIVE
        )
    }

    private class NoopLlmProvider : LlmProvider {
        override fun streamChat(request: com.memorychat.app.domain.model.ChatRequest) =
            emptyFlow<com.memorychat.app.domain.model.ChatChunk>()

        override suspend fun complete(request: com.memorychat.app.domain.model.ChatRequest) =
            com.memorychat.app.domain.model.ChatResponse("")
    }
}
