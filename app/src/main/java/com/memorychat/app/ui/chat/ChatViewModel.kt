package com.memorychat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.*
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
import com.memorychat.app.domain.engine.ChatTurnErrorPersister
import com.memorychat.app.domain.engine.ConversationMessageStore
import com.memorychat.app.domain.engine.MemoryExtractionSaver
import com.memorychat.app.domain.engine.MemoryExtractionStore
import com.memorychat.app.domain.engine.MemoryEngine
import com.memorychat.app.util.AppLogger
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

    private var llmProvider: OpenAICompatibleProvider? = null
    private var memoryEngine: MemoryEngine? = null
    private var streamJob: Job? = null

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

        AppLogger.i("ChatVM", "Sending: ${content.take(50)}")

        viewModelScope.launch {
            // 加载当前会话绑定的 Persona
            val persona = conv.personaId?.let { app.personaRepo.getPersona(it) }
            AppLogger.i("ChatVM", "Persona loaded: ${persona?.name ?: "none"}")

            val userMsg = ChatMessage(conversationId = conv.id, role = "user", content = content)
            app.conversationRepo.saveMessage(userMsg)
            _messages.value = _messages.value + userMsg

            val memories = if (conv.useMemory) {
                val activeMemories = app.memoryRepo.getActiveMemories()
                val recall = memoryEngine?.recall(content, activeMemories, persona)
                _lastRecallResult.value = recall
                AppLogger.i("ChatVM", "Recall: scene=${recall?.scene}, count=${recall?.memories?.size}")
                recall?.memories ?: emptyList()
            } else emptyList()

            val systemPrompt = buildSystemPrompt(memories, persona)
            val allMessages = _messages.value.map { ChatMessage(role = it.role, content = it.content) }.toMutableList()
            if (systemPrompt.isNotBlank()) {
                allMessages.add(0, ChatMessage(role = "system", content = systemPrompt))
            }

            _isGenerating.value = true
            _streamingContent.value = ""

            val model = app.settingsDataStore.modelName.first()
            AppLogger.i("ChatVM", "Starting stream, model=$model")

            streamJob = viewModelScope.launch {
                var accumulated = ""
                try {
                    provider.streamChat(ChatRequest(messages = allMessages, model = model, stream = true)).collect { chunk ->
                        if (chunk.done) {
                            AppLogger.i("ChatVM", "Stream done, length=${accumulated.length}")
                            if (accumulated.isNotBlank()) {
                                val assistantMsg = ChatMessage(conversationId = conv.id, role = "assistant", content = accumulated)
                                app.conversationRepo.saveMessage(assistantMsg)
                                _messages.value = _messages.value + assistantMsg
                                AppLogger.d("ChatVM", "Message saved to DB and added to list")
                                memoryEngine?.let { engine ->
                                    extractMemoriesForMessages(engine, conv, listOf(userMsg, assistantMsg))
                                }
                            }
                            _streamingContent.value = ""
                            accumulated = ""
                        } else {
                            accumulated += chunk.content
                            _streamingContent.value = accumulated
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("ChatVM", "Stream error: ${e.message}")
                    if (accumulated.isNotBlank()) {
                        val assistantMsg = ChatMessage(conversationId = conv.id, role = "assistant", content = accumulated)
                        app.conversationRepo.saveMessage(assistantMsg)
                        _messages.value = _messages.value + assistantMsg
                    } else {
                        val errorMsg = ChatTurnErrorPersister(conversationMessageStore())
                            .persistAssistantError(conv.id, e.message)
                        _messages.value = _messages.value + errorMsg
                    }
                    _streamingContent.value = ""
                } finally {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun stopGeneration() {
        AppLogger.i("ChatVM", "Stop requested")
        streamJob?.cancel()
        viewModelScope.launch {
            val content = _streamingContent.value
            if (content.isNotBlank()) {
                val conv = _conversation.value ?: return@launch
                val msg = ChatMessage(conversationId = conv.id, role = "assistant", content = content)
                app.conversationRepo.saveMessage(msg)
                _messages.value = _messages.value + msg
            }
            _streamingContent.value = ""
            _isGenerating.value = false
        }
    }

    private fun buildSystemPrompt(memories: List<Memory>, persona: Persona? = null): String {
        if (memories.isEmpty() && persona == null) return ""
        return MemoryEngine.buildRecallPrompt(
            persona = persona,
            preferences = memories.filter { it.type == MemoryType.PREFERENCE },
            profile = memories.filter { it.type == MemoryType.PROFILE },
            projects = memories.filter { it.type == MemoryType.PROJECT },
            summaries = memories.filter { it.type == MemoryType.SUMMARY }
        )
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
            _memoryExtractionStatus.value = if (result.newMemories.isNotEmpty() || result.updates.isNotEmpty()) {
                "记忆整理完成：新增 ${result.newMemories.size} 条，更新 ${result.updates.size} 条"
            } else {
                "没有提取到新的长期记忆"
            }
        }
    }

    private suspend fun extractMemoriesForMessages(
        engine: MemoryEngine,
        conv: Conversation,
        messages: List<ChatMessage>
    ): MemoryExtractionResult {
        return try {
            MemoryExtractionSaver(engine, memoryExtractionStore()).extractAndSave(conv, messages)
        } catch (e: Exception) {
            AppLogger.e("ChatVM", "Memory extraction failed without blocking chat: ${e.message}")
            MemoryExtractionResult()
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
