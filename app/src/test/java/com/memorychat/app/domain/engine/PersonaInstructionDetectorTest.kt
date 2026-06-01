package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaInstructionDetectorTest {
    @Test
    fun appliesLlmInstructionToPersonaWithoutDroppingExistingRules() {
        val persona = Persona(
            id = "p1",
            name = "技术伙伴",
            role = "协作者",
            tone = "清晰",
            behaviorRules = listOf("结论先行")
        )
        val instruction = PersonaInstruction(name = "小墨", tone = "冷静、直接")

        val updated = PersonaInstructionDetector.apply(persona, instruction)

        assertEquals("小墨", updated.name)
        assertEquals("协作者", updated.role)
        assertEquals("冷静、直接", updated.tone)
        assertEquals(listOf("结论先行"), updated.behaviorRules)
        assertTrue(updated.updatedAt >= persona.updatedAt)
    }
}
