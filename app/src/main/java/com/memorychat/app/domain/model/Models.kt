package com.memorychat.app.domain.model

import java.util.UUID

enum class MemoryType { PROFILE, PREFERENCE, PROJECT, SUMMARY }
enum class MemoryStatus { CANDIDATE, PENDING, ACTIVE, DISABLED, DELETED }

data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val content: String,
    val status: MemoryStatus = MemoryStatus.ACTIVE,
    val importance: Int = 3,
    val confidence: Float = 0.8f,
    val sourceMessageIds: List<String> = emptyList(),
    val sourceConversationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val userEdited: Boolean = false
)

data class MemoryTombstone(
    val id: String = UUID.randomUUID().toString(),
    val memoryType: MemoryType,
    val contentFingerprint: String,
    val deletedReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: String? = null,
    val description: String? = null,
    val role: String? = null,
    val mission: String? = null,
    val expertise: List<String> = emptyList(),
    val tone: String? = null,
    val communicationStyle: String? = null,
    val behaviorRules: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val toolPolicy: List<String> = emptyList(),
    val memoryPolicy: List<String> = emptyList(),
    val exampleDialogues: List<String> = emptyList(),
    val proactivity: Int = 3,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val personaId: String? = null,
    val useMemory: Boolean = true,
    val generateMemory: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String = "",
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class HistorySearchScope { CURRENT, ALL }

data class ConversationHistoryMatch(
    val conversationId: String,
    val conversationTitle: String,
    val messageId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val score: Int = 0,
    val reason: String = ""
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val stream: Boolean = true,
    val enableWebSearch: Boolean = false
)

data class ChatChunk(
    val content: String,
    val done: Boolean = false,
    val searchCitations: List<SearchCitation> = emptyList(),
    val webSearchUsage: WebSearchUsage? = null
)

data class ChatResponse(
    val content: String,
    val usage: TokenUsage? = null,
    val searchCitations: List<SearchCitation> = emptyList(),
    val webSearchUsage: WebSearchUsage? = null
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class MemoryExtractionResult(
    val newMemories: List<MemoryCandidate> = emptyList(),
    val updates: List<MemoryUpdate> = emptyList(),
    val discarded: List<DiscardedInfo> = emptyList()
)

data class MemoryCandidate(
    val type: MemoryType,
    val content: String,
    val importance: Int = 3,
    val confidence: Float = 0.8f,
    val statusSuggestion: MemoryStatus = MemoryStatus.ACTIVE,
    val reason: String = "",
    val sourceMessageIds: List<String> = emptyList()
)

data class MemoryUpdate(
    val targetMemoryId: String,
    val newContent: String,
    val reason: String = "",
    val statusSuggestion: MemoryStatus = MemoryStatus.ACTIVE
)

data class DiscardedInfo(
    val content: String,
    val reason: String
)

data class MemoryRecallResult(
    val memories: List<Memory> = emptyList(),
    val scene: String = "general",
    val reasons: Map<String, String> = emptyMap()
)

data class MemoryQuery(
    val text: String = "",
    val types: List<MemoryType>? = null,
    val limit: Int = 10,
    val allowFallback: Boolean = true
)

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val errors: List<String> = emptyList()
)

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String? = null
)
