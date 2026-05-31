package com.memorychat.app.domain.engine

import com.google.gson.JsonParser
import com.memorychat.app.domain.model.*
import com.memorychat.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.memorychat.app.domain.provider.LlmProvider

class MemoryEngine(private val llmProvider: LlmProvider, private val modelName: String = "") {

    companion object {
        const val EXTRACTION_PROMPT = """You are a long-term memory extractor. Extract stable, useful information from the conversation.

RULES:
1. ALWAYS extract when user explicitly says "remember", "note", "don't forget", "keep in mind"
2. Extract user preferences, habits, traits, goals, project info
3. Extract facts user states about themselves (name, job, location, hobbies)
4. Extract decisions and conclusions from the conversation
5. Do NOT save: temporary emotions, jokes, sensitive info, API keys, passwords, tokens
6. If user says "remember X", extract X as a memory with high confidence (0.9+)
7. Output strict JSON only"""

        fun buildRecallPrompt(
            persona: Persona?,
            preferences: List<Memory>,
            profile: List<Memory>,
            projects: List<Memory>,
            summaries: List<Memory>
        ): String {
            val sb = StringBuilder()
            sb.appendLine("Use the following long-term memory and context to answer naturally. Do not frequently mention 'according to my memory'. If memory conflicts with current message, follow the current message.")
            sb.appendLine()

            if (persona != null) {
                sb.appendLine("[Current Persona]")
                sb.appendLine("Name: ${persona.name}")
                sb.appendLine("Role: ${persona.role}")
                sb.appendLine("Tone: ${persona.tone}")
                sb.appendLine("Rules: ${persona.behaviorRules.joinToString("; ")}")
                sb.appendLine("Boundaries: ${persona.boundaries.joinToString("; ")}")
                sb.appendLine()
            }

            if (preferences.isNotEmpty()) {
                sb.appendLine("[User Preferences]")
                preferences.take(2).forEach { sb.appendLine("- ${it.content}") }
                sb.appendLine()
            }

            if (profile.isNotEmpty()) {
                sb.appendLine("[User Profile]")
                profile.take(1).forEach { sb.appendLine("- ${it.content}") }
                sb.appendLine()
            }

            if (projects.isNotEmpty()) {
                sb.appendLine("[Project Memory]")
                projects.take(3).forEach { sb.appendLine("- ${it.content}") }
                sb.appendLine()
            }

            if (summaries.isNotEmpty()) {
                sb.appendLine("[Recent Summaries]")
                summaries.take(2).forEach { sb.appendLine("- ${it.content}") }
                sb.appendLine()
            }

            return sb.toString()
        }
    }

    suspend fun extractMemories(
        messages: List<ChatMessage>,
        existingMemories: List<Memory>
    ): MemoryExtractionResult {
        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val existingText = existingMemories.joinToString("\n") { "- [${it.type}] ${it.content}" }

        val prompt = """$EXTRACTION_PROMPT

Existing memories:
$existingText

Current conversation:
$conversationText

Output JSON format:
{
  "new_memories": [{"type": "profile|preference|project|summary", "content": "...", "importance": 3, "confidence": 0.8, "status_suggestion": "active|pending", "reason": "..."}],
  "updates": [{"target_memory_id": "...", "new_content": "...", "reason": "..."}],
  "discarded": [{"content": "...", "reason": "..."}]
}"""

        val request = ChatRequest(
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            model = modelName,
            stream = false
        )

        AppLogger.i("MemoryEngine", "Extracting: msgCount=${messages.size}, model=$modelName")
        return withContext(Dispatchers.IO) {
            try {
                val response = llmProvider.complete(request)
                AppLogger.i("MemoryEngine", "Response: ${response.content.take(200)}")
                parseExtractionResult(response.content)
            } catch (e: Exception) {
                AppLogger.e("MemoryEngine", "Extract failed: ${e.javaClass.simpleName}: ${e.message}")
                MemoryExtractionResult()
            }
        }
    }

