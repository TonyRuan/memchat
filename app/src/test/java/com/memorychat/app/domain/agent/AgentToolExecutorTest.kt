package com.memorychat.app.domain.agent

import com.memorychat.app.domain.engine.MemoryExtractionStore
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Conversation
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolExecutorTest {
    @Test
    fun updatePersonaToolPersistsAssistantPersona() = runTest {
        val personaStore = FakePersonaStore()
        val memoryStore = FakeMemoryStore()
        val executor = AgentToolExecutor(personaStore, memoryStore) { 1_717_171_717_000L }
        val persona = Persona(id = "persona-1", name = "牛牛", tone = "稳重")

        val result = executor.execute(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall(
                        name = "update_persona",
                        arguments = mapOf(
                            "name" to "噜噜",
                            "tone" to "活泼",
                            "mission" to "陪用户推进工程任务",
                            "expertise" to listOf("Android", "Kotlin"),
                            "communication_style" to "短句、直接、先给结论",
                            "tool_policy" to listOf("需要实时信息时使用搜索"),
                            "memory_policy" to listOf("助手人格设置只写 Persona，不写 Memory"),
                            "example_dialogues" to listOf("用户：你是谁？\n助手：我是噜噜，会直接帮你推进。")
                        )
                    )
                )
            ),
            persona = persona,
            conversation = Conversation(id = "conv-1", title = "测试"),
            sourceMessages = listOf(ChatMessage(id = "msg-1", conversationId = "conv-1", role = "user", content = "你叫噜噜"))
        )

        assertEquals("噜噜", result.persona.name)
        assertEquals("活泼", result.persona.tone)
        assertEquals("陪用户推进工程任务", result.persona.mission)
        assertEquals(listOf("Android", "Kotlin"), result.persona.expertise)
        assertEquals("短句、直接、先给结论", result.persona.communicationStyle)
        assertEquals(listOf("需要实时信息时使用搜索"), result.persona.toolPolicy)
        assertEquals(listOf("助手人格设置只写 Persona，不写 Memory"), result.persona.memoryPolicy)
        assertEquals(listOf("用户：你是谁？\n助手：我是噜噜，会直接帮你推进。"), result.persona.exampleDialogues)
        assertEquals("噜噜", personaStore.saved.single().name)
        assertTrue(result.toolResults.single().contains("update_persona"))
        assertEquals(1, result.appliedActions.size)
        assertEquals(AppliedAgentActionType.PERSONA_UPDATED, result.appliedActions.single().type)
        assertEquals("牛牛", result.appliedActions.single().before)
        assertEquals("噜噜", result.appliedActions.single().after)
        assertEquals("好的，已经改名为「噜噜」。", result.appliedActions.single().userVisibleText)
    }

    @Test
    fun updatePersonaSplitsModelEchoedSemicolonLists() = runTest {
        val personaStore = FakePersonaStore()
        val memoryStore = FakeMemoryStore()
        val executor = AgentToolExecutor(personaStore, memoryStore) { 1_717_171_717_000L }
        val persona = Persona(
            id = "persona-1",
            name = "牛牛",
            toolPolicy = listOf("需要实时信息时使用搜索", "本地状态可验证时优先读真实数据"),
            memoryPolicy = listOf("人格设置只写 Persona", "用户资料写入 Memory")
        )

        val result = executor.execute(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall(
                        name = "update_persona",
                        arguments = mapOf(
                            "name" to "验证桃子",
                            "tool_policy" to listOf("需要实时信息时使用搜索; 本地状态可验证时优先读真实数据"),
                            "memory_policy" to listOf("人格设置只写 Persona; 用户资料写入 Memory")
                        )
                    )
                )
            ),
            persona = persona,
            conversation = Conversation(id = "conv-1", title = "测试"),
            sourceMessages = listOf(ChatMessage(id = "msg-1", conversationId = "conv-1", role = "user", content = "给你改名为验证桃子"))
        )

        assertEquals("验证桃子", result.persona.name)
        assertEquals(listOf("需要实时信息时使用搜索", "本地状态可验证时优先读真实数据"), result.persona.toolPolicy)
        assertEquals(listOf("人格设置只写 Persona", "用户资料写入 Memory"), result.persona.memoryPolicy)
    }

    @Test
    fun userAddressingPreferenceSavesMemoryWithoutChangingPersona() = runTest {
        val personaStore = FakePersonaStore()
        val memoryStore = FakeMemoryStore()
        val executor = AgentToolExecutor(personaStore, memoryStore) { 1_717_171_717_000L }
        val persona = Persona(id = "persona-1", name = "牛牛")

        val result = executor.execute(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall(
                        name = "set_user_addressing_preference",
                        arguments = mapOf("addressing" to "大王")
                    )
                )
            ),
            persona = persona,
            conversation = Conversation(id = "conv-1", title = "测试"),
            sourceMessages = listOf(ChatMessage(id = "msg-1", conversationId = "conv-1", role = "user", content = "你叫我大王吧"))
        )

        assertEquals("牛牛", result.persona.name)
        assertTrue(personaStore.saved.isEmpty())
        assertEquals(1, memoryStore.inserted.size)
        assertEquals(MemoryType.PREFERENCE, memoryStore.inserted.single().type)
        assertEquals("用户希望助手称呼自己为大王", memoryStore.inserted.single().content)
        assertEquals(true, result.memoryWritten)
    }

    @Test
    fun saveMemoryToolReportsMemoryWrite() = runTest {
        val executor = AgentToolExecutor(FakePersonaStore(), FakeMemoryStore()) { 1_717_171_717_000L }

        val result = executor.execute(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall(
                        name = "save_memory",
                        arguments = mapOf(
                            "type" to "project",
                            "content" to "真机测试的颜色是靛蓝",
                            "importance" to 4,
                            "confidence" to 0.9
                        )
                    )
                )
            ),
            persona = Persona(name = "牛牛"),
            conversation = Conversation(id = "conv-1", title = "测试"),
            sourceMessages = listOf(ChatMessage(id = "msg-1", conversationId = "conv-1", role = "user", content = "记住真机测试颜色是靛蓝"))
        )

        assertEquals(true, result.memoryWritten)
        assertTrue(result.toolResults.single().contains("save_memory"))
    }

    @Test
    fun memoryToolsDoNotWriteWhenGenerateMemoryDisabled() = runTest {
        val memoryStore = FakeMemoryStore()
        val executor = AgentToolExecutor(FakePersonaStore(), memoryStore) { 1_717_171_717_000L }

        val result = executor.execute(
            decision = AgentDecision(
                toolCalls = listOf(
                    AgentToolCall(
                        name = "set_user_addressing_preference",
                        arguments = mapOf("addressing" to "大王")
                    ),
                    AgentToolCall(
                        name = "save_memory",
                        arguments = mapOf(
                            "type" to "project",
                            "content" to "真机测试的颜色是靛蓝"
                        )
                    )
                )
            ),
            persona = Persona(name = "牛牛"),
            conversation = Conversation(id = "conv-1", title = "测试", generateMemory = false),
            sourceMessages = listOf(ChatMessage(id = "msg-1", conversationId = "conv-1", role = "user", content = "记住这个"))
        )

        assertEquals(false, result.memoryWritten)
        assertTrue(memoryStore.inserted.isEmpty())
        assertTrue(result.toolResults.all { it.contains("skipped") })
    }

    @Test
    fun getCurrentTimeReturnsToolResultWithoutStorageWrites() = runTest {
        val personaStore = FakePersonaStore()
        val memoryStore = FakeMemoryStore()
        val executor = AgentToolExecutor(personaStore, memoryStore) { 1_717_171_717_000L }

        val result = executor.execute(
            decision = AgentDecision(toolCalls = listOf(AgentToolCall("get_current_time"))),
            persona = Persona(name = "牛牛"),
            conversation = Conversation(id = "conv-1", title = "测试"),
            sourceMessages = emptyList()
        )

        assertTrue(result.toolResults.single().contains("get_current_time"))
        assertTrue(memoryStore.inserted.isEmpty())
        assertTrue(personaStore.saved.isEmpty())
    }

    private class FakePersonaStore : AgentPersonaStore {
        val saved = mutableListOf<Persona>()
        override suspend fun savePersona(persona: Persona) {
            saved += persona
        }
    }

    private class FakeMemoryStore : MemoryExtractionStore {
        val active = mutableListOf<Memory>()
        val inserted = mutableListOf<Memory>()
        val updated = mutableListOf<Memory>()

        override suspend fun getActiveMemories(): List<Memory> = active

        override suspend fun isTombstoned(content: String, type: MemoryType): Boolean = false

        override suspend fun insert(memory: Memory) {
            inserted += memory
            active += memory
        }

        override suspend fun getById(id: String): Memory? = active.firstOrNull { it.id == id }

        override suspend fun update(memory: Memory) {
            updated += memory
            active.removeAll { it.id == memory.id }
            active += memory
        }
    }
}
