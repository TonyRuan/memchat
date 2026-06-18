package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryQuery
import com.memorychat.app.domain.model.MemoryRecallResult
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona

class MemoryRecallEngine {
    fun recall(
        userMessage: String,
        allActiveMemories: List<Memory>,
        persona: Persona?
    ): MemoryRecallResult {
        return recall(
            query = MemoryQuery(text = userMessage, limit = DEFAULT_LIMIT),
            allActiveMemories = allActiveMemories,
            persona = persona
        )
    }

    fun recall(
        query: MemoryQuery,
        allActiveMemories: List<Memory>,
        persona: Persona?
    ): MemoryRecallResult {
        val scene = detectScene(query.text)
        val quotas = if (query.types == null) sceneQuotas(scene) else null
        val allowedTypes = query.types?.toSet() ?: quotas?.keys
        val queryTerms = tokenize(query.text)
        val scoredMemories = allActiveMemories
            .asSequence()
            .filter { memory -> allowedTypes == null || memory.type in allowedTypes }
            .distinctBy { it.id }
            .map { memory ->
                val queryScore = queryScore(memory.content, queryTerms)
                ScoredMemory(
                    memory = memory,
                    queryScore = queryScore,
                    sceneScore = sceneScore(scene, memory.type),
                    importance = memory.importance
                )
            }
            .toList()
        val quotaLimited = quotas?.flatMap { (type, quota) ->
            scoredMemories
                .filter { it.memory.type == type }
                .sortedWith(scoreComparator)
                .take(quota)
        } ?: scoredMemories
        val scored = quotaLimited
            .sortedWith(scoreComparator)
            .take(query.limit.coerceIn(1, DEFAULT_LIMIT))
            .toList()

        val reasons = scored.associate { scoredMemory ->
            val reason = if (scoredMemory.queryScore > 0) {
                "query match score=${scoredMemory.queryScore}; scene[$scene] recall"
            } else {
                "scene[$scene] fallback"
            }
            scoredMemory.memory.id to reason
        }

        return MemoryRecallResult(
            memories = scored.map { it.memory },
            scene = scene,
            reasons = reasons
        )
    }

    private fun detectScene(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("记忆") || lower.contains("记住") || lower.contains("记得") ||
                lower.contains("还记得") || lower.contains("remember") || lower.contains("memory") -> "memory_query"
            lower.contains("project") || lower.contains("dev") || lower.contains("code") || lower.contains("app") ||
                lower.contains("项目") || lower.contains("开发") || lower.contains("代码") || lower.contains("应用") -> "project"
            lower.contains("persona") || lower.contains("role") || lower.contains("style") ||
                lower.contains("角色") || lower.contains("风格") -> "persona"
            lower.contains("prefer") || lower.contains("like") || lower.contains("habit") ||
                lower.contains("偏好") || lower.contains("喜欢") || lower.contains("习惯") -> "preference"
            else -> "general"
        }
    }

    private fun sceneQuotas(scene: String): Map<MemoryType, Int>? {
        return when (scene) {
            "memory_query" -> linkedMapOf(
                MemoryType.PROJECT to 3,
                MemoryType.PREFERENCE to 3,
                MemoryType.PROFILE to 2,
                MemoryType.SUMMARY to 2
            )
            "project" -> linkedMapOf(
                MemoryType.PROJECT to 3,
                MemoryType.SUMMARY to 2,
                MemoryType.PREFERENCE to 2
            )
            "persona", "preference" -> linkedMapOf(MemoryType.PREFERENCE to 3)
            else -> linkedMapOf(
                MemoryType.PROFILE to 3,
                MemoryType.PREFERENCE to 2,
                MemoryType.SUMMARY to 2
            )
        }
    }

    private fun sceneScore(scene: String, type: MemoryType): Int {
        return when (scene) {
            "memory_query" -> when (type) {
                MemoryType.PROJECT -> 40
                MemoryType.PREFERENCE -> 30
                MemoryType.PROFILE -> 20
                MemoryType.SUMMARY -> 10
            }
            "project" -> when (type) {
                MemoryType.PROJECT -> 40
                MemoryType.SUMMARY -> 30
                MemoryType.PREFERENCE -> 20
                MemoryType.PROFILE -> 0
            }
            "persona", "preference" -> when (type) {
                MemoryType.PREFERENCE -> 40
                else -> 0
            }
            else -> when (type) {
                MemoryType.PROFILE -> 40
                MemoryType.PREFERENCE -> 30
                MemoryType.SUMMARY -> 20
                MemoryType.PROJECT -> 0
            }
        }
    }

    private fun tokenize(text: String): Set<String> {
        val lower = text.lowercase()
        val latin = Regex("[a-z0-9_+-]{2,}").findAll(lower).map { it.value }
        val cjk = Regex("[\\p{IsHan}]+").findAll(lower).flatMap { match ->
            val chars = match.value.toList()
            sequence {
                for (size in 2..4) {
                    if (chars.size >= size) {
                        chars.windowed(size).forEach { window -> yield(window.joinToString("")) }
                    }
                }
            }
        }
        return (latin + cjk)
            .filterNot { it in STOP_TERMS }
            .toSet()
    }

    private fun queryScore(content: String, queryTerms: Set<String>): Int {
        if (queryTerms.isEmpty()) return 0
        val normalized = content.lowercase()
        return queryTerms.fold(0) { total, term ->
            total + if (normalized.contains(term)) {
                when {
                    term.length >= 4 -> 6
                    term.length == 3 -> 4
                    else -> 2
                }
            } else {
                0
            }
        }
    }

    private data class ScoredMemory(
        val memory: Memory,
        val queryScore: Int,
        val sceneScore: Int,
        val importance: Int
    )

    companion object {
        const val DEFAULT_LIMIT = 8
        private val scoreComparator = compareByDescending<ScoredMemory> { it.queryScore }
            .thenByDescending { it.sceneScore }
            .thenByDescending { it.importance }
            .thenByDescending { it.memory.updatedAt }
        private val STOP_TERMS = setOf(
            "这个",
            "那个",
            "什么",
            "怎么",
            "项目",
            "记忆",
            "记得",
            "remember",
            "memory"
        )
    }
}
