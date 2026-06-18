package com.memorychat.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.memorychat.app.data.repository.PersonaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migration1To2AddsPersonaContractColumnsAndBackfillsBuiltInAndConfiguredDefaultPersona() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        createVersion1Database(context).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `personaId` TEXT,
                    `useMemory` INTEGER NOT NULL,
                    `generateMemory` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages` (
                    `id` TEXT NOT NULL,
                    `conversationId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memories` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `importance` INTEGER NOT NULL,
                    `confidence` REAL NOT NULL,
                    `sourceMessageIds` TEXT,
                    `sourceConversationId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastUsedAt` INTEGER,
                    `userEdited` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_tombstones` (
                    `id` TEXT NOT NULL,
                    `memoryType` TEXT NOT NULL,
                    `contentFingerprint` TEXT NOT NULL,
                    `deletedReason` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `personas` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `avatar` TEXT,
                    `description` TEXT,
                    `role` TEXT,
                    `tone` TEXT,
                    `behaviorRulesJson` TEXT,
                    `boundariesJson` TEXT,
                    `proactivity` INTEGER NOT NULL,
                    `isDefault` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `personas` (
                    `id`, `name`, `avatar`, `description`, `role`, `tone`,
                    `behaviorRulesJson`, `boundariesJson`, `proactivity`,
                    `isDefault`, `createdAt`, `updatedAt`
                ) VALUES (
                    'persona_default', '技术伙伴', NULL, '旧描述', '技术协作者', '直接',
                    '["结论先行"]', '["不假装知道"]', 3, 0, 100, 100
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `personas` (
                    `id`, `name`, `avatar`, `description`, `role`, `tone`,
                    `behaviorRulesJson`, `boundariesJson`, `proactivity`,
                    `isDefault`, `createdAt`, `updatedAt`
                ) VALUES (
                    'custom_default', '自定义伙伴', NULL, '旧自定义描述', '协作者', '直接',
                    '["保留上下文"]', '["不编造"]', 3, 1, 101, 101
                )
                """.trimIndent()
            )
            close()
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        val db = room.openHelper.writableDatabase

        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`personas`)").use { cursor ->
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        assertTrue(columns.containsAll(PERSONA_CONTRACT_COLUMNS))

        db.query(
            """
            SELECT mission, expertiseJson, communicationStyle, toolPolicyJson,
                   memoryPolicyJson, exampleDialoguesJson
            FROM personas
            WHERE id = 'persona_default'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val defaultPersona = PersonaRepository.createDefaultPersona()
            assertEquals(defaultPersona.mission, cursor.getString(0))
            assertEquals(defaultPersona.expertise, cursor.getStringList(1))
            assertEquals(defaultPersona.communicationStyle, cursor.getString(2))
            assertEquals(defaultPersona.toolPolicy, cursor.getStringList(3))
            assertEquals(defaultPersona.memoryPolicy, cursor.getStringList(4))
            assertEquals(defaultPersona.exampleDialogues, cursor.getStringList(5))
        }
        db.query(
            """
            SELECT mission, toolPolicyJson, memoryPolicyJson
            FROM personas
            WHERE id = 'custom_default'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val defaultPersona = PersonaRepository.createDefaultPersona()
            assertEquals(defaultPersona.mission, cursor.getString(0))
            assertEquals(defaultPersona.toolPolicy, cursor.getStringList(1))
            assertEquals(defaultPersona.memoryPolicy, cursor.getStringList(2))
        }
        room.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration2To3AddsMessageHistorySearchIndexes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        createVersion2Database(context).close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
        val db = room.openHelper.writableDatabase

        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list(`messages`)").use { cursor ->
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }
        assertTrue(indexNames.contains("index_messages_conversationId_createdAt"))
        assertTrue(indexNames.contains("index_messages_createdAt"))

        room.close()
        context.deleteDatabase(TEST_DB)
    }

    private companion object {
        const val TEST_DB = "memorychat-migration-test"
        val PERSONA_CONTRACT_COLUMNS = setOf(
            "mission",
            "expertiseJson",
            "communicationStyle",
            "toolPolicyJson",
            "memoryPolicyJson",
            "exampleDialoguesJson"
        )
        val gson = Gson()

        fun android.database.Cursor.getStringList(index: Int): List<String> {
            return gson.fromJson(getString(index), object : TypeToken<List<String>>() {}.type)
        }

        fun createVersion1Database(context: Context): SQLiteDatabase {
            val path = context.getDatabasePath(TEST_DB)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, null).apply {
                version = 1
            }
        }

        fun createVersion2Database(context: Context): SQLiteDatabase {
            val path = context.getDatabasePath(TEST_DB)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, null).apply {
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `personaId` TEXT,
                        `useMemory` INTEGER NOT NULL,
                        `generateMemory` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `confidence` REAL NOT NULL,
                        `sourceMessageIds` TEXT,
                        `sourceConversationId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER,
                        `userEdited` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_tombstones` (
                        `id` TEXT NOT NULL,
                        `memoryType` TEXT NOT NULL,
                        `contentFingerprint` TEXT NOT NULL,
                        `deletedReason` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `personas` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatar` TEXT,
                        `description` TEXT,
                        `role` TEXT,
                        `mission` TEXT,
                        `expertiseJson` TEXT,
                        `tone` TEXT,
                        `communicationStyle` TEXT,
                        `behaviorRulesJson` TEXT,
                        `boundariesJson` TEXT,
                        `toolPolicyJson` TEXT,
                        `memoryPolicyJson` TEXT,
                        `exampleDialoguesJson` TEXT,
                        `proactivity` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                version = 2
            }
        }
    }
}
