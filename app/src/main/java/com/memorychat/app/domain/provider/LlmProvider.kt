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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface LlmProvider {
    fun streamChat(request: ChatRequest): Flow<ChatChunk>
    suspend fun complete(request: ChatRequest): ChatResponse
}

class OpenAICompatibleProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val modelName: String,
    private val maxTokens: Int = 8192,
    private val webSearchClient: WebSearchClient = DuckDuckGoWebSearchClient()
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
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", "web_search")
                    addProperty("description", "Search the web for current information")
                    add("parameters", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("query", JsonObject().apply {
                                addProperty("type", "string")
                                addProperty("description", "The search query")
                            })
                        })
                        add("required", com.google.gson.JsonArray().apply { add("query") })
                    })
                })
            })
        }
    }

    override fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val body = buildRequestBody(request, true)
        AppLogger.i("LlmProvider", "Stream request: model=${request.model}, webSearch=${request.enableWebSearch}")

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
        return try {
            completeInternal(request, allowWebSearchFallback = true)
        } catch (e: Exception) {
            AppLogger.e("LlmProvider", "complete() failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
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
                        if (content.isBlank() && request.enableWebSearch && allowWebSearchFallback) {
                            val query = extractWebSearchQuery(message)
                            if (!query.isNullOrBlank()) {
                                AppLogger.i("LlmProvider", "Running fallback web search: ${query.take(80)}")
                                val searchResults = runCatching {
                                    webSearchClient.search(query)
                                }.getOrElse { error ->
                                    "Search failed: ${error.javaClass.simpleName}: ${error.message}"
                                }
                                return completeInternal(
                                    request.copy(
                                        messages = request.messages + ChatMessage(
                                            role = "system",
                                            content = """
[Web Search Results]
$searchResults

Use the search results above to answer the user. If the results are empty or insufficient, say so clearly.
""".trimIndent()
                                        ),
                                        stream = false,
                                        enableWebSearch = false
                                    ),
                                    allowWebSearchFallback = false
                                )
                            }
                        }
                        return ChatResponse(content = content)
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

    private fun extractWebSearchQuery(message: JsonObject): String? {
        val toolCalls = message.getAsJsonArray("tool_calls") ?: return null
        toolCalls.forEach { element ->
            val call = element.asJsonObject
            val function = call.getAsJsonObject("function") ?: return@forEach
            if (function.get("name")?.asString != "web_search") return@forEach
            val args = function.get("arguments")?.asString ?: return@forEach
            return runCatching {
                JsonParser.parseString(args).asJsonObject.get("query")?.asString
            }.getOrNull()
        }
        return null
    }
}

interface WebSearchClient {
    fun search(query: String): String
}

class DuckDuckGoWebSearchClient : WebSearchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun search(query: String): String {
        val duckResult = runCatching { searchDuckDuckGo(query) }.getOrDefault("")
        if (duckResult.isNotBlank()) return duckResult
        val baiduResult = runCatching { searchBaidu(query) }.getOrDefault("")
        return baiduResult.ifBlank { "No useful search result returned for query: $query" }
    }

    private fun searchDuckDuckGo(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val httpRequest = Request.Builder()
            .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return "Search failed: HTTP ${response.code}"
            }
            val body = response.body?.string().orEmpty()
            return parseDuckDuckGoResult(body)
        }
    }

    private fun searchBaidu(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val httpRequest = Request.Builder()
            .url("https://www.baidu.com/s?wd=$encoded&rn=5")
            .addHeader("User-Agent", "Mozilla/5.0 (Android) MemoryChat/1.0")
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                return "Search failed: HTTP ${response.code}"
            }
            val body = response.body?.string().orEmpty()
            return parseBaiduHtml(body)
        }
    }

    private fun parseDuckDuckGoResult(body: String): String {
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return ""
        val lines = mutableListOf<String>()
        obj.get("AbstractText")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let {
            lines += "Abstract: $it"
        }
        obj.get("AbstractURL")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let {
            lines += "Source: $it"
        }
        obj.getAsJsonArray("RelatedTopics")?.take(5)?.forEach { topic ->
            val topicObj = topic.asJsonObject
            topicObj.get("Text")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let {
                lines += "- $it"
            }
            topicObj.getAsJsonArray("Topics")?.take(3)?.forEach { nested ->
                nested.asJsonObject.get("Text")?.takeUnless { it.isJsonNull }?.asString?.takeIf { text -> text.isNotBlank() }?.let {
                    lines += "- $it"
                }
            }
        }
        return lines.distinct().joinToString("\n")
    }

    private fun parseBaiduHtml(body: String): String {
        val compact = body
            .replace(Regex("\\s+"), " ")
            .replace("&nbsp;", " ")
        val titleRegex = Regex("<h3[^>]*>\\s*<a[^>]*>(.*?)</a>\\s*</h3>", RegexOption.IGNORE_CASE)
        return titleRegex.findAll(compact)
            .map { cleanHtml(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .map { "- $it" }
            .joinToString("\n")
    }

    private fun cleanHtml(value: String): String {
        return value
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}








