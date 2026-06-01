package com.memorychat.app.domain.engine

object PersonaUpdateAcknowledger {
    fun acknowledge(instruction: PersonaInstruction): String {
        return instruction.name
            ?.takeIf { it.isNotBlank() }
            ?.let { "好的，已经改名为「$it」。" }
            ?: "好的，Persona 设置已更新。"
    }
}
