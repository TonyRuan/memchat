package com.memorychat.app.domain.model

object ModelRuntimeDefaults {
    const val MIMO_V25_CONTEXT_WINDOW_TOKENS = 1_000_000
    const val MIMO_V25_MAX_COMPLETION_TOKENS = 128_000
    const val DEFAULT_SAFETY_MARGIN_TOKENS = 2_000
    const val DEFAULT_COMPRESSION_MESSAGE_TURN_THRESHOLD = 200
    const val MIMO_V25_TEMPERATURE = 1.0
    const val MIMO_V25_TOP_P = 0.95
}