    /**
     * Try to extract a JSON object from raw LLM output.
     * Handles: direct JSON, ```json code blocks, JSON buried in surrounding text.
     */
    private fun extractJsonObject(raw: String): com.google.gson.JsonObject {
        val trimmed = raw.trim()

        // 1. Try direct parse
        try {
            return JsonParser.parseString(trimmed).asJsonObject
        } catch (_: Exception) { }

        // 2. Try stripping ```json ... ``` blocks
        val codeBlockPattern = Regex("```(?:json)?\\s*\n?(.*?)\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockPattern.find(trimmed)?.let { match ->
            try {
                return JsonParser.parseString(match.groupValues[1].trim()).asJsonObject
            } catch (_: Exception) { }
        }

        // 3. Try finding first { ... } in surrounding text
        val firstBrace = trimmed.indexOf('{')
        if (firstBrace >= 0) {
            var depth = 0
            for (i in firstBrace until trimmed.length) {
                when (trimmed[i]) { '{' -> depth++; '}' -> depth-- }
                if (depth == 0) {
                    try {
                        return JsonParser.parseString(trimmed.substring(firstBrace, i + 1)).asJsonObject
                    } catch (_: Exception) { }
                    break
                }
            }
        }

        throw IllegalArgumentException("No valid JSON object found in input")
    }

    /** Helper: get string field with alias fallback (e.g. "type" / "category"). */
    private fun com.google.gson.JsonObject.getString(vararg keys: String): String? {
        for (key in keys) {
            val el = get(key)
            if (el != null && !el.isJsonNull) return el.asString
        }
        return null
    }

    private fun parseExtractionResult(json: String): MemoryExtractionResult {
        return try {
            val obj = extractJsonObject(json)
            val newMemories = obj.getAsJsonArray("new_memories")?.map { item ->
                val o = item.asJsonObject
                MemoryCandidate(
                    type = MemoryType.valueOf(o.getString("type", "category")?.uppercase() ?: "PROFILE"),
                    content = o.getString("content", "text") ?: "",
                    importance = o.get("importance")?.asInt ?: 3,
                    confidence = o.get("confidence")?.asFloat ?: 0.8f,
                    statusSuggestion = MemoryStatus.valueOf(o.getString("status_suggestion", "status")?.uppercase() ?: "ACTIVE"),
                    reason = o.getString("reason") ?: ""
                )
            } ?: emptyList()

            val updates = obj.getAsJsonArray("updates")?.map { item ->
                val o = item.asJsonObject
                MemoryUpdate(
                    targetMemoryId = o.getString("target_memory_id", "id") ?: "",
                    newContent = o.getString("new_content", "content") ?: "",
                    reason = o.getString("reason") ?: ""
                )
            } ?: emptyList()

            val discarded = obj.getAsJsonArray("discarded")?.map { item ->
                val o = item.asJsonObject
                DiscardedInfo(
                    content = o.getString("content", "text") ?: "",
                    reason = o.getString("reason") ?: ""
                )
            } ?: emptyList()

            MemoryExtractionResult(newMemories, updates, discarded)
        } catch (e: Exception) {
            AppLogger.e("MemoryEngine", "Parse failed: ${e.javaClass.simpleName}: ${e.message}")
            MemoryExtractionResult()
        }
    }

    fun recall(
        userMessage: String,
        allActiveMemories: List<Memory>,
        persona: Persona?
    ): MemoryRecallResult {
        val scene = detectScene(userMessage)
        val recalled = when (scene) {
            "project" -> {
                allActiveMemories.filter { it.type == MemoryType.PROJECT }.take(3) +
                allActiveMemories.filter { it.type == MemoryType.SUMMARY }.take(2) +
                allActiveMemories.filter { it.type == MemoryType.PREFERENCE }.take(2)
            }
            "persona" -> {
                allActiveMemories.filter { it.type == MemoryType.PREFERENCE }.take(3)
            }
            else -> {
                allActiveMemories.filter { it.type == MemoryType.PROFILE }.take(3) +
                allActiveMemories.filter { it.type == MemoryType.PREFERENCE }.take(2) +
                allActiveMemories.filter { it.type == MemoryType.SUMMARY }.take(2)
            }
        }

        val reasons = recalled.associate { it.id to "scene[$scene] recall" }
        return MemoryRecallResult(
            memories = recalled.distinctBy { it.id }.take(8),
            scene = scene,
            reasons = reasons
        )
    }

    private fun detectScene(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("project") || lower.contains("dev") || lower.contains("code") || lower.contains("app") ||
            lower.contains("项目") || lower.contains("开发") || lower.contains("代码") || lower.contains("应用") -> "project"
            lower.contains("persona") || lower.contains("role") || lower.contains("style") ||
            lower.contains("角色") || lower.contains("风格") -> "persona"
            lower.contains("prefer") || lower.contains("like") || lower.contains("habit") ||
            lower.contains("偏好") || lower.contains("喜欢") || lower.contains("习惯") -> "preference"
            else -> "general"
        }
    }
}
