package com.memorychat.app.domain.agent

import com.memorychat.app.domain.engine.MemoryExtractionSaver
import com.memorychat.app.domain.engine.MemoryExtractionStore
import com.memorychat.app.domain.engine.PersonaInstruction
import com.memorychat.app.domain.engine.PersonaInstructionDetector
import com.memorychat.app.domain.engine.PersonaUpdateAcknowledger
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.Conversation
import com.memorychat.app.domain.model.MemoryCandidate
import com.memorychat.app.domain.model.MemoryExtractionResult
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import com.memorychat.app.util.AppLogger
import java.time.Instant

interface AgentPersonaStore {
    suspend fun savePersona(persona: Persona)
}

data class AgentToolExecutionResult(
    val persona: Persona,
    val toolResults: List<String> = emptyList(),
    val appliedActions: List<AppliedAgentAction> = emptyList(),
    val memoryWritten: Boolean = false
)

class AgentToolExecutor(
    private val personaStore: AgentPersonaStore,
    private val memoryStore: MemoryExtractionStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun execute(
        decision: AgentDecision,
        persona: Persona,
        conversation: Conversation,
        sourceMessages: List<ChatMessage>
    ): AgentToolExecutionResult {
        var currentPersona = persona
        val results = mutableListOf<String>()
        val appliedActions = mutableListOf<AppliedAgentAction>()
        var memoryWritten = false
        decision.toolCalls.forEach { call ->
            try {
                when (call.name) {
                    "get_current_time" -> {
                        results += "[tool:get_current_time] ${Instant.ofEpochMilli(nowMillis())}"
                    }
                    "update_persona" -> {
                        val instruction = call.toPersonaInstruction()
                        if (instruction.isEmpty()) return@forEach
                        val updated = PersonaInstructionDetector.apply(currentPersona, instruction)
                        personaStore.savePersona(updated)
                        instruction.name?.takeIf { it.isNotBlank() && it != currentPersona.name }?.let { newName ->
                            appliedActions += AppliedAgentAction(
                                type = AppliedAgentActionType.PERSONA_UPDATED,
                                target = "persona.name",
                                before = currentPersona.name,
                                after = newName,
                                userVisibleText = PersonaUpdateAcknowledger.acknowledge(
                                    PersonaInstruction(name = newName)
                                )
                            )
                        }
                        currentPersona = updated
                        results += "[tool:update_persona] applied"
                    }
                    "set_user_addressing_preference" -> {
                        val addressing = call.stringArg("addressing")?.trim().orEmpty()
                        if (addressing.isBlank()) return@forEach
                        if (!conversation.generateMemory) {
                            results += "[tool:set_user_addressing_preference] skipped: generate_memory=false"
                            return@forEach
                        }
                        saveMemory(
                            conversation = conversation,
                            sourceMessages = sourceMessages,
                            candidate = MemoryCandidate(
                                type = MemoryType.PREFERENCE,
                                content = "用户希望助手称呼自己为$addressing",
                                importance = 4,
                                confidence = 0.9f,
                                statusSuggestion = MemoryStatus.ACTIVE,
                                reason = "agent tool: user addressing preference"
                            )
                        )
                        memoryWritten = true
                        results += "[tool:set_user_addressing_preference] saved"
                    }
                    "save_memory" -> {
                        if (!conversation.generateMemory) {
                            results += "[tool:save_memory] skipped: generate_memory=false"
                            return@forEach
                        }
                        call.toMemoryCandidate()?.let { candidate ->
                            saveMemory(conversation, sourceMessages, candidate)
                            memoryWritten = true
                            results += "[tool:save_memory] saved"
                        }
                    }
                    "search_docs" -> {
                        val query = call.stringArg("query")?.trim().orEmpty()
                        if (query.isNotBlank()) {
                            results += "[tool:search_docs] query=\"$query\" result=\"文档查询接口已预留；当前版本未找到本地索引结果。\""
                        }
                    }
                    "web_search" -> {
                        val query = call.stringArg("query")?.trim().orEmpty()
                        results += if (query.isNotBlank()) {
                            "[tool:web_search] enabled for final model request; query=\"$query\""
                        } else {
                            "[tool:web_search] enabled for final model request"
                        }
                    }
                    "recall_memory" -> {
                        results += "[tool:recall_memory] handled by normal memory recall"
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("AgentTools", "Tool ${call.name} failed: ${e.javaClass.simpleName}: ${e.message}")
                results += "[tool:${call.name}] failed"
            }
        }
        return AgentToolExecutionResult(currentPersona, results, appliedActions, memoryWritten)
    }

    private suspend fun saveMemory(
        conversation: Conversation,
        sourceMessages: List<ChatMessage>,
        candidate: MemoryCandidate
    ) {
        MemoryExtractionSaver(engine = null, store = memoryStore)
            .saveResult(
                conversation = conversation,
                messages = sourceMessages,
                result = MemoryExtractionResult(newMemories = listOf(candidate))
            )
    }

    private fun AgentToolCall.toPersonaInstruction(): PersonaInstruction {
        return PersonaInstruction(
            name = stringArg("name")?.cleanToolText(),
            role = stringArg("role")?.cleanToolText(),
            tone = stringArg("tone")?.cleanToolText(),
            behaviorRules = stringListArg("behavior_rules").map { it.cleanToolText() }.filter { it.isNotBlank() },
            boundaries = stringListArg("boundaries").map { it.cleanToolText() }.filter { it.isNotBlank() }
        )
    }

    private fun AgentToolCall.toMemoryCandidate(): MemoryCandidate? {
        val content = stringArg("content")?.trim().orEmpty()
        if (content.isBlank()) return null
        return MemoryCandidate(
            type = parseMemoryType(stringArg("type")),
            content = content,
            importance = (arguments["importance"] as? Number)?.toInt() ?: 3,
            confidence = (arguments["confidence"] as? Number)?.toFloat() ?: 0.8f,
            statusSuggestion = MemoryStatus.ACTIVE,
            reason = "agent tool: save_memory"
        )
    }

    private fun parseMemoryType(raw: String?): MemoryType {
        return when (raw?.lowercase()) {
            "profile" -> MemoryType.PROFILE
            "preference" -> MemoryType.PREFERENCE
            "project" -> MemoryType.PROJECT
            "summary" -> MemoryType.SUMMARY
            else -> MemoryType.SUMMARY
        }
    }

    private fun String.cleanToolText(): String {
        return trim().trim('。', '.', '，', ',', '：', ':', '"', '\'', '“', '”')
    }
}
