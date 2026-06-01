package com.memorychat.app.domain.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.provider.LlmProvider
import com.memorychat.app.util.AppLogger

class PersonaInstructionExtractor(
    private val llmProvider: LlmProvider,
    private val modelName: String
) {
    suspend fun detect(content: String): PersonaInstruction? {
        PersonaInstructionDetector.detect(content)?.let { return it }
        if (!shouldUseModelFallback(content)) return null

        return try {
            val response = llmProvider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = buildPrompt(content))),
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

    private fun shouldUseModelFallback(content: String): Boolean {
        val text = content.trim().lowercase()
        if (text.isBlank()) return false
        val userProfileName = listOf("我叫", "我的名字", "我的昵称", "my name is", "call me")
            .any { text.contains(it) }
        if (userProfileName) return false

        val assistantReference = listOf(
            "你", "你的", "给你", "把你", "帮你", "为你",
            "助手", "ai", "agent", "assistant"
        ).any { text.contains(it) }
        val personaIntent = listOf(
            "名字", "名称", "昵称", "称呼", "叫", "取名", "起名", "改名", "更名",
            "语气", "风格", "说话方式", "角色", "身份", "性格", "人设", "规则"
        ).any { text.contains(it) }
        val implicitAssistantNickname = listOf("固定昵称", "固定名称", "以后昵称", "以后名称")
            .any { text.contains(it) }

        return (assistantReference && personaIntent) || implicitAssistantNickname
    }

    private fun buildPrompt(content: String): String {
        return """
You extract assistant persona instructions from a single user message.

Return strict JSON only:
{
  "is_persona_instruction": true|false,
  "name": string|null,
  "role": string|null,
  "tone": string|null,
  "behavior_rules": string[],
  "boundaries": string[]
}

Rules:
- Assistant persona means the user is naming or configuring the assistant, not describing the user.
- If the message sets the assistant's name, nickname, role, tone, personality, behavior rules, or boundaries, return true.
- If the user describes their own name, preference, or profile, return false.
- Keep names concise and remove trailing particles such as 吧, 啦, 哦, 呀.

User message:
$content
""".trimIndent()
    }

    private fun parseModelInstruction(raw: String): PersonaInstruction? {
        val obj = extractJsonObject(raw)
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
