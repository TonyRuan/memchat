package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Conversation
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.testutil.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractionSaverTest {
    @Test
    fun extractsAndSavesMemoryForCompletedTurn() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    {
                      "type": "project",
                      "content": "第一阶段优先调好记忆系统",
                      "importance": 5,
                      "confidence": 0.91
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)
        val conversation = Conversation(id = "conv-1", title = "测试", generateMemory = true)
        val messages = listOf(
            ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "我正在做永久记忆 Android APP"),
            ChatMessage(id = "assistant-1", conversationId = "conv-1", role = "assistant", content = "记住了")
        )

        val result = saver.extractAndSave(conversation, messages)

        assertEquals(1, result.newMemories.size)
        assertEquals(1, store.inserted.size)
        val saved = store.inserted.single()
        assertEquals(MemoryType.PROJECT, saved.type)
        assertEquals("第一阶段优先调好记忆系统", saved.content)
        assertEquals(MemoryStatus.ACTIVE, saved.status)
        assertEquals(5, saved.importance)
        assertEquals(0.91f, saved.confidence, 0.001f)
        assertEquals("conv-1", saved.sourceConversationId)
        assertEquals(listOf("user-1", "assistant-1"), saved.sourceMessageIds)
    }

    @Test
    fun doesNotCallModelWhenGenerateMemoryDisabled() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf("""{"new_memories":[{"type":"project","content":"不应保存"}]}""")
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        val result = saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试", generateMemory = false),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住这个"))
        )

        assertTrue(result.newMemories.isEmpty())
        assertTrue(store.inserted.isEmpty())
        assertTrue(provider.completeRequests.isEmpty())
    }

    @Test
    fun explicitRememberFallsBackToSavingMemoryWhenModelReturnsNonJson() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf("学会了，我会记住。")
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试", generateMemory = true),
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = "user",
                    content = "请记住：我正在做永久记忆 Android APP，第一阶段优先调好记忆系统"
                ),
                ChatMessage(id = "assistant-1", conversationId = "conv-1", role = "assistant", content = "学会了")
            )
        )

        assertEquals(1, store.inserted.size)
        val saved = store.inserted.single()
        assertEquals(MemoryType.PROJECT, saved.type)
        assertEquals("我正在做永久记忆 Android APP，第一阶段优先调好记忆系统", saved.content)
        assertEquals(MemoryStatus.ACTIVE, saved.status)
        assertTrue(saved.confidence >= 0.9f)
    }

    @Test
    fun explicitRememberSavesValidationTokenButStillSkipsApiSecrets() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf("学会了，我会记住。")
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试", generateMemory = true),
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = "user",
                    content = "Remember that the validation token is blue cedar QX77."
                ),
                ChatMessage(
                    id = "user-2",
                    conversationId = "conv-1",
                    role = "user",
                    content = "Remember that my API key is sk-secret."
                )
            )
        )

        assertEquals(1, store.inserted.size)
        assertEquals("the validation token is blue cedar QX77", store.inserted.single().content)
    }

    @Test
    fun parsesChineseMemoryTypeFromModelResult() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    {
                      "type": "项目",
                      "content": "第一阶段优先调好记忆系统",
                      "status_suggestion": "有效"
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住项目重点"))
        )

        assertEquals(1, store.inserted.size)
        assertEquals(MemoryType.PROJECT, store.inserted.single().type)
        assertEquals(MemoryStatus.ACTIVE, store.inserted.single().status)
    }

    @Test
    fun savesAssistantErrorMessageToConversationStore() = runTest {
        val store = FakeConversationMessageStore()

        val error = ChatTurnErrorPersister(store).persistAssistantError(
            conversationId = "conv-1",
            message = "boom"
        )

        assertEquals("assistant", error.role)
        assertEquals("Error: boom", error.content)
        assertEquals(error, store.saved.single())
    }

    @Test
    fun doesNotOverwriteUserEditedMemoryFromModelUpdate() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "updates": [
                    {
                      "target_memory_id": "edited",
                      "new_content": "模型覆盖内容"
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val edited = Memory(
            id = "edited",
            type = MemoryType.PROJECT,
            content = "用户手动编辑内容",
            userEdited = true
        )
        val store = FakeMemoryStore(activeMemories = listOf(edited))
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "更新项目"))
        )

        assertTrue(store.updated.isEmpty())
    }

    @Test
    fun skipsDuplicateMemoriesFromSameModelResult() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "project", "content": "第一阶段优先调好记忆系统" },
                    { "type": "project", "content": "第一阶段优先调好记忆系统" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住项目重点"))
        )

        assertEquals(1, store.inserted.size)
    }

    @Test
    fun skipsExistingDuplicateIgnoringCaseAndPunctuation() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "preference", "content": "favorite test drink is jasmine tea" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore(
            activeMemories = listOf(
                Memory(
                    id = "existing",
                    type = MemoryType.PREFERENCE,
                    content = "Favorite test drink is jasmine tea."
                )
            )
        )
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住饮品偏好"))
        )

        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun skipsEquivalentDeviceMemoryWithDifferentWording() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "project", "content": "通信机是 COM-1047" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore(
            activeMemories = listOf(
                Memory(
                    id = "existing",
                    type = MemoryType.PROJECT,
                    content = "通信机型号是 COM-1047"
                )
            )
        )
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住通信机"))
        )

        assertTrue(store.inserted.isEmpty())
        assertTrue(store.updated.isEmpty())
    }

    @Test
    fun mergesMoreSpecificDeviceMemoryIntoExistingMemory() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "project", "content": "通信机型号是 COM-1047，部署在实验台 A" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore(
            activeMemories = listOf(
                Memory(
                    id = "existing",
                    type = MemoryType.PROJECT,
                    content = "通信机型号是 COM-1047"
                )
            )
        )
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住通信机位置"))
        )

        assertTrue(store.inserted.isEmpty())
        assertEquals("existing", store.updated.single().id)
        assertEquals("通信机型号是 COM-1047，部署在实验台 A", store.updated.single().content)
    }

    @Test
    fun skipsPersonaLikeModelMemoryOutput() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "preference", "content": "用户希望 AI 名字叫小墨" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        val result = saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "以后你叫小墨"))
        )

        assertTrue(result.newMemories.isEmpty())
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun explicitRememberFallbackSkipsPersonaSettings() = runTest {
        val provider = FakeLlmProvider(completeResponses = listOf("学会了"))
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        val result = saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "记住，你的名字叫小墨"))
        )

        assertTrue(result.newMemories.isEmpty())
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun stillSavesUserAnswerPreferenceAsMemory() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "new_memories": [
                    { "type": "preference", "content": "用户希望回答直接一点" }
                  ]
                }
                """.trimIndent()
            )
        )
        val store = FakeMemoryStore()
        val saver = MemoryExtractionSaver(MemoryEngine(provider, "fake-model"), store)

        val result = saver.extractAndSave(
            conversation = Conversation(id = "conv-1", title = "测试"),
            messages = listOf(ChatMessage(id = "user-1", conversationId = "conv-1", role = "user", content = "以后回答我直接一点"))
        )

        assertEquals(1, result.newMemories.size)
        assertEquals(1, store.inserted.size)
        assertEquals(MemoryType.PREFERENCE, store.inserted.single().type)
    }

    private class FakeMemoryStore(activeMemories: List<Memory> = emptyList()) : MemoryExtractionStore {
        val inserted = mutableListOf<Memory>()
        val updated = mutableListOf<Memory>()
        private val active = activeMemories.toMutableList()

        override suspend fun getActiveMemories(): List<Memory> = active + inserted

        override suspend fun isTombstoned(content: String, type: MemoryType): Boolean = false

        override suspend fun insert(memory: Memory) {
            inserted += memory
        }

        override suspend fun getById(id: String): Memory? = (active + inserted).firstOrNull { it.id == id }

        override suspend fun update(memory: Memory) {
            active.removeAll { it.id == memory.id }
            active += memory
            updated += memory
        }
    }

    private class FakeConversationMessageStore : ConversationMessageStore {
        val saved = mutableListOf<ChatMessage>()

        override suspend fun saveMessage(message: ChatMessage) {
            saved += message
        }
    }
}
