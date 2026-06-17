package com.memorychat.app.domain.model

import com.google.gson.Gson

data class ConversationDebugSnapshot(
    val conversationId: String,
    val updatedAt: Long,
    val recallScene: String = "general",
    val recalledMemories: List<DebugMemoryTrace> = emptyList(),
    val contextMessageCount: Int = 0,
    val rollingSummary: String = "",
    val summaryWatermark: Long = 0L,
    val summaryUpdated: Boolean = false,
    val retryAfterContextLimit: Boolean = false
)

data class DebugMemoryTrace(
    val id: String,
    val type: MemoryType,
    val content: String,
    val reason: String = ""
)

object ConversationDebugSnapshotJson {
    private val gson = Gson()

    fun toJson(snapshot: ConversationDebugSnapshot): String = gson.toJson(snapshot)

    fun fromJson(json: String): ConversationDebugSnapshot? {
        val text = json.trim()
        if (text.isBlank()) return null
        return runCatching {
            gson.fromJson(text, ConversationDebugSnapshot::class.java)
        }.getOrNull()
    }
}
