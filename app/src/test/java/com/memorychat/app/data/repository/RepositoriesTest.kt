package com.memorychat.app.data.repository

import com.google.gson.JsonParser
import com.memorychat.app.data.local.db.dao.MemoryDao
import com.memorychat.app.data.local.db.dao.MemoryTombstoneDao
import com.memorychat.app.data.local.db.dao.PersonaDao
import com.memorychat.app.data.local.db.entity.MemoryEntity
import com.memorychat.app.data.local.db.entity.MemoryTombstoneEntity
import com.memorychat.app.data.local.db.entity.PersonaEntity
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
    @Test
    fun getOrCreateDefaultPersonaCreatesSeedWhenMissing() = runBlocking {
        val personaDao = FakePersonaDao()
        val repo = PersonaRepository(personaDao)

        val persona = repo.getOrCreateDefaultPersona()

        assertEquals("persona_default", persona.id)
        assertEquals("技术伙伴", persona.name)
        assertTrue(persona.isDefault)
        assertEquals("persona_default", personaDao.getDefault()?.id)
    }

    @Test
    fun importPersonasJsonAcceptsCamelCaseFieldsFromExistingExports() = runBlocking {
        val personaRepo = PersonaRepository(FakePersonaDao())
        val service = ExportImportService(MemoryRepository(FakeMemoryDao(), FakeMemoryTombstoneDao()), personaRepo)
        val json = """
            {
              "version": "1.0",
              "personas": [
                {
                  "id": "p1",
                  "name": "Imported",
                  "behaviorRules": ["rule-a", "rule-b"],
                  "isDefault": true
                }
              ]
            }
        """.trimIndent()

        val result = service.importPersonasJson(json)
        val persona = personaRepo.getPersona("p1")

        assertEquals(1, result.imported)
        assertNotNull(persona)
        assertEquals(listOf("rule-a", "rule-b"), persona?.behaviorRules)
        assertTrue(persona?.isDefault == true)
    }

    @Test
    fun exportPersonasJsonUsesSnakeCaseFieldNames() = runBlocking {
        val personaRepo = PersonaRepository(FakePersonaDao().apply {
            insert(PersonaEntity(
                id = "p1",
                name = "Exported",
                behaviorRulesJson = """["rule-a"]""",
                isDefault = 1
            ))
        })
        val service = ExportImportService(MemoryRepository(FakeMemoryDao(), FakeMemoryTombstoneDao()), personaRepo)

        val json = service.exportPersonasJson()
        val personaJson = JsonParser.parseString(json)
            .asJsonObject
            .getAsJsonArray("personas")[0]
            .asJsonObject

        assertTrue(personaJson.has("behavior_rules"))
        assertTrue(personaJson.has("is_default"))
        assertFalse(personaJson.has("behaviorRules"))
        assertFalse(personaJson.has("isDefault"))
    }

    @Test
    fun importMemoriesJsonImportsValidMemoriesWithDefaults() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "version": "1.0",
              "memories": [
                {
                  "id": "m1",
                  "type": "project",
                  "content": "第一阶段优先验证记忆系统"
                }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)
        val memory = memoryDao.getById("m1")

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertTrue(result.errors.isEmpty())
        assertNotNull(memory)
        assertEquals(MemoryType.PROJECT.name, memory?.type)
        assertEquals(MemoryStatus.ACTIVE.name, memory?.status)
        assertEquals(3, memory?.importance)
        assertEquals(0.8f, memory?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun importMemoriesJsonSkipsInvalidRowsWithoutCrashing() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "version": "1.0",
              "memories": [
                { "id": "blank", "type": "profile", "content": " " },
                { "id": "bad-type", "type": "temporary", "content": "should skip" },
                { "id": "bad-status", "type": "profile", "status": "archived", "content": "should skip" }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)

        assertEquals(0, result.imported)
        assertEquals(3, result.skipped)
        assertTrue(result.errors.any { it.contains("empty content") })
        assertTrue(result.errors.any { it.contains("Invalid memory type") })
        assertTrue(result.errors.any { it.contains("Invalid memory status") })
        assertTrue(memoryDao.memories.isEmpty())
    }

    @Test
    fun importMemoriesJsonSkipsTombstonedMemory() = runBlocking {
        val memoryDao = FakeMemoryDao()
        val tombstoneDao = FakeMemoryTombstoneDao().apply {
            tombstone("不要使用猫娘语气")
        }
        val service = ExportImportService(MemoryRepository(memoryDao, tombstoneDao), PersonaRepository(FakePersonaDao()))
        val json = """
            {
              "memories": [
                { "id": "m1", "type": "preference", "content": "不要使用猫娘语气" }
              ]
            }
        """.trimIndent()

        val result = service.importMemoriesJson(json)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(memoryDao.memories.isEmpty())
    }

    @Test
    fun exportMemoriesJsonExcludesDeletedMemories() = runBlocking {
        val memoryDao = FakeMemoryDao().apply {
            insert(MemoryEntity(
                id = "active",
                type = MemoryType.PROJECT.name,
                content = "第一阶段优先验证记忆系统",
                status = MemoryStatus.ACTIVE.name
            ))
            insert(MemoryEntity(
                id = "deleted",
                type = MemoryType.PREFERENCE.name,
                content = "已删除偏好",
                status = MemoryStatus.DELETED.name
            ))
        }
        val service = ExportImportService(MemoryRepository(memoryDao, FakeMemoryTombstoneDao()), PersonaRepository(FakePersonaDao()))

        val json = service.exportMemoriesJson()
        val memories = JsonParser.parseString(json).asJsonObject.getAsJsonArray("memories")

        assertEquals(1, memories.size())
        assertEquals("active", memories[0].asJsonObject.get("id").asString)
    }

    private class FakePersonaDao : PersonaDao {
        private val personas = LinkedHashMap<String, PersonaEntity>()

        override suspend fun getAll(): List<PersonaEntity> {
            return personas.values.sortedWith(compareByDescending<PersonaEntity> { it.isDefault }.thenBy { it.name })
        }

        override suspend fun getById(id: String): PersonaEntity? {
            return personas[id]
        }

        override suspend fun getDefault(): PersonaEntity? {
            return personas.values.firstOrNull { it.isDefault == 1 }
        }

        override suspend fun insert(entity: PersonaEntity) {
            personas[entity.id] = entity
        }

        override suspend fun delete(id: String) {
            personas.remove(id)
        }
    }

    private class FakeMemoryDao : MemoryDao {
        val memories = LinkedHashMap<String, MemoryEntity>()

        override suspend fun getActiveMemories(): List<MemoryEntity> {
            return memories.values.filter { it.status == MemoryStatus.ACTIVE.name }
        }

        override suspend fun getPendingMemories(): List<MemoryEntity> {
            return memories.values.filter { it.status == MemoryStatus.PENDING.name }
        }

        override suspend fun getAllMemories(): List<MemoryEntity> {
            return memories.values.toList()
        }

        override suspend fun getById(id: String): MemoryEntity? {
            return memories[id]
        }

        override suspend fun getByType(type: String): List<MemoryEntity> {
            return memories.values.filter { it.type == type && it.status == MemoryStatus.ACTIVE.name }
        }

        override suspend fun insert(entity: MemoryEntity) {
            memories[entity.id] = entity
        }

        override suspend fun update(entity: MemoryEntity) {
            memories[entity.id] = entity
        }

        override suspend fun delete(id: String) {
            memories.remove(id)
        }

        override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
            memories[id]?.let { existing ->
                memories[id] = existing.copy(status = status, updatedAt = updatedAt)
            }
        }
    }

    private class FakeMemoryTombstoneDao : MemoryTombstoneDao {
        private val tombstones = LinkedHashMap<String, MemoryTombstoneEntity>()

        override suspend fun getByFingerprint(fingerprint: String): MemoryTombstoneEntity? {
            return tombstones[fingerprint]
        }

        override suspend fun insert(entity: MemoryTombstoneEntity) {
            tombstones[entity.contentFingerprint] = entity
        }

        fun tombstone(content: String) {
            val fingerprint = java.security.MessageDigest.getInstance("MD5")
                .digest(content.trim().lowercase().toByteArray())
                .joinToString("") { "%02x".format(it) }
            tombstones[fingerprint] = MemoryTombstoneEntity(
                id = "t-$fingerprint",
                memoryType = MemoryType.PREFERENCE.name,
                contentFingerprint = fingerprint
            )
        }
    }
}
