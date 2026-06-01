package com.memorychat.app.domain.provider

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.memorychat.app.domain.model.*
import com.memorychat.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
            if (request.enableWebSearch) {
                add("tools", webSearchTools())
                addProperty("tool_choice", "auto")
            }
        }
        return body.toString()
    }

    private fun webSearchTools(): com.google.gson.JsonArray {
        return com.google.gson.JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "web_search")
                addProperty("max_keyword", 3)
                addProperty("force_search", true)
                addProperty("limit", 3)
            })
        }
    }

    override fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val body = buildRequestBody(request, true)
        AppLogger.i("LlmProvider", "Stream request: model=${request.model}, webSearch=${request.enableWebSearch}")

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)
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
                        val webSearchUsage = parseWebSearchUsage(json)
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val choice = choices[0].asJsonObject
                            val delta = choice.getAsJsonObject("delta")
                            if (delta != null) {
                                val searchCitations = parseSearchCitations(delta)
                                if (searchCitations.isNotEmpty() || webSearchUsage != null) {
                                    emit(
                                        ChatChunk(
                                            content = "",
                                            done = false,
                                            searchCitations = searchCitations,
                                            webSearchUsage = webSearchUsage
                                        )
                                    )
                                }
                                if (delta.has("content")) {
                                    val contentElement = delta.get("content")
                                    if (contentElement != null && !contentElement.isJsonNull) {
                                        val content = contentElement.asString
                                        if (!content.isNullOrEmpty()) {
                                            chunkCount++
                                            emit(ChatChunk(content = content, done = false))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Silently skip - JsonNull is normal in streaming
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
        return withContext(Dispatchers.IO) {
            try {
                completeInternal(request, allowWebSearchFallback = true)
            } catch (e: Exception) {
                AppLogger.e("LlmProvider", "complete() failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
    }

    private suspend fun completeInternal(
        request: ChatRequest,
        allowWebSearchFallback: Boolean
    ): ChatResponse {
        return try {
            val body = buildRequestBody(request, false)
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: "{}"
            val code = response.code
            response.close()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${responseBody.take(200)}")
            }

            try {
                val json = JsonParser.parseString(responseBody).asJsonObject
                val choices = json.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val message = choices[0].asJsonObject?.getAsJsonObject("message")
                    if (message != null && message.has("content")) {
                        val content = message.get("content")?.asString ?: ""
                        if (content.isBlank() && request.enableWebSearch) {
                            AppLogger.w("LlmProvider", "Web search returned empty non-streaming content")
                        }
                        return ChatResponse(
                            content = content,
                            searchCitations = parseSearchCitations(message),
                            webSearchUsage = parseWebSearchUsage(json)
                        )
                    }
                }
                AppLogger.w("LlmProvider", "Unexpected response format: ${responseBody.take(200)}")
                throw IllegalStateException("Unexpected response format")
            } catch (e: Exception) {
                AppLogger.e("LlmProvider", "Parse error: ${e.message}")
                throw IllegalStateException("Failed to parse completion response", e)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun parseSearchCitations(container: JsonObject?): List<SearchCitation> {
        val annotations = container?.getAsJsonArrayOrNull("annotations") ?: return emptyList()
        return annotations.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            if (obj.getStringOrNull("type") != "url_citation") return@mapNotNull null
            val title = obj.getStringOrNull("title") ?: obj.getStringOrNull("url") ?: return@mapNotNull null
            SearchCitation(
                title = title,
                url = obj.getStringOrNull("url"),
                siteName = obj.getStringOrNull("site_name") ?: obj.getStringOrNull("siteName"),
                summary = obj.getStringOrNull("summary"),
                publishTime = obj.getStringOrNull("publish_time") ?: obj.getStringOrNull("publishTime")
            )
        }
    }

    private fun parseWebSearchUsage(root: JsonObject): WebSearchUsage? {
        val usage = root.getAsJsonObjectOrNull("usage")
        val webSearchUsage = usage?.getAsJsonObjectOrNull("web_search_usage")
            ?: root.getAsJsonObjectOrNull("web_search_usage")
            ?: return null
        val keywordCount = webSearchUsage.getIntOrNull("tool_usage")
            ?: webSearchUsage.getIntOrNull("keyword_usage")
            ?: webSearchUsage.getIntOrNull("keyword_count")
        val pageCount = webSearchUsage.getIntOrNull("page_usage")
            ?: webSearchUsage.getIntOrNull("page_count")
        return if (keywordCount == null && pageCount == null) null else WebSearchUsage(keywordCount, pageCount)
    }

    private fun JsonObject.getStringOrNull(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else value.asString
    }

    private fun JsonObject.getIntOrNull(name: String): Int? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else runCatching { value.asInt }.getOrNull()
    }

    private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun JsonObject.getAsJsonArrayOrNull(name: String): com.google.gson.JsonArray? {
        val value = get(name) ?: return null
        return if (value.isJsonArray) value.asJsonArray else null
    }

}








