package com.memorychat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.memorychat.app.data.local.db.AppDatabase
import com.memorychat.app.data.local.datastore.SettingsDataStore
import com.memorychat.app.data.local.db.entity.MessageEntity
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
import com.memorychat.app.domain.engine.MemoryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdbInputReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("msg") ?: return
        val conversationId = intent.getStringExtra("conv_id") ?: return

        Log.i("AdbInput", "Received: msg=$message, conv=$conversationId")

        val pendingResult = goAsync()
        val db = AppDatabase.getInstance(context)
        val ds = SettingsDataStore(context)

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
                Log.i("AdbInput", "User message saved")

                // Get API settings
                val apiKey = ds.apiKey.first()
                val baseUrl = ds.baseUrl.first()
                val model = ds.modelName.first()
                Log.i("AdbInput", "API config: model=$model, key=${apiKey.take(8)}...")

                if (apiKey.isNotBlank()) {
                    val provider = OpenAICompatibleProvider(apiKey, baseUrl, model)

                    // Memory recall
                    val memoryEntities = db.memoryDao().getActiveMemories()
                    val activeMemories = memoryEntities.map { entity ->
                        Memory(
                            id = entity.id,
                            type = MemoryType.valueOf(entity.type),
                            content = entity.content,
                            status = MemoryStatus.valueOf(entity.status),
                            importance = entity.importance,
                            confidence = entity.confidence,
                            sourceConversationId = entity.sourceConversationId,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            lastUsedAt = entity.lastUsedAt,
                            userEdited = entity.userEdited == 1
                        )
                    }

                    val messages = mutableListOf<ChatMessage>()

                    if (activeMemories.isNotEmpty()) {
                        val memoryEngine = MemoryEngine(provider, model)
                        val recall = memoryEngine.recall(message, activeMemories, null)

                        if (recall.memories.isNotEmpty()) {
                            val systemPrompt = MemoryEngine.buildRecallPrompt(
                                persona = null,
                                preferences = recall.memories.filter { it.type == MemoryType.PREFERENCE },
                                profile = recall.memories.filter { it.type == MemoryType.PROFILE },
                                projects = recall.memories.filter { it.type == MemoryType.PROJECT },
                                summaries = recall.memories.filter { it.type == MemoryType.SUMMARY }
                            )
                            if (systemPrompt.isNotBlank()) {
                                messages.add(ChatMessage(role = "system", content = systemPrompt))
                            }
                            Log.i("AdbInput", "Recalled ${recall.memories.size} memories, scene=${recall.scene}")
                        } else {
                            Log.i("AdbInput", "No relevant memories recalled")
                        }
                    } else {
                        Log.i("AdbInput", "No active memories")
                    }

                    messages.add(ChatMessage(role = "user", content = message))

                    val response = provider.complete(
                        ChatRequest(
                            messages = messages,
                            model = model,
                            stream = false
                        )
                    )
                    Log.i("AdbInput", "API response: ${response.content.take(50)}")

                    val assistantMsg = MessageEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "assistant",
                        content = response.content,
                        createdAt = System.currentTimeMillis()
                    )
                    db.messageDao().insert(assistantMsg)
                    Log.i("AdbInput", "Assistant message saved")
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
}
