package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import java.time.Instant

object ChatPromptBuilder {

    fun build(
        memories: List<Memory>,
        persona: Persona? = null,
        toolResults: List<String> = emptyList(),
        appliedActionLines: List<String> = emptyList(),
        temporaryResponseFormat: String? = null,
        rollingSummary: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        return decorate(
            basePrompt = buildBasePrompt(memories = memories, persona = persona),
            toolResults = toolResults,
            appliedActionLines = appliedActionLines,
            temporaryResponseFormat = temporaryResponseFormat,
            rollingSummary = rollingSummary,
            nowMillis = nowMillis
        )
    }

    fun buildBasePrompt(
        memories: List<Memory>,
        persona: Persona? = null
    ): String {
        return MemoryEngine.buildRecallPrompt(
            persona = persona,
            preferences = memories.filter { it.type == MemoryType.PREFERENCE },
            profile = memories.filter { it.type == MemoryType.PROFILE },
            projects = memories.filter { it.type == MemoryType.PROJECT },
            summaries = memories.filter { it.type == MemoryType.SUMMARY }
        )
    }

    fun decorate(
        basePrompt: String,
        toolResults: List<String> = emptyList(),
        appliedActionLines: List<String> = emptyList(),
        temporaryResponseFormat: String? = null,
        rollingSummary: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        return buildString {
            append(basePrompt)
            appendLine()
            if (rollingSummary.isNotBlank()) {
                appendLine("[Rolling Conversation Summary]")
                appendLine(rollingSummary)
                appendLine()
            }
            appendLine("[Environment]")
            appendLine("Current time: ${Instant.ofEpochMilli(nowMillis)}")
            if (temporaryResponseFormat != null) {
                appendLine("Temporary response format for this answer: $temporaryResponseFormat")
            }
            if (toolResults.isNotEmpty()) {
                appendLine()
                appendLine("[Tool Results]")
                toolResults.forEach { appendLine("- $it") }
            }
            if (appliedActionLines.isNotEmpty()) {
                appendLine()
                appendLine("[Applied Actions]")
                appendLine("These actions have already been applied to local app state. Treat them as observations, not suggestions.")
                appliedActionLines.forEach { appendLine("- $it") }
            }
        }
    }
}
