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
        
        val message = intent.getStringExtra("msg") ?: return
        val conversationId = intent.getStringExtra("conv_id") ?: return

        Log.i("AdbInput", "=== NEW MESSAGE ===")
        Log.i("AdbInput", "msg=${message.take(50)}, conv=$conversationId")

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
                Log.i("AdbInput", "[1/4] User message saved")

                // Get API settings
                val apiKey = ds.apiKey.first()
                val baseUrl = ds.baseUrl.first()
                val model = ds.modelName.first()

                if (apiKey.isNotBlank()) {
                    val provider = OpenAICompatibleProvider(apiKey, baseUrl, model)

                    // === MEMORY RECALL ===
                    Log.i("AdbInput", "[2/4] Memory recall start")
                    val memoryEntities = db.memoryDao().getActiveMemories()
                    Log.i("AdbInput", "  DB returned ${memoryEntities.size} active memories")
                    
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
                    var recalledCount = 0

                    if (activeMemories.isNotEmpty()) {
                        val memoryEngine = MemoryEngine(provider, model)
                        val recall = memoryEngine.recall(message, activeMemories, null)
                        recalledCount = recall.memories.size
                        Log.i("AdbInput", "  Recall: scene=${recall.scene}, matched=$recalledCount")
                        
                        recall.memories.forEach { mem ->
                            Log.i("AdbInput", "  -> [${mem.type}] ${mem.content.take(50)}")
                        }

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
                                Log.i("AdbInput", "  System prompt injected (${systemPrompt.length} chars)")
                            }
                        }
                    } else {
                        Log.i("AdbInput", "  No memories in DB, skipping recall")
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
}

