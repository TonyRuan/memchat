package com.memorychat.app.domain.provider

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.memorychat.app.domain.model.*
import com.memorychat.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

interface LlmProvider {
    fun streamChat(request: ChatRequest): Flow<ChatChunk>
    suspend fun complete(request: ChatRequest): ChatResponse
}

class OpenAICompatibleProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val modelName: String,
    private val maxTokens: Int = 8192
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun buildRequestBody(request: ChatRequest, stream: Boolean): String {
        val body = JsonObject().apply {
            addProperty("model", request.model)
            addProperty("stream", stream)
            addProperty("max_completion_tokens", maxTokens)
            val msgs = com.google.gson.JsonArray()
            request.messages.forEach { msg ->
                msgs.add(JsonObject().apply {
                    addProperty("role", msg.role)
                    addProperty("content", msg.content)
                })
            }
            add("messages", msgs)
        }
        return body.toString()
    }

    override fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val body = buildRequestBody(request, true)
        AppLogger.i("LlmProvider", "Stream request: model=${request.model}")

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            AppLogger.e("LlmProvider", "HTTP ${response.code}", errBody.take(200))
            response.close()
            throw Exception("HTTP ${response.code}: $errBody")
        }

        AppLogger.i("LlmProvider", "Stream connected")
        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        var chunkCount = 0

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()

                if (data == "[DONE]") {
                    AppLogger.i("LlmProvider", "SSE [DONE], chunks=$chunkCount")
                    break
                }

                try {
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val choice = choices[0].asJsonObject
                            val delta = choice.getAsJsonObject("delta")
                            if (delta != null && delta.has("content")) {
                                val content = delta.get("content")?.asString
                                if (!content.isNullOrEmpty()) {
                                    chunkCount++
                                    emit(ChatChunk(content = content, done = false))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("LlmProvider", "Parse error: ${e.message}")
                    }
                } catch (e: Exception) {
                    AppLogger.w("LlmProvider", "Parse error: ${e.message}")
                }
            }
        } finally {
            reader.close()
            response.close()
        }

        AppLogger.i("LlmProvider", "Emitting done, total chunks=$chunkCount")
        emit(ChatChunk(content = "", done = true))
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(request: ChatRequest): ChatResponse {
        val body = buildRequestBody(request, false)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: "{}"
        response.close()

        try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject?.getAsJsonObject("message")
                if (message != null && message.has("content")) {
                    val content = message.get("content")?.asString ?: ""
                    return ChatResponse(content = content)
                }
            }
            AppLogger.w("LlmProvider", "Unexpected response format: ${responseBody.take(200)}")
            return ChatResponse(content = "")
        } catch (e: Exception) {
            AppLogger.e("LlmProvider", "Parse error: ${e.message}")
            return ChatResponse(content = "")
        }
    }
}





