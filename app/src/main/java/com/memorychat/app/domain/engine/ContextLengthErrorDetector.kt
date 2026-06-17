package com.memorychat.app.domain.engine

object ContextLengthErrorDetector {
    fun isContextLengthExceeded(message: String?): Boolean {
        val text = message?.lowercase().orEmpty()
        return text.contains("context_length_exceeded") ||
            text.contains("maximum context length") ||
            text.contains("context window") && text.contains("exceed")
    }
}
