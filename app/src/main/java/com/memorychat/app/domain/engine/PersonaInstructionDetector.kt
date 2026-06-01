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

    private fun mergeList(existing: List<String>, incoming: List<String>): List<String> {
        return (existing + incoming).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
}
