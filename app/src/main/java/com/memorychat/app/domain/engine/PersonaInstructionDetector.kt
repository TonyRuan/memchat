package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Persona

data class PersonaInstruction(
    val name: String? = null,
    val role: String? = null,
    val mission: String? = null,
    val expertise: List<String> = emptyList(),
    val tone: String? = null,
    val communicationStyle: String? = null,
    val behaviorRules: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val toolPolicy: List<String> = emptyList(),
    val memoryPolicy: List<String> = emptyList(),
    val exampleDialogues: List<String> = emptyList()
) {
    fun isEmpty(): Boolean {
        return name == null &&
            role == null &&
            mission == null &&
            expertise.isEmpty() &&
            tone == null &&
            communicationStyle == null &&
            behaviorRules.isEmpty() &&
            boundaries.isEmpty() &&
            toolPolicy.isEmpty() &&
            memoryPolicy.isEmpty() &&
            exampleDialogues.isEmpty()
    }
}

object PersonaInstructionDetector {
    fun apply(persona: Persona, instruction: PersonaInstruction): Persona {
        return persona.copy(
            name = instruction.name ?: persona.name,
            role = instruction.role ?: persona.role,
            mission = instruction.mission ?: persona.mission,
            expertise = replaceList(persona.expertise, instruction.expertise),
            tone = instruction.tone ?: persona.tone,
            communicationStyle = instruction.communicationStyle ?: persona.communicationStyle,
            behaviorRules = replaceList(persona.behaviorRules, instruction.behaviorRules),
            boundaries = replaceList(persona.boundaries, instruction.boundaries),
            toolPolicy = replaceList(persona.toolPolicy, instruction.toolPolicy),
            memoryPolicy = replaceList(persona.memoryPolicy, instruction.memoryPolicy),
            exampleDialogues = replaceList(persona.exampleDialogues, instruction.exampleDialogues),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun replaceList(existing: List<String>, incoming: List<String>): List<String> {
        val cleaned = incoming.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return cleaned.ifEmpty { existing }
    }
}
