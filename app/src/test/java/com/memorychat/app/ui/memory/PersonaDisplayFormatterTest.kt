package com.memorychat.app.ui.memory

import com.memorychat.app.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaDisplayFormatterTest {
    @Test
    fun formatsPersonaFieldsForMemoryCenterDisplay() {
        val persona = Persona(
            id = "persona-1",
            name = "小墨",
            description = "适合调试的助手",
            role = "技术伙伴",
            tone = "冷静、直接",
            behaviorRules = listOf("先说结论", "不确定时说明"),
            boundaries = listOf("不要编造信息"),
            isDefault = true
        )

        val fields = PersonaDisplayFormatter.fields(persona)

        assertEquals(
            listOf(
                "描述" to "适合调试的助手",
                "角色" to "技术伙伴",
                "语气" to "冷静、直接",
                "规则" to "先说结论；不确定时说明",
                "边界" to "不要编造信息"
            ),
            fields
        )
    }

    @Test
    fun omitsBlankPersonaFields() {
        val persona = Persona(
            id = "persona-1",
            name = "技术伙伴",
            description = "",
            role = null,
            tone = "冷静",
            behaviorRules = emptyList(),
            boundaries = emptyList()
        )

        val fields = PersonaDisplayFormatter.fields(persona)

        assertEquals(listOf("语气" to "冷静"), fields)
    }

    @Test
    fun parsesEditableListFieldsFromChineseSemicolonText() {
        assertEquals(
            listOf("先说结论", "不确定时说明"),
            PersonaDisplayFormatter.parseListField("先说结论；不确定时说明")
        )
    }
}
