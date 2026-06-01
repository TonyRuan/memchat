package com.memorychat.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaUpdateAcknowledgerTest {
    @Test
    fun acknowledgesNameUpdateWithoutRefusalLanguage() {
        val message = PersonaUpdateAcknowledger.acknowledge(PersonaInstruction(name = "噜噜"))

        assertEquals("好的，已经改名为「噜噜」。", message)
        assertTrue(!message.contains("不愿意"))
        assertTrue(!message.contains("不能"))
    }

    @Test
    fun acknowledgesNonNamePersonaUpdate() {
        val message = PersonaUpdateAcknowledger.acknowledge(PersonaInstruction(tone = "冷静直接"))

        assertEquals("好的，Persona 设置已更新。", message)
    }
}
