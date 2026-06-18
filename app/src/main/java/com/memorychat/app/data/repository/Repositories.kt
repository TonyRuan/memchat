package com.memorychat.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.memorychat.app.data.local.db.dao.*
import com.memorychat.app.data.local.db.entity.*
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
    suspend fun deleteConversation(id: String) {
        messageDao.deleteByConversationId(id)
        conversationDao.delete(id)
    }

    suspend fun getMessages(convId: String) = messageDao.getByConversationId(convId).map { it.toDomain() }
    suspend fun getMessagesAfter(convId: String, createdAfter: Long) =
        messageDao.getByConversationIdAfter(convId, createdAfter).map { it.toDomain() }
    suspend fun searchMessages(
        query: String,
        scope: HistorySearchScope,
        currentConversationId: String,
        beforeCreatedAt: Long,
        limit: Int
    ): List<ConversationHistoryMatch> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        val boundedLimit = limit.coerceIn(1, 5)
        val candidateLimit = (boundedLimit * 80).coerceIn(80, 500)
        val candidates = when (scope) {
            HistorySearchScope.CURRENT -> messageDao.getByConversationIdBefore(
                currentConversationId,
                beforeCreatedAt,
                candidateLimit
            )
            HistorySearchScope.ALL -> messageDao.getBefore(beforeCreatedAt, candidateLimit)
        }
        val titleByConversationId = conversationDao.getAll().associate { it.id to it.title }
        return rankHistoryMatches(cleanQuery, candidates, titleByConversationId, boundedLimit)
    }

    suspend fun saveMessage(msg: ChatMessage) = messageDao.insert(msg.toEntity())
    suspend fun deleteMessage(id: String) = messageDao.delete(id)
    suspend fun getMessageCount(convId: String) = messageDao.countByConversationId(convId)

    private fun rankHistoryMatches(
        query: String,
        candidates: List<MessageEntity>,
        titleByConversationId: Map<String, String>,
        limit: Int
    ): List<ConversationHistoryMatch> {
        val tokens = tokenizeHistoryQuery(query)
        if (tokens.isEmpty()) return emptyList()
        val normalizedPhrase = normalizeHistoryText(query)
        return candidates.mapNotNull { message ->
            val title = titleByConversationId[message.conversationId].orEmpty()
            val haystack = normalizeHistoryText("$title ${message.content}")
            val matchedTokens = tokens.filter { haystack.contains(it) }.distinct()
            if (matchedTokens.isEmpty()) return@mapNotNull null
            val phraseScore = if (normalizedPhrase.length >= 3 && haystack.contains(normalizedPhrase)) 5 else 0
            val tokenScore = matchedTokens.fold(0) { score, token ->
                score + when {
                    token.any { it in 'a'..'z' || it in '0'..'9' } && token.length >= 4 -> 5
                    token.length >= 4 -> 4
                    else -> 2
                }
            }
            ConversationHistoryMatch(
                conversationId = message.conversationId,
                conversationTitle = title.ifBlank { "未命名会话" },
                messageId = message.id,
                role = message.role,
                content = message.content,
                createdAt = message.createdAt,
                score = tokenScore + phraseScore,
                reason = "query match: ${matchedTokens.take(6).joinToString(", ")}"
            )
        }
            .sortedWith(
                compareByDescending<ConversationHistoryMatch> { it.score }
                    .thenByDescending { it.createdAt }
            )
            .take(limit)
    }

    private fun tokenizeHistoryQuery(query: String): List<String> {
        val normalized = normalizeHistoryText(query)
        val tokens = linkedSetOf<String>()
        Regex("[a-z0-9_]{2,}").findAll(normalized).forEach { match ->
            tokens += match.value
        }
        Regex("[\\u4E00-\\u9FFF]{2,}").findAll(normalized).forEach { match ->
            val run = match.value
            if (run.length <= 6) tokens += run
            tokens.addAll(run.windowed(2))
            if (run.length >= 4) tokens.addAll(run.windowed(4))
        }
        val stopTokens = setOf("之前", "上次", "那个", "这个", "怎么", "一下", "我们", "你们", "历史", "会话")
        return tokens
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopTokens }
            .distinct()
    }

    private fun normalizeHistoryText(text: String): String {
        return text.lowercase().replace(Regex("\\s+"), " ").trim()
    }
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
        return Persona(id, name, avatar, description, role, tone, rules, bounds, proactivity, isDefault == 1, createdAt, updatedAt)
    }

    private fun Persona.toEntity(): PersonaEntity {
        return PersonaEntity(id, name, avatar, description, role, tone,
            gson.toJson(behaviorRules), gson.toJson(boundaries), proactivity, if (isDefault) 1 else 0, createdAt, updatedAt)
    }

    private val gson = Gson()

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
        getDefaultPersona()?.let { return it }
        val defaultPersona = createDefaultPersona()
        savePersona(defaultPersona)
        return getDefaultPersona() ?: defaultPersona
    }

    companion object {
        const val DEFAULT_PERSONA_ID = "persona_default"

        fun createDefaultPersona(): Persona = Persona(
            id = DEFAULT_PERSONA_ID,
            name = "技术伙伴",
            description = "适合产品讨论、技术协作的默认人格",
            role = "技术协作者",
            tone = "直接、清晰、有见地",
            behaviorRules = listOf("漏指令立即补全", "结论先行再展开", "必要时引用参考", "不确定时直接说明"),
            boundaries = listOf("不要假装知道不确定的信息", "不要在无偏好的时候硬编偏好"),
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
                        tone = o.stringValue("tone"),
                        behaviorRules = o.stringListValue("behavior_rules", "behaviorRules"),
                        boundaries = o.stringListValue("boundaries"),
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
        "tone" to tone,
        "behavior_rules" to behaviorRules,
        "boundaries" to boundaries,
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

