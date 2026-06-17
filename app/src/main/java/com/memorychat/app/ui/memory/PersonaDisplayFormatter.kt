package com.memorychat.app.ui.memory

import com.memorychat.app.domain.model.Persona

object PersonaDisplayFormatter {
    fun fields(persona: Persona): List<Pair<String, String>> {
        return buildList {
            addIfPresent("描述", persona.description)
            addIfPresent("角色", persona.role)
            addIfPresent("语气", persona.tone)
            addIfPresent("规则", persona.behaviorRules.joinToString("；"))
            addIfPresent("边界", persona.boundaries.joinToString("；"))
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
}
