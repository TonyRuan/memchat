package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Persona

data class PersonaInstruction(
    val name: String? = null,
    val role: String? = null,
    val tone: String? = null,
    val behaviorRules: List<String> = emptyList(),
    val boundaries: List<String> = emptyList()
) {
    fun isEmpty(): Boolean {
        return name == null && role == null && tone == null && behaviorRules.isEmpty() && boundaries.isEmpty()
    }
}

object PersonaInstructionDetector {
    private val namePatterns = listOf(
        Regex("(?:以后)?你(?:的名字)?叫[:：\\s]*([^，。,.!！?？\\n]+)"),
        Regex("你的名字(?:是|叫)[:：\\s]*([^，。,.!！?？\\n]+)"),
        Regex("(?:给你|帮你|为你)?(?:取名|起名)(?:叫|为|是)?[:：\\s]*([^，。,.!！?？\\n]+)"),
        Regex("(?:把你|给你|帮你|为你)?(?:改名|更名)(?:叫|为|成|是)?[:：\\s]*([^，。,.!！?？\\n]+)")
    )
    private val tonePatterns = listOf(
        Regex("你的(?:语气|风格|说话方式)(?:要|是|改成|设为)?[:：\\s]*([^，。,.!！?？\\n]+(?:[、,，][^，。,.!！?？\\n]+)*)"),
        Regex("你(?:以后)?(?:说话|回答)(?:要|应该)?[:：\\s]*([^，。,.!！?？\\n]+)")
    )
    private val rolePatterns = listOf(
        Regex("(?:以后)?你(?:就)?是(?:我的|一个|一名)?[:：\\s]*([^，。,.!！?？\\n]+)")
    )
    private val behaviorPatterns = listOf(
        Regex("你(?:回答|回复)(?:时|的时候)?(?:要|应该)[:：\\s]*([^，。,.!！?？\\n]+)"),
        Regex("你的(?:规则|行为规则)(?:是|要|设为)?[:：\\s]*([^。.!！?？\\n]+)")
    )

    fun detect(content: String): PersonaInstruction? {
        val text = content.trim()
        val name = firstCapture(text, namePatterns)?.clean()
        val tone = firstCapture(text, tonePatterns)?.clean()
        val role = firstCapture(text, rolePatterns)
            ?.clean()
            ?.takeUnless { it in setOf("对的", "错的", "可以", "不对", "正确的") }
        val behaviorRules = firstCapture(text, behaviorPatterns)
            ?.clean()
            ?.let { listOf(it) }
            ?: emptyList()

        val instruction = PersonaInstruction(
            name = name,
            role = role,
            tone = tone,
            behaviorRules = behaviorRules
        )
        return instruction.takeUnless { it.isEmpty() }
    }

    fun apply(persona: Persona, instruction: PersonaInstruction): Persona {
        return persona.copy(
            name = instruction.name ?: persona.name,
            role = instruction.role ?: persona.role,
            tone = instruction.tone ?: persona.tone,
            behaviorRules = mergeList(persona.behaviorRules, instruction.behaviorRules),
            boundaries = mergeList(persona.boundaries, instruction.boundaries),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun looksLikePersonaMemory(content: String): Boolean {
        val text = content.trim()
        if (detect(text) != null) return true
        val lower = text.lowercase()
        val subjectLooksLikeAssistant = listOf("ai", "assistant", "agent", "助手", "你", "你的").any { lower.contains(it) }
        val hasPersonaField = listOf("名字", "名称", "叫", "角色", "身份", "性格", "语气", "风格", "role", "tone", "persona").any { lower.contains(it) }
        return subjectLooksLikeAssistant && hasPersonaField
    }

    private fun firstCapture(text: String, patterns: List<Regex>): String? {
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)
        }
    }

    private fun String.clean(): String {
        return trim()
            .trim('。', '.', '，', ',', '：', ':', '"', '\'', '“', '”')
            .removePrefix("要")
            .removeSuffix("吧")
            .removeSuffix("啦")
            .removeSuffix("哦")
            .removeSuffix("呀")
            .trim()
    }

    private fun mergeList(existing: List<String>, incoming: List<String>): List<String> {
        return (existing + incoming).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
}
