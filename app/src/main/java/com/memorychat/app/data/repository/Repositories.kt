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
    suspend fun saveMessage(msg: ChatMessage) = messageDao.insert(msg.toEntity())
    suspend fun deleteMessage(id: String) = messageDao.delete(id)
    suspend fun getMessageCount(convId: String) = messageDao.countByConversationId(convId)
}

class MemoryRepository(private val memoryDao: MemoryDao, private val tombstoneDao: MemoryTombstoneDao) {
    private val gson = Gson()

    private fun MemoryEntity.toDomain(): Memory {
        val ids: List<String> = try {
            gson.fromJson(sourceMessageIds, object : TypeToken<List<String>>() {}.type)
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
    suspend fun disable(memoryId: String) = memoryDao.updateStatus(memoryId, MemoryStatus.DISABLED.name)
    suspend fun delete(memoryId: String) = memoryDao.updateStatus(memoryId, MemoryStatus.DELETED.name)

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
        return tombstoneDao.getByFingerprint(fingerprint) != null
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

class PersonaRepository(private val personaDao: PersonaDao) {
    private fun PersonaEntity.toDomain(): Persona {
        val rules: List<String> = try {
            gson.fromJson(behaviorRulesJson, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) { emptyList() }
        val bounds: List<String> = try {
            gson.fromJson(boundariesJson, object : TypeToken<List<String>>() {}.type)
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
    suspend fun savePersona(persona: Persona) = personaDao.insert(persona.toEntity())
    suspend fun deletePersona(id: String) = personaDao.delete(id)
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
            "personas" to personas
        ))
    }

    suspend fun importPersonasJson(json: String): ImportResult {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("personas") ?: return ImportResult(0, 0, listOf("No personas array"))
            var imported = 0
            arr.forEach { item ->
                try {
                    val o = item.asJsonObject
                    val rules: List<String> = try {
                        gson.fromJson(o.getAsJsonArray("behavior_rules"), object : TypeToken<List<String>>() {}.type)
                    } catch (_: Exception) { emptyList() }
                    val bounds: List<String> = try {
                        gson.fromJson(o.getAsJsonArray("boundaries"), object : TypeToken<List<String>>() {}.type)
                    } catch (_: Exception) { emptyList() }

                    val persona = Persona(
                        id = o.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        name = o.get("name")?.asString ?: "Unnamed",
                        description = o.get("description")?.asString,
                        role = o.get("role")?.asString,
                        tone = o.get("tone")?.asString,
                        behaviorRules = rules,
                        boundaries = bounds,
                        proactivity = o.get("proactivity")?.asInt ?: 3,
                        isDefault = o.get("is_default")?.asBoolean ?: false
                    )
                    personaRepository.savePersona(persona)
                    imported++
                } catch (_: Exception) {}
            }
            ImportResult(imported, 0)
        } catch (e: Exception) {
            ImportResult(0, 0, listOf(e.message ?: "Parse error"))
        }
    }
}

