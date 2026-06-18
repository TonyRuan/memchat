package com.memorychat.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.memorychat.app.data.local.db.dao.*
import com.memorychat.app.data.local.db.entity.*
import com.memorychat.app.domain.engine.ConversationTitleGenerator
import com.memorychat.app.domain.model.*
import java.security.MessageDigest

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    private fun ConversationEntity.toDomain() = Conversation(id, title, personaId, useMemory == 1, generateMemory == 1, createdAt, updatedAt)
    private fun Conversation.toEntity() = ConversationEntity(id, title, personaId, if (useMemory) 1 else 0, if (generateMemory) 1 else 0, createdAt, updatedAt)
    private fun MessageEntity.toDomain() = ChatMessage(id, conversationId, role, content, createdAt)
    private fun ChatMessage.toEntity() = MessageEntity(id, conversationId, role, content, createdAt)

    suspend fun getAllConversations() = conversationDao.getAll().map { it.toDomain() }
    suspend fun getConversation(id: String) = conversationDao.getById(id)?.toDomain()
    suspend fun saveConversation(conv: Conversation) = conversationDao.insert(conv.toEntity())
    suspend fun updateConversationTitleIfAuto(
        conversationId: String,
        newTitle: String,
        knownAutoTitle: String
    ): Conversation? {
        val title = newTitle.trim()
        if (title.isBlank()) return getConversation(conversationId)
        conversationDao.updateTitleIfCurrent(
            id = conversationId,
            newTitle = title,
            updatedAt = System.currentTimeMillis(),
            placeholderTitle = ConversationTitleGenerator.PLACEHOLDER_TITLE,
            knownAutoTitle = knownAutoTitle.trim()
        )
        return getConversation(conversationId)
    }

    suspend fun deleteConversation(id: String) {
        messageDao.deleteByConversationId(id)
        conversationDao.delete(id)
    }

    suspend fun getMessages(convId: String) = messageDao.getByConversationId(convId).map { it.toDomain() }
    suspend fun getMessagesAfter(convId: String, createdAfter: Long) =
        messageDao.getByConversationIdAfter(convId, createdAfter).map { it.toDomain() }
    suspend fun saveMessage(msg: ChatMessage) = messageDao.insert(msg.toEntity())
    suspend fun deleteMessage(id: String) = messageDao.delete(id)
    suspend fun getMessageCount(convId: String) = messageDao.countByConversationId(convId)
}

class MemoryRepository(private val memoryDao: MemoryDao, private val tombstoneDao: MemoryTombstoneDao) {
    private val gson = Gson()

