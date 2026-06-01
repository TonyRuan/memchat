package com.memorychat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.agent.AgentDecision
import com.memorychat.app.domain.agent.AgentDecisionEngine
import com.memorychat.app.domain.agent.AgentPersonaStore
import com.memorychat.app.domain.agent.AgentToolExecutor
import com.memorychat.app.domain.model.*
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
import com.memorychat.app.domain.engine.ChatTurnErrorPersister
import com.memorychat.app.domain.engine.ConversationMessageStore
import com.memorychat.app.domain.engine.MemoryExtractionSaver
import com.memorychat.app.domain.engine.MemoryExtractionStore
import com.memorychat.app.domain.engine.MemoryExtractionTrigger
import com.memorychat.app.domain.engine.MemoryExtractionTriggerPolicy
import com.memorychat.app.domain.engine.MemoryEngine
import com.memorychat.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MemoryChatApp

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _lastRecallResult = MutableStateFlow<MemoryRecallResult?>(null)
    val lastRecallResult: StateFlow<MemoryRecallResult?> = _lastRecallResult

    private val _memoryExtractionStatus = MutableStateFlow<String?>(null)
    val memoryExtractionStatus: StateFlow<String?> = _memoryExtractionStatus
    val activeMemoryExtractionConversationIds = app.activeMemoryExtractionConversationIds

    private var llmProvider: OpenAICompatibleProvider? = null
    private var memoryEngine: MemoryEngine? = null
    private var streamJob: Job? = null
    private val sendGate = GenerationSendGate()
    private val extractionPolicy = MemoryExtractionTriggerPolicy()

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            AppLogger.i("ChatVM", "Loading conversation: $conversationId")
            val conv = app.conversationRepo.getConversation(conversationId)
            _conversation.value = conv
            val msgs = app.conversationRepo.getMessages(conversationId)
            _messages.value = msgs
            AppLogger.i("ChatVM", "Loaded ${msgs.size} messages")

            val apiKey = app.settingsDataStore.apiKey.first()
            val baseUrl = app.settingsDataStore.baseUrl.first()
            val model = app.settingsDataStore.modelName.first()
            val maxTokens = app.settingsDataStore.maxTokens.first()

            if (apiKey.isNotBlank()) {
                llmProvider = OpenAICompatibleProvider(apiKey, baseUrl, model, maxTokens)
                memoryEngine = MemoryEngine(llmProvider!!, model)
                AppLogger.i("ChatVM", "Provider ready: $model")
            }
        }
    }

    fun sendMessage(content: String) {
        val provider = llmProvider
        val conv = _conversation.value

        if (provider == null || conv == null) {
            AppLogger.e("ChatVM", "Cannot send: provider=$provider, conv=$conv")
            return
        }
        if (!sendGate.tryStart()) {
            AppLogger.w("ChatVM", "Ignoring send while generation is active")
            return
        }

        AppLogger.i("ChatVM", "Sending: ${content.take(50)}")
        _isGenerating.value = true
        _streamingContent.value = ""

        viewModelScope.launch {
            try {
                // 加载当前会话绑定的 Persona
                val (activeConv, loadedPersona) = ensureConversationPersona(conv)
                val model = app.settingsDataStore.modelName.first()
                AppLogger.i("ChatVM", "Persona loaded: ${loadedPersona.name}")

                val userMsg = ChatMessage(conversationId = activeConv.id, role = "user", content = content)
                app.conversationRepo.saveMessage(userMsg)
                _messages.value = _messages.value + userMsg

                val decision = AgentDecisionEngine(provider, model).decide(content, loadedPersona)
                val toolExecution = executeAgentTools(decision, loadedPersona, activeConv, listOf(userMsg))
                val persona = toolExecution.persona
                AppLogger.i("ChatVM", "Agent tools: calls=${decision.toolCalls.size}, results=${toolExecution.toolResults.size}")

                val memories = if (activeConv.useMemory) {
                    val activeMemories = app.memoryRepo.getActiveMemories()
                    val recall = memoryEngine?.recall(content, activeMemories, persona)
                    _lastRecallResult.value = recall
                    AppLogger.i("ChatVM", "Recall: scene=${recall?.scene}, count=${recall?.memories?.size}")
                    recall?.memories ?: emptyList()
                } else emptyList()

                val systemPrompt = buildSystemPrompt(
                    memories = memories,
                    persona = persona,
                    toolResults = toolExecution.toolResults,
                    temporaryResponseFormat = decision.temporaryResponseFormat
                )
                val allMessages = _messages.value.map { ChatMessage(role = it.role, content = it.content) }.toMutableList()
                if (systemPrompt.isNotBlank()) {
                    allMessages.add(0, ChatMessage(role = "system", content = systemPrompt))
                }

                AppLogger.i("ChatVM", "Starting stream, model=$model")

                streamJob = viewModelScope.launch {
                    var accumulated = ""
                    try {
                        if (decision.usesWebSearch()) {
                            val response = provider.complete(
                                ChatRequest(
                                    messages = allMessages,
                                    model = model,
                                    stream = false,
                                    enableWebSearch = true
                                )
                            )
                            val finalContent = response.content
                            if (finalContent.isNotBlank()) {
                                val assistantMsg = ChatMessage(conversationId = activeConv.id, role = "assistant", content = finalContent)
                                app.conversationRepo.saveMessage(assistantMsg)
                                _messages.value = _messages.value + assistantMsg
                                if (toolExecution.memoryWritten) {
                                    saveExtractionWatermark(activeConv.id, listOf(userMsg, assistantMsg))
                                    AppLogger.i("ChatVM", "Memory extraction skipped: agent memory tool already wrote this turn")
                                } else memoryEngine?.let { engine ->
                                    scheduleMemoryExtractionAfterTurn(engine, activeConv, userMsg)
                                }
                            }
                        } else provider.streamChat(
                            ChatRequest(
                                messages = allMessages,
                                model = model,
                                stream = true,
                                enableWebSearch = false
                            )
                        ).collect { chunk ->
                            if (chunk.done) {
                                val finalContent = accumulated
                                AppLogger.i("ChatVM", "Stream done, length=${finalContent.length}")
                                accumulated = ""
                                _streamingContent.value = ""
                                if (finalContent.isNotBlank()) {
                                    val assistantMsg = ChatMessage(conversationId = activeConv.id, role = "assistant", content = finalContent)
                                    app.conversationRepo.saveMessage(assistantMsg)
                                    _messages.value = _messages.value + assistantMsg
                                    AppLogger.d("ChatVM", "Message saved to DB and added to list")
                                    if (toolExecution.memoryWritten) {
                                        saveExtractionWatermark(activeConv.id, listOf(userMsg, assistantMsg))
                                        AppLogger.i("ChatVM", "Memory extraction skipped: agent memory tool already wrote this turn")
                                    } else memoryEngine?.let { engine ->
                                        scheduleMemoryExtractionAfterTurn(engine, activeConv, userMsg)
                                    }
                                }
                            } else {
                                accumulated += chunk.content
                                _streamingContent.value = accumulated
                            }
                        }
                    } catch (e: CancellationException) {
                        AppLogger.i("ChatVM", "Stream cancelled")
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("ChatVM", "Stream error: ${e.message}")
                        val partialContent = accumulated
                        accumulated = ""
                        _streamingContent.value = ""
                        if (partialContent.isNotBlank()) {
                            val assistantMsg = ChatMessage(conversationId = activeConv.id, role = "assistant", content = partialContent)
                            app.conversationRepo.saveMessage(assistantMsg)
                            _messages.value = _messages.value + assistantMsg
                        } else {
                            val errorMsg = ChatTurnErrorPersister(conversationMessageStore())
                                .persistAssistantError(activeConv.id, e.message)
                            _messages.value = _messages.value + errorMsg
                        }
                    } finally {
                        sendGate.finish()
                        _isGenerating.value = false
                    }
                }
            } catch (e: CancellationException) {
                finishGeneration()
                throw e
            } catch (e: Exception) {
                AppLogger.e("ChatVM", "Send setup error: ${e.message}")
                val errorMsg = ChatTurnErrorPersister(conversationMessageStore())
                    .persistAssistantError(conv.id, e.message)
                _messages.value = _messages.value + errorMsg
                finishGeneration()
            }
        }
    }

    private fun finishGeneration() {
        sendGate.finish()
        _streamingContent.value = ""
        _isGenerating.value = false
    }

    fun stopGeneration() {
        AppLogger.i("ChatVM", "Stop requested")
        val content = _streamingContent.value
        streamJob?.cancel()
        viewModelScope.launch {
            val conv = _conversation.value
            if (content.isNotBlank() && conv != null) {
                val msg = ChatMessage(conversationId = conv.id, role = "assistant", content = content)
                app.conversationRepo.saveMessage(msg)
                _messages.value = _messages.value + msg
            }
            finishGeneration()
        }
    }

    private fun buildSystemPrompt(
        memories: List<Memory>,
        persona: Persona? = null,
        toolResults: List<String> = emptyList(),
        temporaryResponseFormat: String? = null
    ): String {
        val base = MemoryEngine.buildRecallPrompt(
            persona = persona,
            preferences = memories.filter { it.type == MemoryType.PREFERENCE },
            profile = memories.filter { it.type == MemoryType.PROFILE },
            projects = memories.filter { it.type == MemoryType.PROJECT },
            summaries = memories.filter { it.type == MemoryType.SUMMARY }
        )
        return buildString {
            append(base)
            appendLine()
            appendLine("[Environment]")
            appendLine("Current time: ${java.time.Instant.ofEpochMilli(System.currentTimeMillis())}")
            if (temporaryResponseFormat != null) {
                appendLine("Temporary response format for this answer: $temporaryResponseFormat")
            }
            if (toolResults.isNotEmpty()) {
                appendLine()
                appendLine("[Tool Results]")
                toolResults.forEach { appendLine("- $it") }
            }
        }
    }

    private suspend fun executeAgentTools(
        decision: AgentDecision,
        persona: Persona,
        conv: Conversation,
        sourceMessages: List<ChatMessage>
    ) = AgentToolExecutor(
        personaStore = object : AgentPersonaStore {
            override suspend fun savePersona(persona: Persona) {
                app.personaRepo.savePersona(persona)
            }
        },
        memoryStore = memoryExtractionStore()
    ).execute(
        decision = decision,
        persona = persona,
        conversation = conv,
        sourceMessages = sourceMessages
    )

    private suspend fun ensureConversationPersona(conv: Conversation): Pair<Conversation, Persona> {
        conv.personaId?.let { personaId ->
            app.personaRepo.getPersona(personaId)?.let { persona ->
                return conv to persona
            }
        }

        val defaultPersona = app.getOrCreateDefaultPersona()
        val updatedConv = conv.copy(personaId = defaultPersona.id, updatedAt = System.currentTimeMillis())
        app.conversationRepo.saveConversation(updatedConv)
        _conversation.value = updatedConv
        return updatedConv to defaultPersona
    }

    fun extractMemories() {
        val engine = memoryEngine
        val conv = _conversation.value
        if (engine == null) {
            _memoryExtractionStatus.value = "模型配置未就绪，无法整理记忆"
            return
        }
        if (conv == null) {
            _memoryExtractionStatus.value = "会话未加载，无法整理记忆"
            return
        }
        if (!conv.generateMemory) {
            _memoryExtractionStatus.value = "当前会话已关闭生成记忆"
            return
        }

        AppLogger.i("ChatVM", "Extracting memories...")
        viewModelScope.launch {
            // Reload messages from DB to ensure we have the latest
            val freshMessages = app.conversationRepo.getMessages(conv.id)
            _messages.value = freshMessages
            AppLogger.i("ChatVM", "Reloaded ${freshMessages.size} messages for extraction")
            val result = extractMemoriesForMessages(engine, conv, freshMessages)
            if (result == null) {
                _memoryExtractionStatus.value = "记忆整理失败，聊天记录已保留"
                return@launch
            }
            saveExtractionWatermark(conv.id, freshMessages)
            _memoryExtractionStatus.value = if (result.newMemories.isNotEmpty() || result.updates.isNotEmpty()) {
                "记忆整理完成：新增 ${result.newMemories.size} 条，更新 ${result.updates.size} 条"
            } else {
                "没有提取到新的长期记忆"
            }
        }
    }

    fun flushPendingMemoryExtraction() {
        val engine = memoryEngine ?: return
        val conv = _conversation.value ?: return
        scheduleMemoryExtraction(engine, conv, MemoryExtractionTrigger.CONVERSATION_EXIT)
    }

    private fun scheduleMemoryExtractionAfterTurn(
        engine: MemoryEngine,
        conv: Conversation,
        userMsg: ChatMessage
    ) {
        if (!conv.generateMemory) return
        val launched = app.launchMemoryExtractionIfIdle(conv.id) {
            val unextractedMessages = getUnextractedMessages(conv.id)
            val trigger = extractionPolicy.afterAssistantTurn(unextractedMessages, userMsg) ?: run {
                AppLogger.i("ChatVM", "Memory extraction deferred: unextracted=${unextractedMessages.size}")
                return@launchMemoryExtractionIfIdle
            }
            runBackgroundMemoryExtraction(engine, conv, trigger, unextractedMessages)
        }
        if (!launched) {
            AppLogger.i("ChatVM", "Memory extraction check skipped: conversation already active")
        }
    }

    private fun scheduleMemoryExtraction(
        engine: MemoryEngine,
        conv: Conversation,
        trigger: MemoryExtractionTrigger
    ) {
        if (!conv.generateMemory) return
        val launched = app.launchMemoryExtractionIfIdle(conv.id) {
            val unextractedMessages = getUnextractedMessages(conv.id)
            val effectiveTrigger = if (trigger == MemoryExtractionTrigger.CONVERSATION_EXIT) {
                extractionPolicy.onConversationExit(unextractedMessages) ?: run {
                    AppLogger.i("ChatVM", "Memory extraction exit flush skipped: no unextracted user messages")
                    return@launchMemoryExtractionIfIdle
                }
            } else {
                trigger
            }
            runBackgroundMemoryExtraction(engine, conv, effectiveTrigger, unextractedMessages)
        }
        if (!launched) {
            AppLogger.i("ChatVM", "Memory extraction already running for conversation, skip trigger=$trigger")
        }
    }

    private suspend fun getUnextractedMessages(conversationId: String): List<ChatMessage> {
        val watermark = app.settingsDataStore.getMemoryExtractionWatermark(conversationId)
        return app.conversationRepo.getMessagesAfter(conversationId, watermark)
    }

    private suspend fun runBackgroundMemoryExtraction(
        engine: MemoryEngine,
        conv: Conversation,
        trigger: MemoryExtractionTrigger,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return
        AppLogger.i("ChatVM", "Background memory extraction start: trigger=$trigger, messages=${messages.size}")
        val result = extractMemoriesForMessages(engine, conv, messages)
        if (result == null) {
            AppLogger.e("ChatVM", "Background memory extraction failed; watermark not advanced")
            return
        }
        saveExtractionWatermark(conv.id, messages)
        AppLogger.i("ChatVM", "Background memory extraction done: trigger=$trigger, new=${result.newMemories.size}, updates=${result.updates.size}")
    }

    private suspend fun saveExtractionWatermark(conversationId: String, messages: List<ChatMessage>) {
        val watermark = messages.maxOfOrNull { it.createdAt } ?: return
        app.settingsDataStore.saveMemoryExtractionWatermark(conversationId, watermark)
    }

    private suspend fun extractMemoriesForMessages(
        engine: MemoryEngine,
        conv: Conversation,
        messages: List<ChatMessage>
    ): MemoryExtractionResult? {
        return try {
            MemoryExtractionSaver(engine, memoryExtractionStore()).extractAndSave(conv, messages)
        } catch (e: Exception) {
            AppLogger.e("ChatVM", "Memory extraction failed without blocking chat: ${e.message}")
            null
        }
    }

    fun clearMemoryExtractionStatus() {
        _memoryExtractionStatus.value = null
    }

    private fun memoryExtractionStore(): MemoryExtractionStore {
        return object : MemoryExtractionStore {
            override suspend fun getActiveMemories(): List<Memory> = app.memoryRepo.getActiveMemories()

            override suspend fun isTombstoned(content: String, type: MemoryType): Boolean {
                return app.memoryRepo.isTombstoned(content, type)
            }

            override suspend fun insert(memory: Memory) {
                app.memoryRepo.insert(memory)
            }

            override suspend fun getById(id: String): Memory? = app.memoryRepo.getById(id)

            override suspend fun update(memory: Memory) {
                app.memoryRepo.update(memory)
            }
        }
    }

    private fun conversationMessageStore(): ConversationMessageStore {
        return object : ConversationMessageStore {
            override suspend fun saveMessage(message: ChatMessage) {
                app.conversationRepo.saveMessage(message)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            app.conversationRepo.deleteMessage(messageId)
            _messages.value = _messages.value.filter { it.id != messageId }
        }
    }

    fun updateConversationSettings(useMemory: Boolean, generateMemory: Boolean) {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            val updated = conv.copy(useMemory = useMemory, generateMemory = generateMemory, updatedAt = System.currentTimeMillis())
            app.conversationRepo.saveConversation(updated)
            _conversation.value = updated
            AppLogger.i("ChatVM", "Updated settings: useMemory=$useMemory, generateMemory=$generateMemory")
        }
    }
}
