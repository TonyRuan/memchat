package com.memorychat.app.domain.engine

import com.memorychat.app.testutil.FakeLlmProvider
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaInstructionExtractorTest {
    @Test
    fun usesModelClassificationForExplicitAssistantRename() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "assistant_persona_update",
                  "is_persona_instruction": true,
                  "name": "小墨",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("以后你叫小墨")

        assertEquals("小墨", instruction?.name)
        assertTrue(provider.completeRequests.single().messages.single().content.contains("以后你叫小墨"))
    }

    @Test
    fun modelClassificationCanRejectExplicitAssistantRenameText() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "other",
                  "is_persona_instruction": false,
                  "name": null,
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("以后你叫小墨")

        assertNull(instruction)
        assertEquals(1, provider.completeRequests.size)
    }

    @Test
    fun usesModelFallbackForImplicitRenameInstruction() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "is_persona_instruction": true,
                  "name": "芒果",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("以后固定昵称就用芒果")

        assertEquals("芒果", instruction?.name)
        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("assistant persona"))
        assertTrue(prompt.contains("以后固定昵称就用芒果"))
    }

    @Test
    fun usesCurrentPersonaContextForSubjectlessRenameInstruction() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "is_persona_instruction": true,
                  "name": "猪妞",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "改成猪妞吧",
            currentPersona = Persona(name = "牛牛")
        )

        assertEquals("猪妞", instruction?.name)
        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("Current assistant persona"))
        assertTrue(prompt.contains("Name: 牛牛"))
    }

    @Test
    fun usesModelClassificationForNaturalAssistantRenameWording() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "assistant_persona_update",
                  "is_persona_instruction": true,
                  "name": "比比拉布",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "给你改名字为比比拉布",
            currentPersona = Persona(name = "牛牛")
        )

        assertEquals("比比拉布", instruction?.name)
        assertTrue(provider.completeRequests.single().messages.single().content.contains("给你改名字为比比拉布"))
    }

    @Test
    fun usesModelClassificationForUserProfileName() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "user_profile",
                  "is_persona_instruction": false,
                  "name": "张三",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("我叫张三")

        assertNull(instruction)
        assertTrue(provider.completeRequests.single().messages.single().content.contains("我叫张三"))
    }

    @Test
    fun usesModelClassificationForUserAddressingRequest() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "user_addressing_preference",
                  "is_persona_instruction": false,
                  "name": "大王",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "你叫我大王吧",
            currentPersona = Persona(name = "猪妞")
        )

        assertNull(instruction)
        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("user_addressing_preference"))
        assertTrue(prompt.contains("你叫我大王吧"))
    }

    @Test
    fun usesModelClassificationForSubjectlessUserAddressingRequest() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "user_addressing_preference",
                  "is_persona_instruction": false,
                  "name": "大王",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "以后叫我大王就好",
            currentPersona = Persona(name = "猪妞")
        )

        assertNull(instruction)
        assertTrue(provider.completeRequests.single().messages.single().content.contains("以后叫我大王就好"))
    }

    @Test
    fun suppressesModelResultWhenCategoryIsNotAssistantPersonaUpdate() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "user_addressing_preference",
                  "is_persona_instruction": true,
                  "name": "我大王",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "你以后可以尊称我大王",
            currentPersona = Persona(name = "猪妞")
        )

        assertNull(instruction)
        assertTrue(provider.completeRequests.single().messages.single().content.contains("你以后可以尊称我大王"))
    }

    @Test
    fun usesModelClassificationForAddressingVariants() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "user_addressing_preference",
                  "is_persona_instruction": false,
                  "name": "King",
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent(),
                """
                {
                  "category": "user_addressing_preference",
                  "is_persona_instruction": false,
                  "name": null,
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val englishInstruction = extractor.detect("call me King", Persona(name = "猪妞"))
        val negatedInstruction = extractor.detect("以后别叫我大王了", Persona(name = "猪妞"))

        assertNull(englishInstruction)
        assertNull(negatedInstruction)
        assertEquals(2, provider.completeRequests.size)
    }

    @Test
    fun treatsOneOffMarkdownReplyFormatAsOther() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf(
                """
                {
                  "category": "other",
                  "is_persona_instruction": false,
                  "name": null,
                  "role": null,
                  "tone": null,
                  "behavior_rules": [],
                  "boundaries": []
                }
                """.trimIndent()
            )
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect(
            content = "请用 Markdown 回复，包含一个二级标题、两个项目符号和一个 kotlin 代码块，内容简短。",
            currentPersona = Persona(name = "露露")
        )

        assertNull(instruction)
        val prompt = provider.completeRequests.single().messages.single().content
        assertTrue(prompt.contains("Temporary formatting requests"))
        assertTrue(prompt.contains("请用 Markdown 回复"))
    }
}
