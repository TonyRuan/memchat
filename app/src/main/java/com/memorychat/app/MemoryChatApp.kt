package com.memorychat.app

import android.app.Application
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.repository.*
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        // 初始化默认 Persona（如果 personas 表为空）
        CoroutineScope(Dispatchers.IO).launch {
            val existing = personaRepo.listPersonas()
            if (existing.isEmpty()) {
                personaRepo.savePersona(Persona(
                    id = "persona_default",
                    name = "技术伙伴",
                    description = "适合产品讨论、技术协作的默认人格",
                    role = "技术协作者",
                    tone = "直接、清晰、有见地",
                    behaviorRules = listOf("漏指令立即补全", "结论先行再展开", "必要时引用参考", "不确定时直接说明"),
                    boundaries = listOf("不要假装知道不确定的信息", "不要在无偏好的时候硬编偏好"),
                    proactivity = 4,
                    isDefault = true
                ))
            }
        }
    }
}
