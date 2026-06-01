package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Conversation
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryExtractionResult
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.util.AppLogger

interface MemoryExtractionStore {
    suspend fun getActiveMemories(): List<Memory>
    suspend fun isTombstoned(content: String, type: MemoryType): Boolean
    suspend fun insert(memory: Memory)
    suspend fun getById(id: String): Memory?
    suspend fun update(memory: Memory)
}

class MemoryExtractionSaver(
    private val engine: MemoryEngine,
    private val store: MemoryExtractionStore
) {
    suspend fun extractAndSave(
        conversation: Conversation,
        messages: List<ChatMessage>
    ): MemoryExtractionResult {
        if (!conversation.generateMemory) {
            AppLogger.i("MemoryExtraction", "Skipped: generateMemory=false")
            return MemoryExtractionResult()
        }

        val existing = store.getActiveMemories()
        val modelResult = filterPersonaInstructions(engine.extractMemories(messages, existing))
        val fallbackCandidates = if (modelResult.newMemories.isEmpty() && modelResult.updates.isEmpty()) {
            explicitMemoryCandidates(messages)
        } else {
            emptyList()
        }
        val result = if (fallbackCandidates.isNotEmpty()) {
            AppLogger.i("MemoryExtraction", "Using explicit remember fallback: ${fallbackCandidates.size}")
            MemoryExtractionResult(newMemories = fallbackCandidates)
        } else {
            modelResult
        }
        AppLogger.i("MemoryExtraction", "Extracted: new=${result.newMemories.size}, updates=${result.updates.size}")

        val fallbackSourceIds = messages.map { it.id }
        val activeMemories = existing.toMutableList()
        val seenNewContents = existing.map { memoryKey(it.type, it.content) }.toMutableSet()
        result.newMemories.forEach { candidate ->
            val content = candidate.content.trim()
            if (content.isBlank()) return@forEach
            if (PersonaInstructionDetector.looksLikePersonaMemory(content)) {
                AppLogger.d("MemoryExtraction", "Skipping persona instruction: ${content.take(40)}")
                return@forEach
            }
            if (store.isTombstoned(content, candidate.type)) {
                AppLogger.d("MemoryExtraction", "Skipping tombstoned: ${content.take(40)}")
                return@forEach
            }
            if (!seenNewContents.add(memoryKey(candidate.type, content))) {
                AppLogger.d("MemoryExtraction", "Skipping duplicate: ${content.take(40)}")
                return@forEach
            }
            val merge = findSimilarMemory(candidate.type, content, activeMemories)
            if (merge != null) {
                if (merge.memory.userEdited) {
                    AppLogger.d("MemoryExtraction", "Skipping user-edited merge: ${merge.memory.id}")
                    return@forEach
                }
                if (merge.newContent == null) {
                    AppLogger.d("MemoryExtraction", "Skipping equivalent memory: ${content.take(40)}")
                    return@forEach
                }
                val updated = merge.memory.copy(
                    content = merge.newContent,
                    updatedAt = System.currentTimeMillis()
                )
                store.update(updated)
                activeMemories.removeAll { it.id == updated.id }
                activeMemories += updated
                seenNewContents += memoryKey(updated.type, updated.content)
                AppLogger.d("MemoryExtraction", "Merged similar memory: ${updated.id}")
                return@forEach
            }

            val memory = Memory(
                type = candidate.type,
                content = content,
                status = candidate.statusSuggestion,
                importance = candidate.importance,
                confidence = candidate.confidence,
                sourceMessageIds = candidate.sourceMessageIds.ifEmpty { fallbackSourceIds },
                sourceConversationId = conversation.id
            )
            store.insert(memory)
            activeMemories += memory
        }

        result.updates.forEach { update ->
            val existingMemory = store.getById(update.targetMemoryId)
            if (existingMemory != null) {
                if (existingMemory.userEdited) {
                    AppLogger.d("MemoryExtraction", "Skipping user-edited update: ${existingMemory.id}")
                    return@forEach
                }
                store.update(
                    existingMemory.copy(
                        content = update.newContent,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return result
    }

    private fun explicitMemoryCandidates(messages: List<ChatMessage>): List<com.memorychat.app.domain.model.MemoryCandidate> {
        return messages
            .filter { it.role == "user" }
            .mapNotNull { ExplicitMemorySignal.extractContent(it.content) }
            .filterNot { PersonaInstructionDetector.looksLikePersonaMemory(it) }
            .filterNot { isSensitiveMemory(it) }
            .map {
                com.memorychat.app.domain.model.MemoryCandidate(
                    type = classifyExplicitMemory(it),
                    content = it,
                    importance = 5,
                    confidence = 0.95f,
                    statusSuggestion = com.memorychat.app.domain.model.MemoryStatus.ACTIVE,
                    reason = "explicit remember command"
                )
            }
    }

    private fun filterPersonaInstructions(result: MemoryExtractionResult): MemoryExtractionResult {
        return result.copy(
            newMemories = result.newMemories.filterNot {
                PersonaInstructionDetector.looksLikePersonaMemory(it.content)
            },
            updates = result.updates.filterNot {
                PersonaInstructionDetector.looksLikePersonaMemory(it.newContent)
            }
        )
    }

    private fun classifyExplicitMemory(content: String): MemoryType {
        val lower = content.lowercase()
        return when {
            lower.contains("项目") || lower.contains("app") || lower.contains("android") ||
                lower.contains("开发") || lower.contains("代码") || lower.contains("prd") ||
                lower.contains("第一阶段") || lower.contains("产品") -> MemoryType.PROJECT
            lower.contains("偏好") || lower.contains("喜欢") || lower.contains("习惯") ||
                lower.contains("prefer") || lower.contains("尽量") || lower.contains("希望你") -> MemoryType.PREFERENCE
            lower.contains("我叫") || lower.contains("我是") || lower.contains("我的名字") ||
                lower.contains("我在") || lower.contains("我住") -> MemoryType.PROFILE
            else -> MemoryType.SUMMARY
        }
    }

    private fun isSensitiveMemory(content: String): Boolean {
        val lower = content.lowercase()
        val obviousSecret = listOf("api key", "apikey", "password", "secret", "sk-", "密码", "密钥")
            .any { lower.contains(it) }
        val credentialToken = Regex("\\b(api|access|refresh|auth|bearer)\\s+token\\b").containsMatchIn(lower)
        return obviousSecret || credentialToken
    }

    private fun memoryFingerprint(content: String): String {
        return content
            .lowercase()
            .replace(Regex("[\\p{Punct}，。！？；：“”‘’、（）【】《》]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun memoryKey(type: MemoryType, content: String): String {
        return "${type.name}:${memoryFingerprint(content)}"
    }

    private fun findSimilarMemory(
        type: MemoryType,
        content: String,
        existing: List<Memory>
    ): MemoryMerge? {
        val candidateCanonical = semanticFingerprint(content)
        if (candidateCanonical.isBlank()) return null
        return existing
            .asSequence()
            .filter { it.type == type }
            .mapNotNull { memory ->
                val existingCanonical = semanticFingerprint(memory.content)
                when {
                    existingCanonical.isBlank() -> null
                    existingCanonical == candidateCanonical -> MemoryMerge(memory, null)
                    isSafeExpansion(existingCanonical, candidateCanonical) ->
                        if (candidateCanonical.length > existingCanonical.length) {
                            MemoryMerge(memory, content)
                        } else {
                            MemoryMerge(memory, null)
                        }
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun isSafeExpansion(existingCanonical: String, candidateCanonical: String): Boolean {
        val shorter = minOf(existingCanonical.length, candidateCanonical.length)
        if (shorter < 6) return false
        return existingCanonical.contains(candidateCanonical) || candidateCanonical.contains(existingCanonical)
    }

    private fun semanticFingerprint(content: String): String {
        return content
            .lowercase()
            .replace(Regex("[\\p{Punct}，。！？；：“”‘’、（）【】《》]"), "")
            .replace(Regex("\\s+"), "")
            .replace("型号是", "")
            .replace("型号为", "")
            .replace("型号", "")
            .replace("编号是", "")
            .replace("编号为", "")
            .replace("编号", "")
            .replace("是", "")
            .replace("为", "")
            .replace("的", "")
            .trim()
    }

    private data class MemoryMerge(
        val memory: Memory,
        val newContent: String?
    )
}

interface ConversationMessageStore {
    suspend fun saveMessage(message: ChatMessage)
}

class ChatTurnErrorPersister(private val store: ConversationMessageStore) {
    suspend fun persistAssistantError(conversationId: String, message: String?): ChatMessage {
        val content = "Error: ${message?.takeIf { it.isNotBlank() } ?: "unknown stream failure"}"
        val errorMessage = ChatMessage(
            conversationId = conversationId,
            role = "assistant",
            content = content
        )
        store.saveMessage(errorMessage)
        return errorMessage
    }
}
