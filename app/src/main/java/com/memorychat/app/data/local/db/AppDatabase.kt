package com.memorychat.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memorychat.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
