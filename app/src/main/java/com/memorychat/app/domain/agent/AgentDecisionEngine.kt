package com.memorychat.app.domain.agent

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.Persona
import com.memorychat.app.domain.provider.LlmProvider
import com.memorychat.app.util.AppLogger

data class AgentDecision(
    val toolCalls: List<AgentToolCall> = emptyList(),
    val temporaryResponseFormat: String? = null,
    val shouldContinueChat: Boolean = true
) {
    fun usesWebSearch(): Boolean = toolCalls.any { it.name == "web_search" }
}

data class AgentToolCall(
    val name: String,
    val arguments: Map<String, Any?> = emptyMap()
) {
    fun stringArg(key: String): String? = arguments[key] as? String
    fun stringListArg(key: String): List<String> = (arguments[key] as? List<*>)
        ?.filterIsInstance<String>()
        ?: emptyList()
}

class AgentDecisionEngine(
    private val llmProvider: LlmProvider,
    private val modelName: String
) {
    suspend fun decide(userMessage: String, currentPersona: Persona?): AgentDecision {
        if (userMessage.isBlank()) return AgentDecision()
        return try {
            val response = llmProvider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = buildPrompt(userMessage, currentPersona))),
                    model = modelName,
                    stream = false
                )
            )
            parseDecision(response.content)
        } catch (e: Exception) {
            AppLogger.w("AgentDecision", "Decision failed: ${e.javaClass.simpleName}: ${e.message}")
            AgentDecision()
        }
    }

    private fun buildPrompt(userMessage: String, currentPersona: Persona?): String {
        val personaContext = currentPersona?.let {
            """
Current assistant persona:
Name: ${it.name}
Role: ${it.role}
Tone: ${it.tone}
Rules: ${it.behaviorRules.joinToString("; ")}
Boundaries: ${it.boundaries.joinToString("; ")}
""".trimIndent()
        } ?: "Current assistant persona: (unknown)"

        return """
You are MemoryChat's local agent tool router. Decide by semantic meaning, not by keyword matching.

$personaContext

Allowed tools:
- get_current_time: read the device current date/time for this answer.
- search_docs: read app/project documentation for this answer only.
- web_search: search the live web for current public information using the model provider's built-in search tool.
- recall_memory: request relevant long-term memories when needed.
- update_persona: update the assistant persona name, role, tone, behavior rules, or boundaries.
- save_memory: save a stable long-term user/profile/project/preference fact.
- set_user_addressing_preference: save how the user wants the assistant to address them. Never update assistant persona for this.

Rules:
- Assistant persona is the assistant identity and behavior. User addressing is how the assistant calls the user.
- One-off formatting requests such as Markdown, lists, or code blocks only set temporary_response_format.
- Document search results are temporary context and must not become long-term memory unless the user explicitly asks to remember the conclusion.
- Return strict JSON only.

JSON schema:
{
  "tool_calls": [
    {"name": "update_persona", "arguments": {"name": null, "role": null, "tone": null, "behavior_rules": [], "boundaries": []}},
    {"name": "save_memory", "arguments": {"type": "profile|preference|project|summary", "content": "...", "importance": 3, "confidence": 0.8}},
    {"name": "set_user_addressing_preference", "arguments": {"addressing": "..."}},
    {"name": "search_docs", "arguments": {"query": "..."}},
    {"name": "recall_memory", "arguments": {"query": "...", "types": ["profile|preference|project|summary"], "limit": 5}},
    {"name": "web_search", "arguments": {"query": "..."}},
    {"name": "get_current_time", "arguments": {}}
  ],
  "temporary_response_format": "markdown|plain|null",
  "should_continue_chat": true
}

User message:
$userMessage
""".trimIndent()
    }

    private fun parseDecision(raw: String): AgentDecision {
        val obj = extractJsonObject(raw)
        val calls = obj.getAsJsonArray("tool_calls")
            ?.mapNotNull { it.asJsonObject.toToolCall() }
            ?.filter { it.name in allowedTools }
            ?: emptyList()
        val format = obj.stringOrNull("temporary_response_format")?.takeUnless { it == "null" }
        val shouldContinue = obj.get("should_continue_chat")?.takeUnless { it.isJsonNull }?.asBoolean ?: true
        return AgentDecision(
            toolCalls = calls,
            temporaryResponseFormat = format,
            shouldContinueChat = shouldContinue
        )
    }

    private fun JsonObject.toToolCall(): AgentToolCall? {
        val name = stringOrNull("name") ?: return null
        val arguments = getAsJsonObject("arguments")?.toMap() ?: emptyMap()
        return AgentToolCall(name, arguments)
    }

    private fun JsonObject.toMap(): Map<String, Any?> {
        return entrySet().associate { (key, value) -> key to value.toKotlinValue() }
    }

    private fun JsonElement.toKotlinValue(): Any? {
        if (isJsonNull) return null
        if (isJsonArray) {
            return asJsonArray.mapNotNull { it.toKotlinValue() as? String }
        }
        if (isJsonPrimitive) {
            val primitive = asJsonPrimitive
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> primitive.asNumber
                else -> primitive.asString
            }
        }
        return null
    }

    private fun extractJsonObject(raw: String): JsonObject {
        val trimmed = raw.trim()
        runCatching { return JsonParser.parseString(trimmed).asJsonObject }
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        codeBlockPattern.find(trimmed)?.let { match ->
            runCatching { return JsonParser.parseString(match.groupValues[1].trim()).asJsonObject }
        }
        val firstBrace = trimmed.indexOf('{')
        if (firstBrace >= 0) {
            var depth = 0
            for (i in firstBrace until trimmed.length) {
                when (trimmed[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth == 0) {
                    return JsonParser.parseString(trimmed.substring(firstBrace, i + 1)).asJsonObject
                }
            }
        }
        throw IllegalArgumentException("No JSON object found")
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return element.asString
    }

    companion object {
        val allowedTools = setOf(
            "get_current_time",
            "search_docs",
            "web_search",
            "recall_memory",
            "update_persona",
            "save_memory",
            "set_user_addressing_preference"
        )
    }
}
