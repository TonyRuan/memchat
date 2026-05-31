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
        val result = engine.extractMemories(messages, existing)
        AppLogger.i("MemoryExtraction", "Extracted: new=${result.newMemories.size}, updates=${result.updates.size}")

        val fallbackSourceIds = messages.map { it.id }
        val seenNewContents = existing.map { it.content.trim().lowercase() }.toMutableSet()
        result.newMemories.forEach { candidate ->
            val content = candidate.content.trim()
            if (content.isBlank()) return@forEach
            if (store.isTombstoned(content, candidate.type)) {
                AppLogger.d("MemoryExtraction", "Skipping tombstoned: ${content.take(40)}")
                return@forEach
            }
            if (!seenNewContents.add(content.lowercase())) {
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
