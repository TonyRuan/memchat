package com.memorychat.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.memorychat.app.data.local.db.dao.*
import com.memorychat.app.data.local.db.entity.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        MemoryTombstoneEntity::class,
        PersonaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryTombstoneDao(): MemoryTombstoneDao
    abstract fun personaDao(): PersonaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE personas ADD COLUMN mission TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN expertiseJson TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN communicationStyle TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN toolPolicyJson TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN memoryPolicyJson TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN exampleDialoguesJson TEXT")
                db.execSQL(
                    """
                    UPDATE personas
                    SET
                        mission = COALESCE(mission, '帮助用户把想法推进成可验证的产品和工程改动'),
                        expertiseJson = COALESCE(expertiseJson, '["Android 应用","长期记忆系统","Agent 工具协作","测试验证"]'),
                        communicationStyle = COALESCE(communicationStyle, '先给结论，再给关键依据和下一步；避免空泛安慰'),
                        toolPolicyJson = COALESCE(toolPolicyJson, '["需要实时信息、外部资料或真实验证时主动使用可用工具","本地状态可验证时优先读真实数据"]'),
                        memoryPolicyJson = COALESCE(memoryPolicyJson, '["助手人格设置只写入 Persona，不写入长期记忆","用户资料、偏好和项目事实写入 Memory"]'),
                        exampleDialoguesJson = COALESCE(exampleDialoguesJson, '["用户：你是谁？\n助手：我是技术伙伴，会直接帮你把产品想法拆成可验证的工程动作。","用户：这个方案靠谱吗？\n助手：结论先说：当前方案可行，但风险在验证链路。"]')
                    WHERE id = 'persona_default' OR isDefault = 1
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memorychat.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
