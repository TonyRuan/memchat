package com.memorychat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.*
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
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
            val userMsg = ChatMessage(conversationId = conv.id, role = "user", content = content)
            app.conversationRepo.saveMessage(userMsg)
            _messages.value = _messages.value + userMsg

            val memories = if (conv.useMemory) {
                val activeMemories = app.memoryRepo.getActiveMemories()
                val recall = memoryEngine?.recall(content, activeMemories, null)
                AppLogger.i("ChatVM", "Recall: scene=${recall?.scene}, count=${recall?.memories?.size}")
                recall?.memories ?: emptyList()
            } else emptyList()

            val systemPrompt = buildSystemPrompt(memories)
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
                        _messages.value = _messages.value + ChatMessage(conversationId = conv.id, role = "assistant", content = "Error: ${e.message}")
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

    private fun buildSystemPrompt(memories: List<Memory>): String {
        if (memories.isEmpty()) return ""
        return MemoryEngine.buildRecallPrompt(
            persona = null,
            preferences = memories.filter { it.type == MemoryType.PREFERENCE },
            profile = memories.filter { it.type == MemoryType.PROFILE },
            projects = memories.filter { it.type == MemoryType.PROJECT },
            summaries = memories.filter { it.type == MemoryType.SUMMARY }
        )
    }

    fun extractMemories() {
        val engine = memoryEngine ?: return
        val conv = _conversation.value ?: return
        if (!conv.generateMemory) return

        AppLogger.i("ChatVM", "Extracting memories...")
        viewModelScope.launch {
            // Reload messages from DB to ensure we have the latest
            val freshMessages = app.conversationRepo.getMessages(conv.id)
            _messages.value = freshMessages
            AppLogger.i("ChatVM", "Reloaded ${freshMessages.size} messages for extraction")
            
            val existing = app.memoryRepo.getActiveMemories()
            val result = engine.extractMemories(freshMessages, existing)
            AppLogger.i("ChatVM", "Extracted: new=${result.newMemories.size}, updates=${result.updates.size}")

            result.newMemories.forEach { candidate ->
                if (!app.memoryRepo.isTombstoned(candidate.content, candidate.type)) {
                    app.memoryRepo.insert(Memory(
                        type = candidate.type, content = candidate.content,
                        status = candidate.statusSuggestion, importance = candidate.importance,
                        confidence = candidate.confidence, sourceConversationId = conv.id
                    ))
                }
            }
            result.updates.forEach { update ->
                val existing = app.memoryRepo.getById(update.targetMemoryId)
                if (existing != null) {
                    app.memoryRepo.update(existing.copy(content = update.newContent, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            app.conversationRepo.deleteMessage(messageId)
            _messages.value = _messages.value.filter { it.id != messageId }
        }
    }
}



