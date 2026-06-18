package com.memorychat.app.domain.agent

object SensitiveTextRedactor {
    private val patterns = listOf(
        Regex("(?i)[\"']?\\bauthorization\\b[\"']?\\s*:\\s*[\"']?bearer\\s+[^\\s,;\\)\\]\\}\"']+"),
        Regex("(?i)[\"']?\\b(?:[a-z0-9]+[_-])*(?:api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token|token|password|passwd|secret)\\b[\"']?\\s*[:=]\\s*[\"']?[^\\s,;\\)\\]\\}\"']+"),
        Regex("(?i)\\bsk-[a-z0-9_-]{12,}\\b"),
        Regex("\\bghp_[A-Za-z0-9_]{20,}\\b"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{20,}\\b"),
        Regex("\\bxox[baprs]-[A-Za-z0-9-]{20,}\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("\\bAIza[0-9A-Za-z_-]{20,}\\b")
    )

    fun redact(text: String): String {
        return patterns.fold(text) { current, pattern ->
            pattern.replace(current, "[redacted]")
        }
    }
}
