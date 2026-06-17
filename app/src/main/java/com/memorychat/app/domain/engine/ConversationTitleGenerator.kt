package com.memorychat.app.domain.engine

object ConversationTitleGenerator {
    const val PLACEHOLDER_TITLE = "新会话"

    private const val LOCAL_TITLE_MAX_CHARS = 16
    private const val SMART_TITLE_MAX_CHARS = 12

    fun localTitleFromUserMessage(message: String): String {
        val normalized = normalize(message)
        if (normalized.isBlank()) return PLACEHOLDER_TITLE
        return ellipsize(normalized, LOCAL_TITLE_MAX_CHARS)
    }

    fun smartTitleFromModelOutput(raw: String): String {
        val cleaned = normalize(
            raw.lineSequence()
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
                .removePrefix("标题：")
                .removePrefix("标题:")
                .trim('"', '\'', '“', '”', '。', '.', '，', ',')
        )
        if (cleaned.isBlank()) return PLACEHOLDER_TITLE
        return cleaned.take(SMART_TITLE_MAX_CHARS)
    }

    fun canAutoReplace(currentTitle: String, knownAutoTitle: String): Boolean {
        val title = currentTitle.trim()
        return title == PLACEHOLDER_TITLE || title == knownAutoTitle.trim()
    }

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('。', '.', '，', ',', '！', '!', '？', '?', ':', '：', '"', '\'', '“', '”')

    private fun ellipsize(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else value.take(maxChars) + "…"
}
