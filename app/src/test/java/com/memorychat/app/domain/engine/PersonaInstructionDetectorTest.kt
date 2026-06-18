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

    @Test
    fun replacesListFieldsWhenInstructionProvidesNewLists() {
        val persona = Persona(
            id = "p1",
            name = "技术伙伴",
            expertise = listOf("Android", "测试"),
            behaviorRules = listOf("结论先行", "先问问题"),
            boundaries = listOf("不假装知道"),
            toolPolicy = listOf("总是先搜索"),
            memoryPolicy = listOf("所有内容都写记忆"),
            exampleDialogues = listOf("旧示例")
        )
        val instruction = PersonaInstruction(
            expertise = listOf("Agent 设计"),
            behaviorRules = listOf("先给可执行结论"),
            boundaries = listOf("不编造验证结果"),
            toolPolicy = listOf("需要真实验证时主动使用工具"),
            memoryPolicy = listOf("人格设置只写 Persona"),
            exampleDialogues = listOf("用户：你是谁？\n助手：我是技术伙伴")
        )

        val updated = PersonaInstructionDetector.apply(persona, instruction)

        assertEquals(listOf("Agent 设计"), updated.expertise)
        assertEquals(listOf("先给可执行结论"), updated.behaviorRules)
        assertEquals(listOf("不编造验证结果"), updated.boundaries)
        assertEquals(listOf("需要真实验证时主动使用工具"), updated.toolPolicy)
        assertEquals(listOf("人格设置只写 Persona"), updated.memoryPolicy)
        assertEquals(listOf("用户：你是谁？\n助手：我是技术伙伴"), updated.exampleDialogues)
    }
}
