package com.memorychat.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
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
            assertEquals("帮助用户把想法推进成可验证的产品和工程改动", cursor.getString(0))
            assertTrue(cursor.getString(1).contains("Agent 工具协作"))
            assertTrue(cursor.getString(2).contains("先给结论"))
            assertTrue(cursor.getString(3).contains("主动使用可用工具"))
            assertTrue(cursor.getString(4).contains("只写入 Persona"))
                assertTrue(cursor.getString(5).contains("技术伙伴"))
        }
        db.query(
            """
            SELECT mission, toolPolicyJson, memoryPolicyJson
            FROM personas
            WHERE id = 'custom_default'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("帮助用户把想法推进成可验证的产品和工程改动", cursor.getString(0))
            assertTrue(cursor.getString(1).contains("主动使用可用工具"))
            assertTrue(cursor.getString(2).contains("只写入 Persona"))
        }
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

        fun createVersion1Database(context: Context): SQLiteDatabase {
            val path = context.getDatabasePath(TEST_DB)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, null).apply {
                version = 1
            }
        }
    }
}
