package com.memorychat.app.ui.memory

import com.memorychat.app.domain.model.Persona

object PersonaDisplayFormatter {
    fun fields(persona: Persona): List<Pair<String, String>> {
        return buildList {
            addIfPresent("描述", persona.description)
            addIfPresent("角色", persona.role)
            addIfPresent("使命", persona.mission)
            addIfPresent("专长", persona.expertise.joinToString("；"))
            addIfPresent("语气", persona.tone)
            addIfPresent("沟通风格", persona.communicationStyle)
            addIfPresent("规则", persona.behaviorRules.joinToString("；"))
            addIfPresent("边界", persona.boundaries.joinToString("；"))
            addIfPresent("工具策略", persona.toolPolicy.joinToString("；"))
            addIfPresent("记忆策略", persona.memoryPolicy.joinToString("；"))
            addIfPresent("示例对话", persona.exampleDialogues.joinToString("\n\n"))
        }
    }

    private fun MutableList<Pair<String, String>>.addIfPresent(label: String, value: String?) {
        val text = value?.trim().orEmpty()
        if (text.isNotBlank()) {
            add(label to text)
        }
    }

    fun parseListField(value: String): List<String> {
        return value
            .split("；", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun parseExampleDialogues(value: String): List<String> {
        return value
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
