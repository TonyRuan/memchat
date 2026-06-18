package com.memorychat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.agent.AgentDecision
import com.memorychat.app.domain.agent.AgentDecisionEngine
import com.memorychat.app.domain.agent.AgentFinalAnswerPolicy
import com.memorychat.app.domain.agent.AgentHistorySearchStore
import com.memorychat.app.domain.agent.AgentPersonaStore
import com.memorychat.app.domain.agent.AgentToolExecutor
import com.memorychat.app.domain.model.*
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
import com.memorychat.app.domain.engine.ChatTurnErrorPersister
import com.memorychat.app.domain.engine.ChatContextWindowConfig
import com.memorychat.app.domain.engine.ChatContextWindowManager
import com.memorychat.app.domain.engine.ChatContextWindowResult
import com.memorychat.app.domain.engine.ContextLengthErrorDetector
import com.memorychat.app.domain.engine.ConversationTitleGenerator
import com.memorychat.app.domain.engine.ConversationMessageStore
import com.memorychat.app.domain.engine.LlmConversationContextSummarizer
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

    private val _activeToolTrace = MutableStateFlow<ToolTrace?>(null)
    val activeToolTrace: StateFlow<ToolTrace?> = _activeToolTrace

    private val _completedToolTraces = MutableStateFlow<Map<String, ToolTrace>>(emptyMap())
    val completedToolTraces: StateFlow<Map<String, ToolTrace>> = _completedToolTraces

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation

    private val _lastRecallResult = MutableStateFlow<MemoryRecallResult?>(null)
    val lastRecallResult: StateFlow<MemoryRecallResult?> = _lastRecallResult

    private val _memoryExtractionStatus = MutableStateFlow<String?>(null)
    val memoryExtractionStatus: StateFlow<String?> = _memoryExtractionStatus
    private val _chatStatusMessage = MutableStateFlow<String?>(null)
    val chatStatusMessage: StateFlow<String?> = _chatStatusMessage
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
            val temperature = app.settingsDataStore.temperature.first().toDouble()
            val topP = app.settingsDataStore.topP.first().toDouble()

            if (apiKey.isNotBlank()) {
                llmProvider = OpenAICompatibleProvider(apiKey, baseUrl, model, maxTokens, temperature, topP)
                memoryEngine = MemoryEngine(llmProvider!!, model)
                AppLogger.i("ChatVM", "Provider ready: $model")
            }
        }
    }

    fun sendMessage(content: String): Boolean {
        val provider = llmProvider
        val conv = _conversation.value
        val readiness = ChatSendReadinessPolicy.evaluate(
            content = content,
            providerReady = provider != null,
            conversationLoaded = conv != null,
            generationActive = _isGenerating.value
        )
        if (!readiness.accepted) {
            readiness.message?.let { _chatStatusMessage.value = it }
            AppLogger.e("ChatVM", "Cannot send: provider=$provider, conv=$conv, generating=${_isGenerating.value}")
            return false
        }
        if (!sendGate.tryStart()) {
            AppLogger.w("ChatVM", "Ignoring send while generation is active")
            _chatStatusMessage.value = "上一条回复还在生成中"
            return false
        }
        if (provider == null || conv == null) {
            sendGate.finish()
            return false
        }

        AppLogger.i("ChatVM", "Sending: ${content.take(50)}")
        _isGenerating.value = true
        _streamingContent.value = ""
        _activeToolTrace.value = ToolTrace.thinking()

        viewModelScope.launch {
            try {
                // 加载当前会话绑定的 Persona
                val (activeConv, loadedPersona) = ensureConversationPersona(conv)
                val model = app.settingsDataStore.modelName.first()
                AppLogger.i("ChatVM", "Persona loaded: ${loadedPersona.name}")

                val userMsg = ChatMessage(conversationId = activeConv.id, role = "user", content = content)
                app.conversationRepo.saveMessage(userMsg)
                _messages.value = _messages.value + userMsg
                val localAutoTitle = ConversationTitleGenerator.localTitleFromUserMessage(content)
                updateConversationTitleIfAuto(
                    conversationId = activeConv.id,
                    newTitle = localAutoTitle,
                    knownAutoTitle = ConversationTitleGenerator.PLACEHOLDER_TITLE
                )

                val decision = AgentDecisionEngine(provider, model).decide(content, loadedPersona)
                _activeToolTrace.value = runningTraceForDecision(decision, content)
                val toolExecution = executeAgentTools(decision, loadedPersona, activeConv, listOf(userMsg))
                val persona = toolExecution.persona
                AppLogger.i("ChatVM", "Agent tools: calls=${decision.toolCalls.size}, results=${toolExecution.toolResults.size}")
                val finalAnswerDecision = AgentFinalAnswerPolicy.resolve(decision, toolExecution.appliedActions)
                if (!finalAnswerDecision.shouldCallModel && finalAnswerDecision.directAnswer != null) {
                    val assistantMsg = ChatMessage(
                        conversationId = activeConv.id,
                        role = "assistant",
                        content = finalAnswerDecision.directAnswer
                    )
                    app.conversationRepo.saveMessage(assistantMsg)
                    _messages.value = _messages.value + assistantMsg
                    completedTraceForTurn()?.let { trace ->
                        _completedToolTraces.value = _completedToolTraces.value + (assistantMsg.id to trace)
                    }
                    maybeGenerateSmartTitle(activeConv.id, userMsg, assistantMsg)
                    AppLogger.i("ChatVM", "Direct agent action answer saved")
                    finishGeneration()
                    return@launch
                }

                val recall = if (activeConv.useMemory) {
                    val activeMemories = app.memoryRepo.getActiveMemories()
                    val recall = memoryEngine?.recall(content, activeMemories, persona)
                    _lastRecallResult.value = recall
                    AppLogger.i("ChatVM", "Recall: scene=${recall?.scene}, count=${recall?.memories?.size}")
                    if (_activeToolTrace.value == null && !recall?.memories.isNullOrEmpty()) {
                        _activeToolTrace.value = ToolTrace.memoryRecall(recall!!.memories.size)
                    }
                    recall ?: MemoryRecallResult()
                } else MemoryRecallResult()

                val preparedContext = prepareChatRequestMessages(
                    provider = provider,
                    model = model,
                    conv = activeConv,
                    memories = recall.memories,
                    persona = persona,
                    toolResults = toolExecution.toolResults,
                    appliedActionLines = finalAnswerDecision.appliedActionLines,
                    temporaryResponseFormat = decision.temporaryResponseFormat
                )
                saveDebugSnapshot(activeConv.id, recall, preparedContext.contextWindow)

                AppLogger.i(
                    "ChatVM",
                    "Starting stream, model=$model, contextMessages=${preparedContext.contextWindow.messages.size}, rollingSummary=${preparedContext.contextWindow.summary.isNotBlank()}"
                )

                streamJob = viewModelScope.launch {
                    try {
                        val finalContent = streamAssistantContent(
                            provider = provider,
                            messages = preparedContext.messages,
                            model = model,
                            enableWebSearch = decision.usesWebSearch(),
                            userText = content
                        )
                        saveAssistantFinalContent(
                            activeConv = activeConv,
                            userMsg = userMsg,
                            finalContent = finalContent,
                            memoryWritten = toolExecution.memoryWritten
                        )
                    } catch (e: CancellationException) {
                        AppLogger.i("ChatVM", "Stream cancelled")
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("ChatVM", "Stream error: ${e.message}")
                        val partialContent = _streamingContent.value
                        if (partialContent.isBlank() && ContextLengthErrorDetector.isContextLengthExceeded(e.message)) {
                            try {
                                val retryContext = prepareChatRequestMessages(
                                    provider = provider,
                                    model = model,
                                    conv = activeConv,
                                    memories = recall.memories,
                                    persona = persona,
                                    toolResults = toolExecution.toolResults,
                                    appliedActionLines = finalAnswerDecision.appliedActionLines,
                                    temporaryResponseFormat = decision.temporaryResponseFormat,
                                    forceCompression = true
                                )
                                saveDebugSnapshot(activeConv.id, recall, retryContext.contextWindow, retryAfterContextLimit = true)
                                _chatStatusMessage.value = "上下文过长，已压缩后重试"
                                val retryContent = streamAssistantContent(
                                    provider = provider,
                                    messages = retryContext.messages,
                                    model = model,
                                    enableWebSearch = decision.usesWebSearch(),
                                    userText = content
                                )
                                saveAssistantFinalContent(
                                    activeConv = activeConv,
                                    userMsg = userMsg,
                                    finalContent = retryContent,
                                    memoryWritten = toolExecution.memoryWritten
                                )
                            } catch (retryError: Exception) {
                                AppLogger.e("ChatVM", "Retry after context compression failed: ${retryError.message}")
                                persistStreamFailure(activeConv.id, retryError, _streamingContent.value)
                            }
                        } else {
                            persistStreamFailure(activeConv.id, e, partialContent)
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
                _activeToolTrace.value = null
                finishGeneration()
            }
        }
        return true
    }

    private fun finishGeneration() {
        sendGate.finish()
        _streamingContent.value = ""
        _activeToolTrace.value = null
        _isGenerating.value = false
    }

    private suspend fun updateConversationTitleIfAuto(
        conversationId: String,
        newTitle: String,
        knownAutoTitle: String
    ): Conversation? {
        val updated = app.conversationRepo.updateConversationTitleIfAuto(
            conversationId = conversationId,
            newTitle = newTitle,
            knownAutoTitle = knownAutoTitle
        )
        if (updated != null && _conversation.value?.id == updated.id) {
            _conversation.value = updated
        }
        return updated
    }

    private fun maybeGenerateSmartTitle(
        conversationId: String,
        userMsg: ChatMessage,
        assistantMsg: ChatMessage
    ) {
        val provider = llmProvider ?: return
        val localAutoTitle = ConversationTitleGenerator.localTitleFromUserMessage(userMsg.content)
        viewModelScope.launch {
            try {
                val current = app.conversationRepo.getConversation(conversationId) ?: return@launch
                if (!ConversationTitleGenerator.canAutoReplace(current.title, localAutoTitle)) {
                    return@launch
                }
                val model = app.settingsDataStore.modelName.first()
                val response = provider.complete(
                    ChatRequest(
                        messages = listOf(
                            ChatMessage(
                                role = "user",
                                content = buildTitlePrompt(userMsg.content, assistantMsg.content)
                            )
                        ),
                        model = model,
                        stream = false
                    )
                )
                val smartTitle = ConversationTitleGenerator.smartTitleFromModelOutput(response.content)
                if (smartTitle != ConversationTitleGenerator.PLACEHOLDER_TITLE) {
                    updateConversationTitleIfAuto(
                        conversationId = conversationId,
                        newTitle = smartTitle,
                        knownAutoTitle = localAutoTitle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w("ChatVM", "Smart title generation failed: ${e.message}")
            }
        }
    }

    private fun buildTitlePrompt(userContent: String, assistantContent: String): String {
        return """
            请为下面这段对话生成一个简短会话标题。
            要求：中文优先，不超过 12 个汉字或 5 个英文词；不要解释；不要加引号；只输出标题。

            用户：$userContent
            助手：${assistantContent.take(500)}
        """.trimIndent()
    }

    private data class PreparedChatContext(
        val messages: List<ChatMessage>,
        val contextWindow: ChatContextWindowResult
    )

    private suspend fun prepareChatRequestMessages(
        provider: OpenAICompatibleProvider,
        model: String,
        conv: Conversation,
        memories: List<Memory>,
        persona: Persona,
        toolResults: List<String>,
        appliedActionLines: List<String>,
        temporaryResponseFormat: String?,
        forceCompression: Boolean = false
    ): PreparedChatContext {
        val contextWindow = ChatContextWindowManager(
            LlmConversationContextSummarizer(provider, model)
        ).build(
            messages = _messages.value,
            existingSummary = app.settingsDataStore.getConversationRollingSummary(conv.id),
            summaryWatermark = app.settingsDataStore.getConversationRollingSummaryWatermark(conv.id),
            config = ChatContextWindowConfig(
                contextWindowTokens = app.settingsDataStore.contextWindowTokens.first(),
                maxCompletionTokens = app.settingsDataStore.maxTokens.first(),
                safetyMarginTokens = app.settingsDataStore.safetyMarginTokens.first(),
                compressionMessageTurnThreshold = app.settingsDataStore.compressionMessageTurnThreshold.first(),
                recentMessageCount = if (forceCompression) 4 else 12,
                forceCompression = forceCompression
            )
        )
        if (contextWindow.summaryUpdated) {
            app.settingsDataStore.saveConversationRollingSummary(conv.id, contextWindow.summary)
            app.settingsDataStore.saveConversationRollingSummaryWatermark(conv.id, contextWindow.summaryWatermark)
        }

        val systemPrompt = buildSystemPrompt(
            memories = memories,
            persona = persona,
            toolResults = toolResults,
            appliedActionLines = appliedActionLines,
            temporaryResponseFormat = temporaryResponseFormat,
            rollingSummary = contextWindow.summary
        )
        val requestMessages = contextWindow.messages
            .map { ChatMessage(role = it.role, content = it.content) }
            .toMutableList()
        if (systemPrompt.isNotBlank()) {
            requestMessages.add(0, ChatMessage(role = "system", content = systemPrompt))
        }
        return PreparedChatContext(requestMessages, contextWindow)
    }

    private suspend fun streamAssistantContent(
        provider: OpenAICompatibleProvider,
        messages: List<ChatMessage>,
        model: String,
        enableWebSearch: Boolean,
        userText: String
    ): String {
        var accumulated = ""
        provider.streamChat(
            ChatRequest(
                messages = messages,
                model = model,
                stream = true,
                enableWebSearch = enableWebSearch
            )
        ).collect { chunk ->
            if (chunk.done) {
                AppLogger.i("ChatVM", "Stream done, length=${accumulated.length}")
            } else {
                if (chunk.searchCitations.isNotEmpty() || chunk.webSearchUsage != null) {
                    _activeToolTrace.value = ToolTrace.search(
                        status = ToolTraceStatus.RUNNING,
                        query = userText,
                        usage = chunk.webSearchUsage ?: _activeToolTrace.value?.usage,
                        citations = mergeCitations(_activeToolTrace.value?.citations.orEmpty(), chunk.searchCitations)
                    )
                }
                accumulated += chunk.content
                _streamingContent.value = accumulated
            }
        }
        _streamingContent.value = ""
        return accumulated
    }

    private suspend fun saveAssistantFinalContent(
        activeConv: Conversation,
        userMsg: ChatMessage,
        finalContent: String,
        memoryWritten: Boolean
    ) {
        if (finalContent.isBlank()) {
            _activeToolTrace.value = null
            return
        }
        val assistantMsg = ChatMessage(conversationId = activeConv.id, role = "assistant", content = finalContent)
        app.conversationRepo.saveMessage(assistantMsg)
        _messages.value = _messages.value + assistantMsg
        completedTraceForTurn()?.let { trace ->
            _completedToolTraces.value = _completedToolTraces.value + (assistantMsg.id to trace)
        }
        _activeToolTrace.value = null
        AppLogger.d("ChatVM", "Message saved to DB and added to list")
        maybeGenerateSmartTitle(activeConv.id, userMsg, assistantMsg)
        if (memoryWritten) {
            saveExtractionWatermark(activeConv.id, listOf(userMsg, assistantMsg))
            AppLogger.i("ChatVM", "Memory extraction skipped: agent memory tool already wrote this turn")
        } else memoryEngine?.let { engine ->
            scheduleMemoryExtractionAfterTurn(engine, activeConv, userMsg)
        }
    }

    private suspend fun persistStreamFailure(
        conversationId: String,
        error: Exception,
        partialContent: String
    ) {
        _streamingContent.value = ""
        if (partialContent.isNotBlank()) {
            val assistantMsg = ChatMessage(conversationId = conversationId, role = "assistant", content = partialContent)
            app.conversationRepo.saveMessage(assistantMsg)
            _messages.value = _messages.value + assistantMsg
        } else {
            val errorMsg = ChatTurnErrorPersister(conversationMessageStore())
                .persistAssistantError(conversationId, error.message)
            _messages.value = _messages.value + errorMsg
        }
        _activeToolTrace.value = null
    }

    private suspend fun saveDebugSnapshot(
        conversationId: String,
        recall: MemoryRecallResult,
        contextWindow: ChatContextWindowResult,
        retryAfterContextLimit: Boolean = false
    ) {
        val snapshot = ConversationDebugSnapshot(
            conversationId = conversationId,
            updatedAt = System.currentTimeMillis(),
            recallScene = recall.scene,
            recalledMemories = recall.memories.map { memory ->
                DebugMemoryTrace(
                    id = memory.id,
                    type = memory.type,
                    content = memory.content,
                    reason = recall.reasons[memory.id].orEmpty()
                )
            },
            contextMessageCount = contextWindow.messages.size,
            rollingSummary = contextWindow.summary,
            summaryWatermark = contextWindow.summaryWatermark,
            summaryUpdated = contextWindow.summaryUpdated,
            retryAfterContextLimit = retryAfterContextLimit
        )
        app.settingsDataStore.saveConversationDebugSnapshot(conversationId, snapshot)
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
        appliedActionLines: List<String> = emptyList(),
        temporaryResponseFormat: String? = null,
        rollingSummary: String = ""
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
            if (rollingSummary.isNotBlank()) {
                appendLine("[Rolling Conversation Summary]")
                appendLine(rollingSummary)
                appendLine()
            }
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
            if (appliedActionLines.isNotEmpty()) {
                appendLine()
                appendLine("[Applied Actions]")
                appendLine("These actions have already been applied to local app state. Treat them as observations, not suggestions.")
                appliedActionLines.forEach { appendLine("- $it") }
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
        memoryStore = memoryExtractionStore(),
        historySearchStore = historySearchStore()
    ).execute(
        decision = decision,
        persona = persona,
        conversation = conv,
        sourceMessages = sourceMessages
    )

    private fun runningTraceForDecision(decision: AgentDecision, userText: String): ToolTrace? {
        if (decision.usesWebSearch()) {
            return ToolTrace.search(ToolTraceStatus.RUNNING, query = userText)
        }
        val firstTool = decision.toolCalls.firstOrNull() ?: return null
        return when (firstTool.name) {
            "update_persona" -> ToolTrace.personaUpdate(ToolTraceStatus.RUNNING)
            "save_memory", "set_user_addressing_preference" -> ToolTrace.memoryWrite(1)
            "search_docs" -> ToolTrace.docSearch(ToolTraceStatus.RUNNING, query = firstTool.arguments["query"] as? String)
            "recall_memory" -> null
            else -> null
        }
    }

    private fun completedTraceForTurn(): ToolTrace? {
        val trace = _activeToolTrace.value ?: return null
        return if (trace.status == ToolTraceStatus.COMPLETED) trace else trace.complete()
    }

    private fun mergeCitations(
        existing: List<SearchCitation>,
        incoming: List<SearchCitation>
    ): List<SearchCitation> {
        if (incoming.isEmpty()) return existing
        val seen = existing.mapNotNull { it.url ?: it.title }.toMutableSet()
        val merged = existing.toMutableList()
        incoming.forEach { citation ->
            val key = citation.url ?: citation.title
            if (seen.add(key)) merged += citation
        }
        return merged
    }

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

    fun clearChatStatusMessage() {
        _chatStatusMessage.value = null
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

    private fun historySearchStore(): AgentHistorySearchStore {
        return object : AgentHistorySearchStore {
            override suspend fun searchHistory(
                query: String,
                scope: HistorySearchScope,
                currentConversationId: String,
                beforeCreatedAt: Long,
                limit: Int
            ): List<ConversationHistoryMatch> {
                return app.conversationRepo.searchMessages(
                    query = query,
                    scope = scope,
                    currentConversationId = currentConversationId,
                    beforeCreatedAt = beforeCreatedAt,
                    limit = limit
                )
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
