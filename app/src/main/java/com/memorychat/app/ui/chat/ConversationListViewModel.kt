package com.memorychat.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MemoryChatApp

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _conversations.value = app.conversationRepo.getAllConversations()
        }
    }

    fun createConversation(title: String = "新会话", onCreated: (String) -> Unit) {
        val conv = Conversation(title = title)
        viewModelScope.launch {
            val configuredPersonaId = app.settingsDataStore.defaultPersonaId.first().takeIf { it.isNotBlank() }
            val defaultPersona = configuredPersonaId?.let { app.personaRepo.getPersona(it) }
                ?: app.getOrCreateDefaultPersona()
            val useMemory = app.settingsDataStore.defaultUseMemory.first()
            val generateMemory = app.settingsDataStore.defaultGenerateMemory.first()
            val convWithDefaults = conv.copy(
                personaId = defaultPersona.id,
                useMemory = useMemory,
                generateMemory = generateMemory
            )
            app.conversationRepo.saveConversation(convWithDefaults)
            _conversations.value = app.conversationRepo.getAllConversations()
            onCreated(convWithDefaults.id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            app.conversationRepo.deleteConversation(id)
            _conversations.value = app.conversationRepo.getAllConversations()
        }
    }
}
