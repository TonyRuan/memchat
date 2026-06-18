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
            mission = "帮助用户推进工程任务",
            expertise = listOf("Android", "Agent 设计"),
            tone = "冷静、直接",
            communicationStyle = "短句、先结论后细节",
            behaviorRules = listOf("先说结论", "不确定时说明"),
            boundaries = listOf("不要编造信息"),
            toolPolicy = listOf("需要实时信息时先搜索"),
            memoryPolicy = listOf("人格设置写 Persona，不写 Memory"),
            exampleDialogues = listOf("用户：你是谁？\n助手：我是小墨。"),
            isDefault = true
        )

        val fields = PersonaDisplayFormatter.fields(persona)

        assertEquals(
            listOf(
                "描述" to "适合调试的助手",
                "角色" to "技术伙伴",
                "使命" to "帮助用户推进工程任务",
                "专长" to "Android；Agent 设计",
                "语气" to "冷静、直接",
                "沟通风格" to "短句、先结论后细节",
                "规则" to "先说结论；不确定时说明",
                "边界" to "不要编造信息",
                "工具策略" to "需要实时信息时先搜索",
                "记忆策略" to "人格设置写 Persona，不写 Memory",
                "示例对话" to "用户：你是谁？\n助手：我是小墨。"
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
