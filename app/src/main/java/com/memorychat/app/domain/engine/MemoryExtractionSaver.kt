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
        val modelResult = engine.extractMemories(messages, existing)
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
        val seenNewContents = existing.map { memoryFingerprint(it.content) }.toMutableSet()
        result.newMemories.forEach { candidate ->
            val content = candidate.content.trim()
            if (content.isBlank()) return@forEach
            if (store.isTombstoned(content, candidate.type)) {
                AppLogger.d("MemoryExtraction", "Skipping tombstoned: ${content.take(40)}")
                return@forEach
            }
            if (!seenNewContents.add(memoryFingerprint(content))) {
                AppLogger.d("MemoryExtraction", "Skipping duplicate: ${content.take(40)}")
                return@forEach
            }

            store.insert(
                Memory(
                    type = candidate.type,
                    content = content,
                    status = candidate.statusSuggestion,
                    importance = candidate.importance,
                    confidence = candidate.confidence,
                    sourceMessageIds = candidate.sourceMessageIds.ifEmpty { fallbackSourceIds },
                    sourceConversationId = conversation.id
                )
            )
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
            .mapNotNull { extractExplicitMemoryContent(it.content) }
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

    private fun extractExplicitMemoryContent(content: String): String? {
        val trimmed = content.trim()
        val patterns = listOf(
            Regex("(?i)please\\s+remember(?:\\s+that)?[:：\\s]+(.+)"),
            Regex("(?i)remember(?:\\s+that|\\s+this)?[:：\\s]+(.+)"),
            Regex("请?帮?我?记住[:：\\s]*(.+)"),
            Regex("记一下[:：\\s]*(.+)"),
            Regex("以后记得[:：\\s]*(.+)"),
            Regex("把(.+?)(?:记下来|加入记忆|加到记忆|写入记忆|存到记忆)")
        )

        val raw = patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(trimmed)?.groupValues?.getOrNull(1)
        } ?: return null

        return raw
            .trim()
            .trim('。', '.', '，', ',', '：', ':', '"', '\'', '“', '”')
            .takeIf { it.isNotBlank() }
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
