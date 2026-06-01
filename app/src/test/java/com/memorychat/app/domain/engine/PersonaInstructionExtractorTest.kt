package com.memorychat.app.domain.engine

import com.memorychat.app.testutil.FakeLlmProvider
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaInstructionExtractorTest {
    @Test
    fun usesDeterministicDetectorBeforeModelFallback() = runTest {
        val provider = FakeLlmProvider(
            completeResponses = listOf("""{"is_persona_instruction":false}""")
        )
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("以后你叫小墨")

        assertEquals("小墨", instruction?.name)
        assertTrue(provider.completeRequests.isEmpty())
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
    fun doesNotCallModelForUserProfileName() = runTest {
        val provider = FakeLlmProvider()
        val extractor = PersonaInstructionExtractor(provider, "fake-model")

        val instruction = extractor.detect("我叫张三")

        assertEquals(null, instruction)
        assertTrue(provider.completeRequests.isEmpty())
    }
}
