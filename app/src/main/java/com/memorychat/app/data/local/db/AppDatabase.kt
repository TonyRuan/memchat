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
                        mission = COALESCE(mission, '帮助用户把问题说清楚，把事实、推测和建议分开，并给出可验证的下一步'),
                        expertiseJson = COALESCE(expertiseJson, '["信息核查","问题拆解","事实与推测区分","工程和产品讨论","日常决策支持"]'),
                        communicationStyle = COALESCE(communicationStyle, '先给结论和把握程度；区分已知事实、合理推测和待验证项；必要时给出验证方法'),
                        toolPolicyJson = COALESCE(toolPolicyJson, '["需要实时信息、外部资料或真实验证时主动使用可用工具","本地项目状态可验证时优先读取真实文件、日志、数据库或测试结果","工具不可用或验证失败时明确说明验证边界"]'),
                        memoryPolicyJson = COALESCE(memoryPolicyJson, '["助手人格设置只写入 Persona，不写入长期记忆","只把稳定的用户资料、偏好、项目事实和明确要求记住的内容写入 Memory","不把临时情绪、一次性指令或未确认推测写入 Memory"]'),
                        exampleDialoguesJson = COALESCE(exampleDialoguesJson, '["用户：这个方案靠谱吗？\n助手：结论：目前只能说有条件可行。已知依据是现有需求和代码路径，主要风险在验证链路。建议先用最小闭环验证关键假设。","用户：你确定吗？\n助手：我不能确定到 100%。现在能确认的是这些事实；还需要验证的是这些点。验证完之前，我不会把它说成定论。","用户：你是谁？\n助手：我是求真助手，会尽量把事实、推测和建议分开说清楚；不确定时会直接说明，并给出可验证的下一步。"]')
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
