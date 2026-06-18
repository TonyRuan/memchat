package com.memorychat.app.data.repository

import com.google.gson.JsonParser
import com.memorychat.app.data.local.db.dao.ConversationDao
import com.memorychat.app.data.local.db.dao.MemoryDao
import com.memorychat.app.data.local.db.dao.MemoryTombstoneDao
import com.memorychat.app.data.local.db.dao.MessageDao
import com.memorychat.app.data.local.db.dao.PersonaDao
import com.memorychat.app.data.local.db.entity.ConversationEntity
import com.memorychat.app.data.local.db.entity.MemoryEntity
import com.memorychat.app.data.local.db.entity.MemoryTombstoneEntity
import com.memorychat.app.data.local.db.entity.MessageEntity
import com.memorychat.app.data.local.db.entity.PersonaEntity
import com.memorychat.app.domain.model.HistorySearchScope
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
    @Test
    fun updateConversationTitleIfAutoReplacesPlaceholderTitle() = runBlocking {
        val conversationDao = FakeConversationDao().apply {
            insert(ConversationEntity(id = "conv-1", title = "新会话"))
        }
        val repo = ConversationRepository(conversationDao, FakeMessageDao())

        val updated = repo.updateConversationTitleIfAuto(
            conversationId = "conv-1",
            newTitle = "Persona 改名测试",
            knownAutoTitle = "你叫噜噜"
        )

        assertEquals("Persona 改名测试", updated?.title)
        assertEquals("Persona 改名测试", conversationDao.getById("conv-1")?.title)
    }

    @Test
    fun updateConversationTitleIfAutoDoesNotOverwriteManualTitle() = runBlocking {
        val conversationDao = FakeConversationDao().apply {
            insert(ConversationEntity(id = "conv-1", title = "用户手动标题"))
        }
        val repo = ConversationRepository(conversationDao, FakeMessageDao())

        val unchanged = repo.updateConversationTitleIfAuto(
            conversationId = "conv-1",
            newTitle = "Persona 改名测试",
            knownAutoTitle = "你叫噜噜"
        )

        assertEquals("用户手动标题", unchanged?.title)
        assertEquals("用户手动标题", conversationDao.getById("conv-1")?.title)
    }

    @Test
    fun updateConversationTitleIfAutoDoesNotOverwriteTitleChangedDuringUpdate() = runBlocking {
        val conversationDao = FakeConversationDao().apply {
            insert(ConversationEntity(id = "conv-1", title = "新会话"))
            simulateManualTitleBeforeNextWrite("conv-1", "用户手动标题")
        }
        val repo = ConversationRepository(conversationDao, FakeMessageDao())

        val unchanged = repo.updateConversationTitleIfAuto(
            conversationId = "conv-1",
            newTitle = "Persona 改名测试",
            knownAutoTitle = "新会话"
        )

        assertEquals("用户手动标题", unchanged?.title)
        assertEquals("用户手动标题", conversationDao.getById("conv-1")?.title)
    }

    @Test
    fun searchMessagesRanksAcrossConversationsAndExcludesCurrentTurn() = runBlocking {
        val conversationDao = FakeConversationDao().apply {
            insert(ConversationEntity(id = "conv-current", title = "当前会话"))
            insert(ConversationEntity(id = "conv-history", title = "历史调试会话"))
        }
        val messageDao = FakeMessageDao().apply {
            insert(MessageEntity(
                id = "current-old",
                conversationId = "conv-current",
                role = "assistant",
                content = "当前会话早些时候也提过 INFO22，但没有电机故障细节。",
                createdAt = 1_000L
            ))
            insert(MessageEntity(
                id = "history-strong",
                conversationId = "conv-history",
                role = "assistant",
                content = "电机故障详情通过 INFO22 字段展示，调试时要优先核对原始帧。",
                createdAt = 1_200L
            ))
            insert(MessageEntity(
                id = "current-user",
                conversationId = "conv-current",
                role = "user",
                content = "之前电机故障 INFO22 怎么看？",
                createdAt = 2_000L
            ))
            insert(MessageEntity(
                id = "future-history",
                conversationId = "conv-history",
                role = "assistant",
                content = "电机故障 INFO22 之后的新结论不应该被当前回合搜到。",
                createdAt = 2_500L
            ))
        }
        val repo = ConversationRepository(conversationDao, messageDao)

        val matches = repo.searchMessages(
            query = "电机故障 INFO22",
            scope = HistorySearchScope.ALL,
            currentConversationId = "conv-current",
            beforeCreatedAt = 2_000L,
            limit = 1
        )

        assertEquals(listOf("history-strong"), matches.map { it.messageId })
        assertEquals("conv-history", matches.single().conversationId)
        assertEquals("历史调试会话", matches.single().conversationTitle)
        assertTrue(matches.single().reason.contains("query match"))
    }

    @Test
    fun searchMessagesCanLimitToCurrentConversation() = runBlocking {
        val conversationDao = FakeConversationDao().apply {
            insert(ConversationEntity(id = "conv-current", title = "当前会话"))
            insert(ConversationEntity(id = "conv-history", title = "历史调试会话"))
        }
        val messageDao = FakeMessageDao().apply {
            insert(MessageEntity(
                id = "current-old",
                conversationId = "conv-current",
                role = "assistant",
                content = "当前会话早些时候提过 INFO22 展示。",
                createdAt = 1_000L
            ))
            insert(MessageEntity(
                id = "history-strong",
                conversationId = "conv-history",
                role = "assistant",
                content = "历史会话里也提过 INFO22 展示。",
                createdAt = 1_200L
            ))
        }
        val repo = ConversationRepository(conversationDao, messageDao)

        val matches = repo.searchMessages(
            query = "INFO22",
            scope = HistorySearchScope.CURRENT,
            currentConversationId = "conv-current",
            beforeCreatedAt = 2_000L,
            limit = 5
        )

        assertEquals(listOf("current-old"), matches.map { it.messageId })
    }

    @Test
    fun getOrCreateDefaultPersonaCreatesSeedWhenMissing() = runBlocking {
        val personaDao = FakePersonaDao()
        val repo = PersonaRepository(personaDao)

        val persona = repo.getOrCreateDefaultPersona()

        assertEquals("persona_default", persona.id)
        assertEquals("求真助手", persona.name)
        assertEquals("实事求是的聊天助手", persona.role)
        assertEquals("帮助用户把问题说清楚，把事实、推测和建议分开，并给出可验证的下一步", persona.mission)
        assertTrue(persona.communicationStyle?.contains("区分已知事实、合理推测和待验证项") == true)
        assertTrue(persona.behaviorRules.any { it.contains("不确定时直接说明") })
        assertTrue(persona.boundaries.any { it.contains("不把未经验证的信息说成确定结论") })
        assertTrue(persona.isDefault)
        assertEquals("persona_default", personaDao.getDefault()?.id)
    }

    @Test
    fun getOrCreateDefaultPersonaUpgradesBundledDefaultPersona() = runBlocking {
        val personaDao = FakePersonaDao().apply {
            insert(
                PersonaEntity(
                    id = "persona_default",
                    name = "技术伙伴",
                    description = "适合产品讨论、技术协作的默认人格",
                    role = "技术协作者",
                    mission = "帮助用户把想法推进成可验证的产品和工程改动",
                    expertiseJson = """["Android 应用","长期记忆系统","Agent 工具协作","测试验证"]""",
                    tone = "直接、清晰、有见地",
                    communicationStyle = "先给结论，再给关键依据和下一步；避免空泛安慰",
                    behaviorRulesJson = """["漏指令立即补全","结论先行再展开","必要时引用参考","不确定时直接说明"]""",
                    boundariesJson = """["不要假装知道不确定的信息","不要在无偏好的时候硬编偏好"]""",
                    toolPolicyJson = """["需要实时信息、外部资料或真实验证时主动使用可用工具","本地状态可验证时优先读真实数据"]""",
                    memoryPolicyJson = """["助手人格设置只写入 Persona，不写入长期记忆","用户资料、偏好和项目事实写入 Memory"]""",
                    isDefault = 1,
                    createdAt = 100,
                    updatedAt = 100
                )
            )
        }
        val repo = PersonaRepository(personaDao)

        val persona = repo.getOrCreateDefaultPersona()

        assertEquals("求真助手", persona.name)
        assertEquals("实事求是的聊天助手", persona.role)
        assertTrue(persona.behaviorRules.any { it.contains("事实、推测、建议分开") })
        assertEquals("求真助手", personaDao.getById("persona_default")?.name)
    }

    @Test
    fun getOrCreateDefaultPersonaDoesNotOverwriteUserEditedDefaultPersona() = runBlocking {
        val personaDao = FakePersonaDao().apply {
            insert(
                PersonaEntity(
                    id = "persona_default",
                    name = "验证桃子",
                    role = "用户自定义助手",
                    mission = "按用户自定义方式协作",
                    isDefault = 1,
                    createdAt = 100,
                    updatedAt = 200
                )
            )
        }
        val repo = PersonaRepository(personaDao)

        val persona = repo.getOrCreateDefaultPersona()

        assertEquals("验证桃子", persona.name)
        assertEquals("用户自定义助手", persona.role)
        assertEquals("按用户自定义方式协作", persona.mission)
    }

    @Test
    fun getOrCreateDefaultPersonaDoesNotOverwriteEditedDefaultWithLegacyName() = runBlocking {
        val personaDao = FakePersonaDao().apply {
            insert(
                PersonaEntity(
                    id = "persona_default",
                    name = "技术伙伴",
                    role = "用户已调整的技术助手",
                    mission = "按用户保留的旧名称协作",
                    isDefault = 1,
                    createdAt = 100,
                    updatedAt = 200
                )
            )
        }
        val repo = PersonaRepository(personaDao)

        val persona = repo.getOrCreateDefaultPersona()

        assertEquals("技术伙伴", persona.name)
        assertEquals("用户已调整的技术助手", persona.role)
        assertEquals("按用户保留的旧名称协作", persona.mission)
    }

    @Test
    fun importPersonasJsonAcceptsCamelCaseFieldsFromExistingExports() = runBlocking {
        val personaRepo = PersonaRepository(FakePersonaDao())
        val service = ExportImportService(MemoryRepository(FakeMemoryDao(), FakeMemoryTombstoneDao()), personaRepo)
        val json = """
            {
              "version": "1.0",
              "personas": [
                {
                  "id": "p1",
                  "name": "Imported",
                  "mission": "帮助用户推进工程任务",
                  "expertise": ["Android", "Agent"],
                  "communicationStyle": "短句、直接",
                  "behaviorRules": ["rule-a", "rule-b"],
                  "toolPolicy": ["实时信息先搜索"],
                  "memoryPolicy": ["人格设置不写入 Memory"],
                  "exampleDialogues": ["用户：你是谁？\n助手：我是 Imported。"],
                  "isDefault": true
                }
              ]
            }
        """.trimIndent()

        val result = service.importPersonasJson(json)
        val persona = personaRepo.getPersona("p1")

        assertEquals(1, result.imported)
        assertNotNull(persona)
        assertEquals(listOf("rule-a", "rule-b"), persona?.behaviorRules)
        assertEquals("帮助用户推进工程任务", persona?.mission)
        assertEquals(listOf("Android", "Agent"), persona?.expertise)
        assertEquals("短句、直接", persona?.communicationStyle)
        assertEquals(listOf("实时信息先搜索"), persona?.toolPolicy)
        assertEquals(listOf("人格设置不写入 Memory"), persona?.memoryPolicy)
        assertEquals(listOf("用户：你是谁？\n助手：我是 Imported。"), persona?.exampleDialogues)
        assertTrue(persona?.isDefault == true)
    }

    @Test
    fun savingDefaultPersonaClearsOtherDefaults() = runBlocking {
        val personaDao = FakePersonaDao().apply {
            insert(PersonaEntity(id = "p1", name = "旧默认", isDefault = 1))
            insert(PersonaEntity(id = "p2", name = "普通", isDefault = 0))
        }
        val personaRepo = PersonaRepository(personaDao)

        personaRepo.savePersona(Persona(id = "p2", name = "新默认", isDefault = true))

        assertEquals(0, personaDao.getById("p1")?.isDefault)
        assertEquals(1, personaDao.getById("p2")?.isDefault)
        assertEquals("p2", personaDao.getDefault()?.id)
    }

    @Test
    fun importPersonasJsonReportsInvalidRows() = runBlocking {
        val personaRepo = PersonaRepository(FakePersonaDao())
        val service = ExportImportService(MemoryRepository(FakeMemoryDao(), FakeMemoryTombstoneDao()), personaRepo)
        val json = """
            {
              "personas": [
                { "id": "blank", "name": " " },
                { "id": "ok", "name": "可用人格" }
              ]
            }
        """.trimIndent()

        val result = service.importPersonasJson(json)

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(result.errors.any { it.contains("empty persona name") })
        assertNotNull(personaRepo.getPersona("ok"))
    }

    @Test
    fun exportPersonasJsonUsesSnakeCaseFieldNames() = runBlocking {
        val personaRepo = PersonaRepository(FakePersonaDao().apply {
            insert(PersonaEntity(
                id = "p1",
                name = "Exported",
                mission = "帮助用户推进工程任务",
                expertiseJson = """["Android"]""",
                communicationStyle = "短句、直接",
                behaviorRulesJson = """["rule-a"]""",
                toolPolicyJson = """["实时信息先搜索"]""",
                memoryPolicyJson = """["人格设置不写入 Memory"]""",
                exampleDialoguesJson = """["用户：你是谁？\n助手：我是 Exported。"]""",
                isDefault = 1
            ))
        })
        val service = ExportImportService(MemoryRepository(FakeMemoryDao(), FakeMemoryTombstoneDao()), personaRepo)

        val json = service.exportPersonasJson()
        val personaJson = JsonParser.parseString(json)
            .asJsonObject
            .getAsJsonArray("personas")[0]
            .asJsonObject

        assertTrue(personaJson.has("behavior_rules"))
        assertTrue(personaJson.has("communication_style"))
        assertTrue(personaJson.has("tool_policy"))
        assertTrue(personaJson.has("memory_policy"))
        assertTrue(personaJson.has("example_dialogues"))
        assertTrue(personaJson.has("is_default"))
        assertFalse(personaJson.has("behaviorRules"))
        assertFalse(personaJson.has("communicationStyle"))
        assertFalse(personaJson.has("toolPolicy"))
        assertFalse(personaJson.has("isDefault"))
    }

    @Test
    fun importMemoriesJsonImportsValidMemoriesWithDefaults() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "version": "1.0",
              "memories": [
                {
                  "id": "m1",
                  "type": "project",
                  "content": "第一阶段优先验证记忆系统"
                }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)
        val memory = memoryDao.getById("m1")

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertTrue(result.errors.isEmpty())
        assertNotNull(memory)
        assertEquals(MemoryType.PROJECT.name, memory?.type)
        assertEquals(MemoryStatus.ACTIVE.name, memory?.status)
        assertEquals(3, memory?.importance)
        assertEquals(0.8f, memory?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun importMemoriesJsonPreservesTimestampMetadata() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "version": "1.0",
              "memories": [
                {
                  "id": "m-time",
                  "type": "project",
                  "content": "第一阶段优先验证记忆系统",
                  "created_at": 1000,
                  "updated_at": 2000,
                  "last_used_at": 3000
                }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)
        val memory = memoryDao.getById("m-time")

        assertEquals(1, result.imported)
        assertEquals(1_000L, memory?.createdAt)
        assertEquals(2_000L, memory?.updatedAt)
        assertEquals(3_000L, memory?.lastUsedAt)
    }

    @Test
    fun importMemoriesJsonSkipsInvalidRowsWithoutCrashing() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "version": "1.0",
              "memories": [
                { "id": "blank", "type": "profile", "content": " " },
                { "id": "bad-type", "type": "temporary", "content": "should skip" },
                { "id": "bad-status", "type": "profile", "status": "archived", "content": "should skip" }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)

        assertEquals(0, result.imported)
        assertEquals(3, result.skipped)
        assertTrue(result.errors.any { it.contains("empty content") })
        assertTrue(result.errors.any { it.contains("Invalid memory type") })
        assertTrue(result.errors.any { it.contains("Invalid memory status") })
        assertTrue(memoryDao.memories.isEmpty())
    }

    @Test
    fun importMemoriesJsonSkipsTombstonedMemory() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val tombstoneDao = FakeMemoryTombstoneDao().apply {
            tombstone("不要使用猫娘语气", MemoryType.PREFERENCE)
        }
        val service = ExportImportService(MemoryRepository(memoryDao, tombstoneDao), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "memories": [
                { "id": "m1", "type": "preference", "content": "不要使用猫娘语气" }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(memoryDao.memories.isEmpty())
    }

    @Test
    fun disabledMemoryCreatesTypeScopedTombstone() = runBlocking {
        val memoryDao = FakeMemoryDao().apply {
            insert(MemoryEntity(
                id = "m1",
                type = MemoryType.PREFERENCE.name,
                content = "不要使用猫娘语气",
                status = MemoryStatus.ACTIVE.name
            ))
        }
        val tombstoneDao = FakeMemoryTombstoneDao()
        val repo = MemoryRepository(memoryDao, tombstoneDao)

        repo.disable("m1")

        assertEquals(MemoryStatus.DISABLED.name, memoryDao.getById("m1")?.status)
        assertTrue(repo.isTombstoned("不要使用猫娘语气", MemoryType.PREFERENCE))
        assertFalse(repo.isTombstoned("不要使用猫娘语气", MemoryType.PROJECT))
    }

    @Test
    fun deletedMemoryCreatesTypeScopedTombstone() = runBlocking {
        val memoryDao = FakeMemoryDao().apply {
            insert(MemoryEntity(
                id = "m1",
                type = MemoryType.PROJECT.name,
                content = "第一阶段优先调好记忆系统",
                status = MemoryStatus.ACTIVE.name
            ))
        }
        val repo = MemoryRepository(memoryDao, FakeMemoryTombstoneDao())

        repo.delete("m1")

        assertEquals(MemoryStatus.DELETED.name, memoryDao.getById("m1")?.status)
        assertTrue(repo.isTombstoned("第一阶段优先调好记忆系统", MemoryType.PROJECT))
        assertFalse(repo.isTombstoned("第一阶段优先调好记忆系统", MemoryType.PREFERENCE))
    }

    @Test
    fun exportMemoriesJsonExcludesDeletedMemories() = runBlocking {
        val memoryDao = FakeMemoryDao().apply {
            insert(MemoryEntity(
                id = "active",
                type = MemoryType.PROJECT.name,
                content = "第一阶段优先验证记忆系统",
                status = MemoryStatus.ACTIVE.name
            ))
            insert(MemoryEntity(
                id = "deleted",
                type = MemoryType.PREFERENCE.name,
                content = "已删除偏好",
                status = MemoryStatus.DELETED.name
            ))
        }
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))

        val json = service.exportMemoriesJson()
        val memories = JsonParser.parseString(json).asJsonObject.getAsJsonArray("memories")

        assertEquals(1, memories.size())
        assertEquals("active", memories[0].asJsonObject.get("id").asString)
    }

    @Test
    fun exportMemoriesMarkdownIncludesTimestampMetadata() = runBlocking {
        val memoryDao = FakeMemoryDao().apply {
            insert(MemoryEntity(
                id = "active",
                type = MemoryType.PROJECT.name,
                content = "第一阶段优先验证记忆系统",
                status = MemoryStatus.ACTIVE.name,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                lastUsedAt = 3_000L
            ))
        }
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))

        val markdown = service.exportMemoriesMarkdown()

        assertTrue(markdown.contains("created_at=1970-01-01T00:00:01Z"))
        assertTrue(markdown.contains("updated_at=1970-01-01T00:00:02Z"))
        assertTrue(markdown.contains("last_used_at=1970-01-01T00:00:03Z"))
        assertTrue(markdown.contains("第一阶段优先验证记忆系统"))
    }

    private class FakeConversationDao : ConversationDao {
        private val conversations = LinkedHashMap<String, ConversationEntity>()
        private var beforeNextWrite: (() -> Unit)? = null

        override suspend fun getAll(): List<ConversationEntity> {
            return conversations.values.sortedByDescending { it.updatedAt }
        }

        override suspend fun getById(id: String): ConversationEntity? {
            return conversations[id]
        }

        override suspend fun insert(entity: ConversationEntity) {
            beforeNextWrite?.invoke()
            beforeNextWrite = null
            conversations[entity.id] = entity
        }

        override suspend fun update(entity: ConversationEntity) {
            conversations[entity.id] = entity
        }

        override suspend fun updateTitleIfCurrent(
            id: String,
            newTitle: String,
            updatedAt: Long,
            placeholderTitle: String,
            knownAutoTitle: String
        ): Int {
            beforeNextWrite?.invoke()
            beforeNextWrite = null
            val current = conversations[id] ?: return 0
            if (current.title != placeholderTitle && current.title != knownAutoTitle) {
                return 0
            }
            conversations[id] = current.copy(title = newTitle, updatedAt = updatedAt)
            return 1
        }

        override suspend fun delete(id: String) {
            conversations.remove(id)
        }

        fun simulateManualTitleBeforeNextWrite(id: String, title: String) {
            beforeNextWrite = {
                conversations[id]?.let { existing ->
                    conversations[id] = existing.copy(title = title)
                }
            }
        }
    }

    private class FakeMessageDao : MessageDao {
        private val messages = LinkedHashMap<String, MessageEntity>()

        override suspend fun getByConversationId(convId: String): List<MessageEntity> {
            return messages.values
                .filter { it.conversationId == convId }
                .sortedBy { it.createdAt }
        }

        override suspend fun getByConversationIdAfter(convId: String, createdAfter: Long): List<MessageEntity> {
            return messages.values
                .filter { it.conversationId == convId && it.createdAt > createdAfter }
                .sortedBy { it.createdAt }
        }

        override suspend fun getByConversationIdBefore(
            convId: String,
            beforeCreatedAt: Long,
            limit: Int
        ): List<MessageEntity> {
            return messages.values
                .filter { it.conversationId == convId && it.createdAt < beforeCreatedAt }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }

        override suspend fun getBefore(beforeCreatedAt: Long, limit: Int): List<MessageEntity> {
            return messages.values
                .filter { it.createdAt < beforeCreatedAt }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }

        override suspend fun insert(entity: MessageEntity) {
            messages[entity.id] = entity
        }

        override suspend fun delete(id: String) {
            messages.remove(id)
        }

        override suspend fun countByConversationId(convId: String): Int {
            return messages.values.count { it.conversationId == convId }
        }

        override suspend fun deleteByConversationId(convId: String) {
            messages.entries.removeIf { it.value.conversationId == convId }
        }
    }

    private class FakePersonaDao : PersonaDao {
        private val personas = LinkedHashMap<String, PersonaEntity>()

        override suspend fun getAll(): List<PersonaEntity> {
            return personas.values.sortedWith(compareByDescending<PersonaEntity> { it.isDefault }.thenBy { it.name })
        }

        override suspend fun getById(id: String): PersonaEntity? {
            return personas[id]
        }

        override suspend fun getDefault(): PersonaEntity? {
            return personas.values.firstOrNull { it.isDefault == 1 }
        }

        override suspend fun insert(entity: PersonaEntity) {
            personas[entity.id] = entity
        }

        override suspend fun delete(id: String) {
            personas.remove(id)
        }
    }

    private class FakeMemoryDao : MemoryDao {
        val memories = LinkedHashMap<String, MemoryEntity>()

        override suspend fun getActiveMemories(): List<MemoryEntity> {
            return memories.values.filter { it.status == MemoryStatus.ACTIVE.name }
        }

        override suspend fun getPendingMemories(): List<MemoryEntity> {
            return memories.values.filter { it.status == MemoryStatus.PENDING.name }
        }

        override suspend fun getAllMemories(): List<MemoryEntity> {
            return memories.values.toList()
        }

        override suspend fun getById(id: String): MemoryEntity? {
            return memories[id]
        }

        override suspend fun getByType(type: String): List<MemoryEntity> {
            return memories.values.filter { it.type == type && it.status == MemoryStatus.ACTIVE.name }
        }

        override suspend fun insert(entity: MemoryEntity) {
            memories[entity.id] = entity
        }

        override suspend fun update(entity: MemoryEntity) {
            memories[entity.id] = entity
        }

        override suspend fun delete(id: String) {
            memories.remove(id)
        }

        override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
            memories[id]?.let { existing ->
                memories[id] = existing.copy(status = status, updatedAt = updatedAt)
            }
        }
    }

    private class FakeMemoryTombstoneDao : MemoryTombstoneDao {
        private val tombstones = LinkedHashMap<String, MemoryTombstoneEntity>()

        override suspend fun getByFingerprintAndType(fingerprint: String, memoryType: String): MemoryTombstoneEntity? {
            return tombstones["$memoryType:$fingerprint"]
        }

        override suspend fun insert(entity: MemoryTombstoneEntity) {
            tombstones["${entity.memoryType}:${entity.contentFingerprint}"] = entity
        }

        fun tombstone(content: String, type: MemoryType) {
            val fingerprint = java.security.MessageDigest.getInstance("MD5")
                .digest(content.trim().lowercase().toByteArray())
                .joinToString("") { "%02x".format(it) }
            tombstones["${type.name}:$fingerprint"] = MemoryTombstoneEntity(
                id = "t-$fingerprint",
                memoryType = type.name,
                contentFingerprint = fingerprint
            )
        }
    }
}
