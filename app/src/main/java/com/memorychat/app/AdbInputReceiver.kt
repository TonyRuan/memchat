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
import com.memorychat.app.domain.provider.OpenAICompatibleProvider
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
                    val response = provider.complete(
                        ChatRequest(
                            messages = listOf(ChatMessage(role = "user", content = message)),
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
