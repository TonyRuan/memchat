package com.memorychat.app.domain.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.Persona
import com.memorychat.app.domain.provider.LlmProvider
import com.memorychat.app.util.AppLogger

class PersonaInstructionExtractor(
    private val llmProvider: LlmProvider,
    private val modelName: String
) {
    suspend fun detect(content: String, currentPersona: Persona? = null): PersonaInstruction? {
        if (content.isBlank()) return null

        return try {
            val response = llmProvider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = buildPrompt(content, currentPersona))),
                    model = modelName,
                    stream = false
                )
            )
            parseModelInstruction(response.content)
        } catch (e: Exception) {
            AppLogger.w("PersonaExtractor", "Model fallback failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildPrompt(content: String, currentPersona: Persona?): String {
        val personaContext = currentPersona?.let {
            """
Current assistant persona:
Name: ${it.name}
Role: ${it.role}
Tone: ${it.tone}
""".trimIndent()
        } ?: "Current assistant persona: (unknown)"

        return """
Classify whether a single user message updates the assistant persona.

$personaContext

Return strict JSON only:
{
  "category": "assistant_persona_update"|"user_addressing_preference"|"user_profile"|"other",
  "is_persona_instruction": true|false,
  "name": string|null,
  "role": string|null,
  "tone": string|null,
  "behavior_rules": string[],
  "boundaries": string[]
}

Rules:
- Decide by semantic meaning, not by keyword matching.
- Assistant persona means the user is naming or configuring the assistant, not describing the user.
- The user may omit the subject when continuing to rename the current assistant persona, e.g. "改成猪妞吧".
- If the message semantically sets the assistant's name, nickname, role, tone, personality, behavior rules, or boundaries, category is assistant_persona_update and is_persona_instruction is true.
- Natural wording such as "给你改名字为比比拉布" is an assistant persona update.
- If the user asks the assistant to call or address the user by a name, category is user_addressing_preference and is_persona_instruction is false.
- If the user describes their own name, preference, or profile, category is user_profile and is_persona_instruction is false.
- If the message is unrelated to assistant persona or user addressing/profile, category is other and is_persona_instruction is false.
- Keep names concise and remove trailing particles such as 吧, 啦, 哦, 呀.

User message:
$content
""".trimIndent()
    }

    private fun parseModelInstruction(raw: String): PersonaInstruction? {
        val obj = extractJsonObject(raw)
        val category = obj.stringOrNull("category") ?: if (obj.get("is_persona_instruction")?.asBoolean == true) {
            "assistant_persona_update"
        } else {
            "other"
        }
        if (category != "assistant_persona_update") return null
        if (obj.get("is_persona_instruction")?.asBoolean != true) return null
        val instruction = PersonaInstruction(
            name = obj.stringOrNull("name")?.cleanModelValue(),
            role = obj.stringOrNull("role")?.cleanModelValue(),
            tone = obj.stringOrNull("tone")?.cleanModelValue(),
            behaviorRules = obj.stringList("behavior_rules"),
            boundaries = obj.stringList("boundaries")
        )
        return instruction.takeUnless { it.isEmpty() }
    }

    private fun extractJsonObject(raw: String): JsonObject {
        val trimmed = raw.trim()
        runCatching { return JsonParser.parseString(trimmed).asJsonObject }
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

    private fun JsonObject.stringList(key: String): List<String> {
        val array = getAsJsonArray(key) ?: return emptyList()
        return array.mapNotNull { element ->
            if (element.isJsonNull) null else element.asString.cleanModelValue()
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun String.cleanModelValue(): String {
        return trim()
            .trim('。', '.', '，', ',', '：', ':', '"', '\'', '“', '”')
            .removeSuffix("吧")
            .removeSuffix("啦")
            .removeSuffix("哦")
            .removeSuffix("呀")
            .trim()
    }
}
