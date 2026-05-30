package com.memorychat.app

import android.app.Application
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.repository.*

class MemoryChatApp : Application() {
    lateinit var database: AppDatabase
    lateinit var settingsDataStore: SettingsDataStore
    lateinit var conversationRepo: ConversationRepository
    lateinit var memoryRepo: MemoryRepository
    lateinit var personaRepo: PersonaRepository
    lateinit var exportImportService: ExportImportService

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        conversationRepo = ConversationRepository(database.conversationDao(), database.messageDao())
        memoryRepo = MemoryRepository(database.memoryDao(), database.memoryTombstoneDao())
        personaRepo = PersonaRepository(database.personaDao())
        exportImportService = ExportImportService(memoryRepo, personaRepo)
    }
}
