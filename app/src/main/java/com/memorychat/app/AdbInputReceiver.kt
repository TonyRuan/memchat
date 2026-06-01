package com.memorychat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.local.db.entity.MessageEntity
import com.memorychat.app.data.repository.MemoryRepository
import com.memorychat.app.data.repository.PersonaRepository
import com.memorychat.app.domain.agent.AgentDecision
import com.memorychat.app.domain.agent.AgentDecisionEngine
import com.memorychat.app.domain.agent.AgentFinalAnswerPolicy
import com.memorychat.app.domain.agent.AgentPersonaStore
import com.memorychat.app.domain.agent.AgentToolExecutor
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.Conversation
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryRecallResult
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
import com.memorychat.app.domain.engine.MemoryExtractionSaver
import com.memorychat.app.domain.engine.MemoryExtractionStore
import com.memorychat.app.domain.engine.MemoryExtractionTriggerPolicy
import com.memorychat.app.domain.engine.MemoryEngine
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

class AdbInputReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle API key setting
        val setApiKey = intent.getStringExtra("set_api_key")
        if (setApiKey != null) {
            val ds = SettingsDataStore(context)
            CoroutineScope(Dispatchers.IO).launch {
                ds.saveApiKey(setApiKey)
                Log.i("AdbInput", "API Key set: ${setApiKey.take(8)}...")
            }
            return
        }
        
        val message = intent.getStringExtra("msg")
            ?: intent.getStringExtra("msg_b64")?.let { encoded ->
                String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            }
            ?: return
        val conversationId = intent.getStringExtra("conv_id") ?: return

        Log.i("AdbInput", "=== NEW MESSAGE ===")
        Log.i("AdbInput", "msg=${message.take(50)}, conv=$conversationId")

        val pendingResult = goAsync()
        val db = AppDatabase.getInstance(context)
        val ds = SettingsDataStore(context)
        val personaRepo = PersonaRepository(db.personaDao())
        val memoryRepo = MemoryRepository(db.memoryDao(), db.memoryTombstoneDao())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Save user message
                val userMsg = MessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "user",
                    content = message,
                    createdAt = System.currentTimeMillis()
                )
                db.messageDao().insert(userMsg)
                Log.i("AdbInput", "[1/4] User message saved")

                // Get API settings
                val apiKey = ds.apiKey.first()
                val baseUrl = ds.baseUrl.first()
                val model = ds.modelName.first()

                if (apiKey.isNotBlank()) {
                    val provider = OpenAICompatibleProvider(apiKey, baseUrl, model)
                    val conversationEntity = db.conversationDao().getById(conversationId)
                    val conversation = conversationEntity?.let {
                        Conversation(
                            id = it.id,
                            title = it.title,
                            personaId = it.personaId,
                            useMemory = it.useMemory == 1,
                            generateMemory = it.generateMemory == 1,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }
                    var persona = conversation?.personaId?.let { personaRepo.getPersona(it) }
                    if (persona == null && conversation != null) {
                        persona = personaRepo.getOrCreateDefaultPersona()
                    }
                    if (conversationEntity == null) {
                        Log.w("AdbInput", "Conversation not found for conv_id=$conversationId")
                    } else {
                        Log.i("AdbInput", "Conversation persona=${conversation?.personaId ?: "none"}, useMemory=${conversation?.useMemory}, generateMemory=${conversation?.generateMemory}")
                    }

                    val latestUserMessage = ChatMessage(
                        id = userMsg.id,
                        conversationId = conversationId,
                        role = "user",
                        content = userMsg.content,
                        createdAt = userMsg.createdAt
                    )
                    val decision = AgentDecisionEngine(provider, model).decide(message, persona)
                    val extractionStore = memoryExtractionStore(memoryRepo)
                    if (persona != null && conversation != null) {
                        val toolExecution = AgentToolExecutor(
                            personaStore = object : AgentPersonaStore {
                                override suspend fun savePersona(persona: Persona) {
                                    personaRepo.savePersona(persona)
                                }
                            },
                            memoryStore = extractionStore
                        ).execute(
                            decision = decision,
                            persona = persona,
                            conversation = conversation,
                            sourceMessages = listOf(latestUserMessage)
                        )
                        persona = toolExecution.persona
                        Log.i("AdbInput", "Agent tools: calls=${decision.toolCalls.size}, results=${toolExecution.toolResults.size}")
                        val finalAnswerDecision = AgentFinalAnswerPolicy.resolve(decision, toolExecution.appliedActions)
                        if (!finalAnswerDecision.shouldCallModel && finalAnswerDecision.directAnswer != null) {
                            db.messageDao().insert(
                                MessageEntity(
                                    id = java.util.UUID.randomUUID().toString(),
                                    conversationId = conversationId,
                                    role = "assistant",
                                    content = finalAnswerDecision.directAnswer,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            Log.i("AdbInput", "Direct agent action answer saved")
                            return@launch
                        }
                        runChatAndMemory(
                            provider = provider,
                            model = model,
                            db = db,
                            ds = ds,
                            memoryRepo = memoryRepo,
                            conversation = conversation,
                            persona = persona,
                            message = message,
                            conversationId = conversationId,
                            latestUserMessage = latestUserMessage,
                            decision = decision,
                            toolResults = toolExecution.toolResults,
                            appliedActionLines = finalAnswerDecision.appliedActionLines,
                            skipTurnMemoryExtraction = toolExecution.memoryWritten,
                            extractionStore = extractionStore
                        )
                        return@launch
                    }

                    // === MEMORY RECALL ===
                    Log.i("AdbInput", "[2/4] Memory recall start")
                    val activeMemories = if (conversation?.useMemory != false) {
                        memoryRepo.getActiveMemories()
                    } else {
                        emptyList()
                    }
                    Log.i("AdbInput", "  DB returned ${activeMemories.size} active memories")

                    val messages = mutableListOf<ChatMessage>()
                    var recalledCount = 0

                    if (activeMemories.isNotEmpty()) {
                        val memoryEngine = MemoryEngine(provider, model)
                        val recall = memoryEngine.recall(message, activeMemories, persona)
                        recalledCount = recall.memories.size
                        Log.i("AdbInput", "  Recall: scene=${recall.scene}, matched=$recalledCount")
                        
                        recall.memories.forEach { mem ->
                            Log.i("AdbInput", "  -> [${mem.type}] ${mem.content.take(50)}")
                        }

                        val systemPrompt = MemoryEngine.buildRecallPrompt(
                            persona = persona,
                            preferences = recall.memories.filter { it.type == MemoryType.PREFERENCE },
                            profile = recall.memories.filter { it.type == MemoryType.PROFILE },
                            projects = recall.memories.filter { it.type == MemoryType.PROJECT },
                            summaries = recall.memories.filter { it.type == MemoryType.SUMMARY }
                        )
                        messages.add(ChatMessage(role = "system", content = systemPrompt))
                        Log.i("AdbInput", "  System prompt injected (${systemPrompt.length} chars)")
                    } else {
                        Log.i("AdbInput", "  No memories in DB, skipping recall")
                        val systemPrompt = MemoryEngine.buildRecallPrompt(
                            persona = persona,
                            preferences = emptyList(),
                            profile = emptyList(),
                            projects = emptyList(),
                            summaries = emptyList()
                        )
                        messages.add(ChatMessage(role = "system", content = systemPrompt))
                        Log.i("AdbInput", "  System prompt injected (${systemPrompt.length} chars)")
                    }

                    messages.add(ChatMessage(role = "user", content = message))
                    Log.i("AdbInput", "[3/4] Calling API with ${messages.size} messages (recall=$recalledCount)")

                    // === API CALL ===
                    val response = provider.complete(
                        ChatRequest(
                            messages = messages,
                            model = model,
                            stream = false
                        )
                    )
                    Log.i("AdbInput", "[4/4] API response: ${response.content.take(80)}")

                    // Save assistant message
                    val assistantMsg = MessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "assistant",
                        content = response.content,
                        createdAt = System.currentTimeMillis()
                    )
                    db.messageDao().insert(assistantMsg)
                    if (conversation?.generateMemory == true) {
                        val latestUserMessage = ChatMessage(
                            id = userMsg.id,
                            conversationId = conversationId,
                            role = "user",
                            content = userMsg.content,
                            createdAt = userMsg.createdAt
                        )
                        val watermark = ds.getMemoryExtractionWatermark(conversationId)
                        val unextractedMessages = db.messageDao()
                            .getByConversationIdAfter(conversationId, watermark)
                            .map { entity ->
                                ChatMessage(
                                    id = entity.id,
                                    conversationId = entity.conversationId,
                                    role = entity.role,
                                    content = entity.content,
                                    createdAt = entity.createdAt
                                )
                            }
                        val trigger = MemoryExtractionTriggerPolicy()
                            .afterAssistantTurn(unextractedMessages, latestUserMessage)
                        if (trigger == null) {
                            Log.i("AdbInput", "Extraction deferred: unextracted=${unextractedMessages.size}")
                            Log.i("AdbInput", "=== DONE (recall=$recalledCount) ===")
                            return@launch
                        }
                        val extractionStore = object : MemoryExtractionStore {
                            override suspend fun getActiveMemories(): List<Memory> = memoryRepo.getActiveMemories()
                            override suspend fun isTombstoned(content: String, type: MemoryType): Boolean =
                                memoryRepo.isTombstoned(content, type)
                            override suspend fun insert(memory: Memory) = memoryRepo.insert(memory)
                            override suspend fun getById(id: String): Memory? = memoryRepo.getById(id)
                            override suspend fun update(memory: Memory) = memoryRepo.update(memory)
                        }
                        val extraction = MemoryExtractionSaver(MemoryEngine(provider, model), extractionStore)
                            .extractAndSave(
                                conversation,
                                unextractedMessages
                            )
                        unextractedMessages.maxOfOrNull { it.createdAt }?.let { extractedAt ->
                            ds.saveMemoryExtractionWatermark(conversationId, extractedAt)
                        }
                        Log.i("AdbInput", "Extraction: trigger=$trigger, new=${extraction.newMemories.size}, updates=${extraction.updates.size}")
                    } else {
                        Log.i("AdbInput", "Extraction skipped: generateMemory=false")
                    }
                    Log.i("AdbInput", "=== DONE (recall=$recalledCount) ===")
                } else {
                    Log.w("AdbInput", "API key is empty!")
                }
            } catch (e: Exception) {
                Log.e("AdbInput", "Error: ${e.message}", e)
                val errorMsg = MessageEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "assistant",
                    content = "Error: ${e.message}",
                    createdAt = System.currentTimeMillis()
                )
                db.messageDao().insert(errorMsg)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runChatAndMemory(
        provider: OpenAICompatibleProvider,
        model: String,
        db: AppDatabase,
        ds: SettingsDataStore,
        memoryRepo: MemoryRepository,
        conversation: Conversation,
        persona: Persona?,
        message: String,
        conversationId: String,
        latestUserMessage: ChatMessage,
        decision: AgentDecision,
        toolResults: List<String>,
        appliedActionLines: List<String>,
        skipTurnMemoryExtraction: Boolean,
        extractionStore: MemoryExtractionStore
    ) {
        Log.i("AdbInput", "[2/4] Memory recall start")
        val activeMemories = if (conversation.useMemory) {
            memoryRepo.getActiveMemories()
        } else {
            emptyList()
        }
        val memoryEngine = MemoryEngine(provider, model)
        val recall = if (activeMemories.isNotEmpty()) {
            memoryEngine.recall(message, activeMemories, persona)
        } else {
            MemoryRecallResult()
        }
        Log.i("AdbInput", "  Recall: scene=${recall.scene}, matched=${recall.memories.size}")
        recall.memories.forEach { mem ->
            Log.i("AdbInput", "  -> [${mem.type}] ${mem.content.take(50)}")
        }

        val basePrompt = MemoryEngine.buildRecallPrompt(
            persona = persona,
            preferences = recall.memories.filter { it.type == MemoryType.PREFERENCE },
            profile = recall.memories.filter { it.type == MemoryType.PROFILE },
            projects = recall.memories.filter { it.type == MemoryType.PROJECT },
            summaries = recall.memories.filter { it.type == MemoryType.SUMMARY }
        )
        val systemPrompt = decorateSystemPrompt(basePrompt, decision, toolResults, appliedActionLines)
        val messages = mutableListOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = message)
        )
        Log.i("AdbInput", "  System prompt injected (${systemPrompt.length} chars)")
        Log.i("AdbInput", "[3/4] Calling API with ${messages.size} messages (recall=${recall.memories.size})")

        val response = provider.complete(
            ChatRequest(
                messages = messages,
                model = model,
                stream = false,
                enableWebSearch = decision.usesWebSearch()
            )
        )
        Log.i("AdbInput", "[4/4] API response: ${response.content.take(80)}")

        val assistantCreatedAt = System.currentTimeMillis()
        db.messageDao().insert(
            MessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "assistant",
                content = response.content,
                createdAt = assistantCreatedAt
            )
        )

        if (skipTurnMemoryExtraction) {
            ds.saveMemoryExtractionWatermark(conversationId, maxOf(latestUserMessage.createdAt, assistantCreatedAt))
            Log.i("AdbInput", "Extraction skipped: agent memory tool already wrote this turn")
            Log.i("AdbInput", "=== DONE (recall=${recall.memories.size}) ===")
            return
        }

        if (conversation.generateMemory) {
            val watermark = ds.getMemoryExtractionWatermark(conversationId)
            val unextractedMessages = db.messageDao()
                .getByConversationIdAfter(conversationId, watermark)
                .map { entity ->
                    ChatMessage(
                        id = entity.id,
                        conversationId = entity.conversationId,
                        role = entity.role,
                        content = entity.content,
                        createdAt = entity.createdAt
                    )
                }
            val trigger = MemoryExtractionTriggerPolicy()
                .afterAssistantTurn(unextractedMessages, latestUserMessage)
            if (trigger == null) {
                Log.i("AdbInput", "Extraction deferred: unextracted=${unextractedMessages.size}")
                Log.i("AdbInput", "=== DONE (recall=${recall.memories.size}) ===")
                return
            }
            val extraction = MemoryExtractionSaver(MemoryEngine(provider, model), extractionStore)
                .extractAndSave(
                    conversation,
                    unextractedMessages
                )
            unextractedMessages.maxOfOrNull { it.createdAt }?.let { extractedAt ->
                ds.saveMemoryExtractionWatermark(conversationId, extractedAt)
            }
            Log.i("AdbInput", "Extraction: trigger=$trigger, new=${extraction.newMemories.size}, updates=${extraction.updates.size}")
        } else {
            Log.i("AdbInput", "Extraction skipped: generateMemory=false")
        }
        Log.i("AdbInput", "=== DONE (recall=${recall.memories.size}) ===")
    }

    private fun decorateSystemPrompt(
        basePrompt: String,
        decision: AgentDecision,
        toolResults: List<String>,
        appliedActionLines: List<String>
    ): String {
        return buildString {
            append(basePrompt)
            appendLine()
            appendLine("[Environment]")
            appendLine("Current time: ${Instant.now()}")
            decision.temporaryResponseFormat?.let {
                appendLine("Temporary response format for this answer: $it")
            }
            if (toolResults.isNotEmpty()) {
                appendLine()
                appendLine("[Tool Results]")
                toolResults.forEach { appendLine("- $it") }
            }
            if (appliedActionLines.isNotEmpty()) {
                appendLine()
                appendLine("[Applied Actions]")
                appendLine("These actions have already been applied to local app state. Treat them as observations, not suggestions.")
                appliedActionLines.forEach { appendLine("- $it") }
            }
        }
    }

    private fun memoryExtractionStore(memoryRepo: MemoryRepository): MemoryExtractionStore {
        return object : MemoryExtractionStore {
            override suspend fun getActiveMemories(): List<Memory> = memoryRepo.getActiveMemories()
            override suspend fun isTombstoned(content: String, type: MemoryType): Boolean =
                memoryRepo.isTombstoned(content, type)
            override suspend fun insert(memory: Memory) = memoryRepo.insert(memory)
            override suspend fun getById(id: String): Memory? = memoryRepo.getById(id)
            override suspend fun update(memory: Memory) = memoryRepo.update(memory)
        }
    }
}

