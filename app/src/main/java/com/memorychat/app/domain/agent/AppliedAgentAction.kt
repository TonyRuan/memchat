package com.memorychat.app.domain.agent

enum class AppliedAgentActionType {
    PERSONA_UPDATED,
    MEMORY_WRITTEN,
    USER_ADDRESSING_UPDATED
}

data class AppliedAgentAction(
    val type: AppliedAgentActionType,
    val target: String,
    val before: String? = null,
    val after: String? = null,
    val userVisibleText: String
) {
    fun observationLine(): String {
        val change = listOfNotNull(before, after).joinToString(" -> ")
        return if (change.isBlank()) {
            "${type.name} $target"
        } else {
            "${type.name} $target: $change"
        }
    }
}

data class AgentFinalAnswerDecision(
    val shouldCallModel: Boolean,
    val directAnswer: String? = null,
    val appliedActionLines: List<String> = emptyList()
)

object AgentFinalAnswerPolicy {
    fun resolve(
        decision: AgentDecision,
        appliedActions: List<AppliedAgentAction>
    ): AgentFinalAnswerDecision {
        val appliedLines = appliedActions.map { it.observationLine() }
        val personaUpdates = appliedActions.filter { it.type == AppliedAgentActionType.PERSONA_UPDATED }
        val hasContextTool = decision.toolCalls.any { call ->
            call.name in setOf("web_search", "search_docs", "get_current_time", "recall_memory")
        }
        val onlyPersonaUpdate = appliedActions.isNotEmpty() &&
            appliedActions.size == personaUpdates.size &&
            !hasContextTool
        if (onlyPersonaUpdate) {
            val answer = personaUpdates.last().userVisibleText
            return AgentFinalAnswerDecision(
                shouldCallModel = false,
                directAnswer = answer,
                appliedActionLines = appliedLines
            )
        }
        return AgentFinalAnswerDecision(
            shouldCallModel = true,
            appliedActionLines = appliedLines
        )
    }
}
