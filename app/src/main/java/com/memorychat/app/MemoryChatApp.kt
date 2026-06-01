package com.memorychat.app

import android.app.Application
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.repository.*
import com.memorychat.app.domain.engine.MemoryExtractionCoordinator
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemoryChatApp : Application() {
    lateinit var database: AppDatabase
    lateinit var settingsDataStore: SettingsDataStore
    lateinit var conversationRepo: ConversationRepository
    lateinit var memoryRepo: MemoryRepository
    lateinit var personaRepo: PersonaRepository
    lateinit var exportImportService: ExportImportService
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryExtractionCoordinator = MemoryExtractionCoordinator()
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

    fun launchBackground(block: suspend CoroutineScope.() -> Unit): Job {
        return applicationScope.launch(block = block)
    }

    fun launchMemoryExtractionIfIdle(
        conversationId: String,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean {
        return memoryExtractionCoordinator.launchIfIdle(conversationId, applicationScope, block)
    }

    val activeMemoryExtractionConversationIds: StateFlow<Set<String>>
        get() = memoryExtractionCoordinator.activeConversationIds

    val isMemoryExtractionActive: StateFlow<Boolean>
        get() = memoryExtractionCoordinator.isAnyActive
}
