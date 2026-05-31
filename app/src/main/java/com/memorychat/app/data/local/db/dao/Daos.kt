package com.memorychat.app.data.local.db.dao

import androidx.room.*
import com.memorychat.app.data.local.db.entity.*

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationEntity)

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC")
    suspend fun getByConversationId(convId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId")
    suspend fun countByConversationId(convId: String): Int

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteByConversationId(convId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE status = 'ACTIVE' ORDER BY importance DESC, updatedAt DESC")
    suspend fun getActiveMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE status = 'pending' ORDER BY updatedAt DESC")
    suspend fun getPendingMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE status != 'deleted' ORDER BY updatedAt DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE type = :type AND status = 'active' ORDER BY importance DESC")
    suspend fun getByType(type: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Update
    suspend fun update(entity: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE memories SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface MemoryTombstoneDao {
    @Query("SELECT * FROM memory_tombstones WHERE contentFingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): MemoryTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryTombstoneEntity)
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isDefault DESC, name ASC")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: String)
}

