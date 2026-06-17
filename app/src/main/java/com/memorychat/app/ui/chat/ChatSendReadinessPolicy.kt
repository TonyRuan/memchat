package com.memorychat.app.ui.chat

data class ChatSendReadiness(
    val accepted: Boolean,
    val message: String? = null
)

object ChatSendReadinessPolicy {
    fun evaluate(
        content: String,
        providerReady: Boolean,
        conversationLoaded: Boolean,
        generationActive: Boolean
    ): ChatSendReadiness {
        if (content.isBlank()) return ChatSendReadiness(false)
        if (!conversationLoaded) {
            return ChatSendReadiness(false, "会话还在加载，请稍后再试")
        }
        if (!providerReady) {
            return ChatSendReadiness(false, "模型配置未就绪，请先检查设置")
        }
        if (generationActive) {
            return ChatSendReadiness(false, "上一条回复还在生成中")
        }
        return ChatSendReadiness(true)
    }
}
