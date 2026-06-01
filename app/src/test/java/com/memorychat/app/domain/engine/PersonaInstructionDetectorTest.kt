package com.memorychat.app.domain.engine

import com.memorychat.app.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaInstructionDetectorTest {
    @Test
    fun detectsPersonaNameInstruction() {
        val instruction = PersonaInstructionDetector.detect("以后你叫小墨")

        assertEquals("小墨", instruction?.name)
    }

    @Test
    fun detectsPersonaToneInstruction() {
        val instruction = PersonaInstructionDetector.detect("你的语气要冷静、直接一点")

        assertEquals("冷静、直接一点", instruction?.tone)
    }

    @Test
    fun doesNotTreatUserAnswerPreferenceAsPersonaInstruction() {
        val instruction = PersonaInstructionDetector.detect("以后回答我直接一点")

        assertNull(instruction)
    }

    @Test
    fun appliesInstructionToPersonaWithoutDroppingExistingRules() {
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

    @Test
    fun identifiesPersonaLikeMemoryContent() {
        assertTrue(PersonaInstructionDetector.looksLikePersonaMemory("用户希望 AI 名字叫小墨"))
        assertTrue(PersonaInstructionDetector.looksLikePersonaMemory("助手的语气要冷静直接"))
    }
}
