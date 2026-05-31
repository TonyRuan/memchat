package com.memorychat.app

import android.app.Application
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.repository.*
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class MemoryChatApp : Application() {
    lateinit var database: AppDatabase
    lateinit var settingsDataStore: SettingsDataStore
    lateinit var conversationRepo: ConversationRepository
    lateinit var memoryRepo: MemoryRepository
    lateinit var personaRepo: PersonaRepository
    lateinit var exportImportService: ExportImportService
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var defaultPersonaReady: Deferred<Persona>

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        conversationRepo = ConversationRepository(database.conversationDao(), database.messageDao())
        memoryRepo = MemoryRepository(database.memoryDao(), database.memoryTombstoneDao())
        personaRepo = PersonaRepository(database.personaDao())
        exportImportService = ExportImportService(memoryRepo, personaRepo)

        defaultPersonaReady = applicationScope.async {
            personaRepo.getOrCreateDefaultPersona()
        }
    }

    suspend fun getOrCreateDefaultPersona(): Persona {
        if (::defaultPersonaReady.isInitialized) {
            defaultPersonaReady.await()
        }
        return personaRepo.getOrCreateDefaultPersona()
    }
}
