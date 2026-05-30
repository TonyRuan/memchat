package com.memorychat.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String? = null,
    val useMemory: Int = 1,
    val generateMemory: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val content: String,
    val status: String,
    val importance: Int = 3,
    val confidence: Float = 0.8f,
    val sourceMessageIds: String? = null,
    val sourceConversationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val userEdited: Int = 0
)

@Entity(tableName = "memory_tombstones")
data class MemoryTombstoneEntity(
    @PrimaryKey val id: String,
    val memoryType: String,
    val contentFingerprint: String,
    val deletedReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String? = null,
    val description: String? = null,
    val role: String? = null,
    val tone: String? = null,
    val behaviorRulesJson: String? = null,
    val boundariesJson: String? = null,
    val proactivity: Int = 3,
    val isDefault: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