    private fun MemoryEntity.toDomain(): Memory {
        val ids: List<String> = try {
            gson.fromJson<List<String>>(sourceMessageIds, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        return Memory(id, MemoryType.valueOf(type), content, MemoryStatus.valueOf(status),
            importance, confidence, ids, sourceConversationId, createdAt, updatedAt, lastUsedAt, userEdited == 1)
    }

    private fun Memory.toEntity(): MemoryEntity {
        return MemoryEntity(id, type.name, content, status.name, importance, confidence,
            gson.toJson(sourceMessageIds), sourceConversationId, createdAt, updatedAt, lastUsedAt, if (userEdited) 1 else 0)
    }

    suspend fun getActiveMemories() = memoryDao.getActiveMemories().map { it.toDomain() }
    suspend fun getPendingMemories() = memoryDao.getPendingMemories().map { it.toDomain() }
    suspend fun getAllMemories() = memoryDao.getAllMemories().map { it.toDomain() }
    suspend fun getById(id: String) = memoryDao.getById(id)?.toDomain()
    suspend fun getByType(type: MemoryType) = memoryDao.getByType(type.name).map { it.toDomain() }
    suspend fun insert(memory: Memory) = memoryDao.insert(memory.toEntity())
    suspend fun update(memory: Memory) = memoryDao.update(memory.toEntity())
    suspend fun disable(memoryId: String) {
        val memory = getById(memoryId)
        memoryDao.updateStatus(memoryId, MemoryStatus.DISABLED.name)
        if (memory != null) {
            addTombstone(memory, "user_disabled")
        }
    }
    suspend fun delete(memoryId: String) {
        val memory = getById(memoryId)
        memoryDao.updateStatus(memoryId, MemoryStatus.DELETED.name)
        if (memory != null) {
            addTombstone(memory, "user_deleted")
        }
    }

    suspend fun addTombstone(memory: Memory, reason: String? = null) {
        val fingerprint = md5(memory.content.trim().lowercase())
        tombstoneDao.insert(MemoryTombstoneEntity(
            id = java.util.UUID.randomUUID().toString(),
            memoryType = memory.type.name,
            contentFingerprint = fingerprint,
            deletedReason = reason
        ))
    }

    suspend fun isTombstoned(content: String, type: MemoryType): Boolean {
        val fingerprint = md5(content.trim().lowercase())
        return tombstoneDao.getByFingerprintAndType(fingerprint, type.name) != null
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

class PersonaRepository(private val personaDao: PersonaDao) {
    private fun PersonaEntity.toDomain(): Persona {
        val rules: List<String> = try {
            gson.fromJson<List<String>>(behaviorRulesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val bounds: List<String> = try {
            gson.fromJson<List<String>>(boundariesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val expertise: List<String> = parseStringList(expertiseJson)
        val toolPolicy: List<String> = parseStringList(toolPolicyJson)
        val memoryPolicy: List<String> = parseStringList(memoryPolicyJson)
        val examples: List<String> = parseStringList(exampleDialoguesJson)
        return Persona(
            id = id,
            name = name,
            avatar = avatar,
            description = description,
            role = role,
            mission = mission,
            expertise = expertise,
            tone = tone,
            communicationStyle = communicationStyle,
            behaviorRules = rules,
            boundaries = bounds,
            toolPolicy = toolPolicy,
            memoryPolicy = memoryPolicy,
            exampleDialogues = examples,
            proactivity = proactivity,
            isDefault = isDefault == 1,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Persona.toEntity(): PersonaEntity {
        return PersonaEntity(
            id = id,
            name = name,
            avatar = avatar,
            description = description,
            role = role,
            mission = mission,
            expertiseJson = gson.toJson(expertise),
            tone = tone,
            communicationStyle = communicationStyle,
            behaviorRulesJson = gson.toJson(behaviorRules),
            boundariesJson = gson.toJson(boundaries),
            toolPolicyJson = gson.toJson(toolPolicy),
            memoryPolicyJson = gson.toJson(memoryPolicy),
            exampleDialoguesJson = gson.toJson(exampleDialogues),
            proactivity = proactivity,
            isDefault = if (isDefault) 1 else 0,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private val gson = Gson()

    private fun parseStringList(json: String?): List<String> {
        return try {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDefaultPersona() = personaDao.getDefault()?.toDomain()
    suspend fun getPersona(id: String) = personaDao.getById(id)?.toDomain()
    suspend fun listPersonas() = personaDao.getAll().map { it.toDomain() }
    suspend fun savePersona(persona: Persona) {
        if (persona.isDefault) {
            listPersonas()
                .filter { it.id != persona.id && it.isDefault }
                .forEach { existing ->
                    personaDao.insert(existing.copy(isDefault = false).toEntity())
                }
        }
        personaDao.insert(persona.toEntity())
    }
    suspend fun deletePersona(id: String) = personaDao.delete(id)

    suspend fun getOrCreateDefaultPersona(): Persona {
        getDefaultPersona()?.let { existing ->
            val upgraded = upgradeBundledDefaultPersonaIfNeeded(existing)
            return upgraded ?: existing
        }
        val defaultPersona = createDefaultPersona()
        savePersona(defaultPersona)
        return getDefaultPersona() ?: defaultPersona
    }

    private suspend fun upgradeBundledDefaultPersonaIfNeeded(existing: Persona): Persona? {
        if (!isUpgradeableBundledDefaultPersona(existing)) return null
        val upgraded = createDefaultPersona().copy(
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        savePersona(upgraded)
        return upgraded
    }

    private fun isUpgradeableBundledDefaultPersona(persona: Persona): Boolean {
        if (persona.id != DEFAULT_PERSONA_ID || !persona.isDefault) return false
        if (persona.name == createDefaultPersona().name) return false
        if (persona.name !in BUNDLED_DEFAULT_PERSONA_NAMES) return false
        return persona.createdAt == persona.updatedAt
    }

    companion object {
        const val DEFAULT_PERSONA_ID = "persona_default"
        private val BUNDLED_DEFAULT_PERSONA_NAMES = setOf("技术伙伴")

        fun createDefaultPersona(): Persona = Persona(
            id = DEFAULT_PERSONA_ID,
            name = "求真助手",
            description = "实事求是、重视证据和验证边界的默认聊天助手",
            role = "实事求是的聊天助手",
            mission = "帮助用户把问题说清楚，把事实、推测和建议分开，并给出可验证的下一步",
            expertise = listOf("信息核查", "问题拆解", "事实与推测区分", "工程和产品讨论", "日常决策支持"),
            tone = "冷静、直接、克制、可信",
            communicationStyle = "先给结论和把握程度；区分已知事实、合理推测和待验证项；必要时给出验证方法",
            behaviorRules = listOf(
                "先说明结论和把握程度",
                "事实、推测、建议分开表达",
                "不确定时直接说明不知道或需要验证",
                "发现用户前提可能有误时温和指出并给出核查路径",
                "涉及时间、价格、法规、医学、金融或外部事实时优先验证"
            ),
            boundaries = listOf(
                "不为了迎合用户编造事实、来源或能力",
                "不把未经验证的信息说成确定结论",
                "不替用户做高风险决策，只给依据、选项和风险"
            ),
            toolPolicy = listOf(
                "需要实时信息、外部资料或真实验证时主动使用可用工具",
                "本地项目状态可验证时优先读取真实文件、日志、数据库或测试结果",
                "工具不可用或验证失败时明确说明验证边界"
            ),
            memoryPolicy = listOf(
                "助手人格设置只写入 Persona，不写入长期记忆",
                "只把稳定的用户资料、偏好、项目事实和明确要求记住的内容写入 Memory",
                "不把临时情绪、一次性指令或未确认推测写入 Memory"
            ),
            exampleDialogues = listOf(
                "用户：这个方案靠谱吗？\n助手：结论：目前只能说有条件可行。已知依据是现有需求和代码路径，主要风险在验证链路。建议先用最小闭环验证关键假设。",
                "用户：你确定吗？\n助手：我不能确定到 100%。现在能确认的是这些事实；还需要验证的是这些点。验证完之前，我不会把它说成定论。",
                "用户：你是谁？\n助手：我是求真助手，会尽量把事实、推测和建议分开说清楚；不确定时会直接说明，并给出可验证的下一步。"
            ),
            proactivity = 4,
            isDefault = true
        )
    }
}

class ExportImportService(
    private val memoryRepository: MemoryRepository,
    private val personaRepository: PersonaRepository
) {
    private val gson = Gson()

    suspend fun exportMemoriesJson(): String {
        val memories = memoryRepository.getAllMemories().filter { it.status != MemoryStatus.DELETED }
        return gson.toJson(mapOf(
            "version" to "1.0",
            "exported_at" to java.time.Instant.now().toString(),
            "app" to "MemoryChat",
            "memories" to memories
        ))
    }

    suspend fun exportMemoriesMarkdown(): String {
        val memories = memoryRepository.getAllMemories().filter { it.status != MemoryStatus.DELETED }
        val sb = StringBuilder("# Long-term Memories\n\nExported at: ${java.time.Instant.now()}\n\n")

        memories.groupBy { it.type }.forEach { (type, list) ->
            sb.appendLine("## ${type.name}")
            list.forEach { sb.appendLine("- ${it.content}") }
            sb.appendLine()
        }
        return sb.toString()
    }

    suspend fun importMemoriesJson(json: String): ImportResult {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("memories") ?: return ImportResult(0, 0, listOf("No memories array"))
            var imported = 0
            var skipped = 0
            val errors = mutableListOf<String>()

            arr.forEach { item ->
                try {
                    val o = item.asJsonObject
                    val content = o.get("content")?.asString?.trim().orEmpty()
                    if (content.isBlank()) {
                        skipped++
                        errors.add("Skipped memory with empty content")
                        return@forEach
                    }
                    val type = try {
                        MemoryType.valueOf(o.get("type")?.asString?.uppercase() ?: "PROFILE")
                    } catch (e: Exception) {
                        skipped++
                        errors.add("Invalid memory type: ${o.get("type")}")
                        return@forEach
                    }
                    val status = try {
                        MemoryStatus.valueOf(o.get("status")?.asString?.uppercase() ?: "ACTIVE")
                    } catch (e: Exception) {
                        skipped++
                        errors.add("Invalid memory status: ${o.get("status")}")
                        return@forEach
                    }

                    if (memoryRepository.isTombstoned(content, type)) {
                        skipped++
                        return@forEach
                    }

                    val memory = Memory(
                        id = o.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        type = type,
                        content = content,
                        status = status,
                        importance = o.get("importance")?.asInt ?: 3,
                        confidence = o.get("confidence")?.asFloat ?: 0.8f
                    )
                    memoryRepository.insert(memory)
                    imported++
                } catch (e: Exception) {
                    errors.add(e.message ?: "Unknown error")
                }
            }

            ImportResult(imported, skipped, errors)
        } catch (e: Exception) {
            ImportResult(0, 0, listOf(e.message ?: "Parse error"))
        }
    }

    suspend fun exportPersonasJson(): String {
        val personas = personaRepository.listPersonas()
        return gson.toJson(mapOf(
            "version" to "1.0",
            "exported_at" to java.time.Instant.now().toString(),
            "personas" to personas.map { it.toExportJson() }
        ))
    }

    suspend fun importPersonasJson(json: String): ImportResult {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("personas") ?: return ImportResult(0, 0, listOf("No personas array"))
            var imported = 0
            var skipped = 0
            val errors = mutableListOf<String>()
            arr.forEach { item ->
                try {
                    val o = item.asJsonObject
                    val name = o.stringValue("name")?.trim().orEmpty()
                    if (name.isBlank()) {
                        skipped++
                        errors += "Skipped persona with empty persona name"
                        return@forEach
                    }
                    val persona = Persona(
                        id = o.stringValue("id") ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        avatar = o.stringValue("avatar"),
                        description = o.stringValue("description"),
                        role = o.stringValue("role"),
                        mission = o.stringValue("mission"),
                        expertise = o.stringListValue("expertise"),
                        tone = o.stringValue("tone"),
                        communicationStyle = o.stringValue("communication_style", "communicationStyle"),
                        behaviorRules = o.stringListValue("behavior_rules", "behaviorRules"),
                        boundaries = o.stringListValue("boundaries"),
                        toolPolicy = o.stringListValue("tool_policy", "toolPolicy"),
                        memoryPolicy = o.stringListValue("memory_policy", "memoryPolicy"),
                        exampleDialogues = o.stringListValue("example_dialogues", "exampleDialogues"),
                        proactivity = o.intValue("proactivity") ?: 3,
                        isDefault = o.booleanValue("is_default", "isDefault") ?: false,
                        createdAt = o.longValue("created_at", "createdAt") ?: System.currentTimeMillis(),
                        updatedAt = o.longValue("updated_at", "updatedAt") ?: System.currentTimeMillis()
                    )
                    personaRepository.savePersona(persona)
                    imported++
                } catch (e: Exception) {
                    skipped++
                    errors += e.message ?: "Invalid persona row"
                }
            }
            ImportResult(imported, skipped, errors)
        } catch (e: Exception) {
            ImportResult(0, 0, listOf(e.message ?: "Parse error"))
        }
    }

    private fun Persona.toExportJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "name" to name,
        "avatar" to avatar,
        "description" to description,
        "role" to role,
        "mission" to mission,
        "expertise" to expertise,
        "tone" to tone,
        "communication_style" to communicationStyle,
        "behavior_rules" to behaviorRules,
        "boundaries" to boundaries,
        "tool_policy" to toolPolicy,
        "memory_policy" to memoryPolicy,
        "example_dialogues" to exampleDialogues,
        "proactivity" to proactivity,
        "is_default" to isDefault,
        "created_at" to createdAt,
        "updated_at" to updatedAt
    )

    private fun com.google.gson.JsonObject.value(vararg names: String): com.google.gson.JsonElement? {
        for (name in names) {
            val element = get(name)
            if (element != null && !element.isJsonNull) return element
        }
        return null
    }

    private fun com.google.gson.JsonObject.stringValue(vararg names: String): String? {
        return value(*names)?.asString
    }

    private fun com.google.gson.JsonObject.intValue(vararg names: String): Int? {
        return try {
            value(*names)?.asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun com.google.gson.JsonObject.longValue(vararg names: String): Long? {
        return try {
            value(*names)?.asLong
        } catch (_: Exception) {
            null
        }
    }

    private fun com.google.gson.JsonObject.booleanValue(vararg names: String): Boolean? {
        return try {
            value(*names)?.asBoolean
        } catch (_: Exception) {
            null
        }
    }

    private fun com.google.gson.JsonObject.stringListValue(vararg names: String): List<String> {
        val parsed: List<String>? = try {
            gson.fromJson(value(*names), object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) {
            null
        }
        return parsed ?: emptyList()
    }
}

