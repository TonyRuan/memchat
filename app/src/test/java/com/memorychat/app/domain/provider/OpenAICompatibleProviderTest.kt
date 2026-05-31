package com.memorychat.app.domain.provider

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAICompatibleProviderTest {
    @Test
    fun completeReturnsContentForValidResponse() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            val response = provider.complete(ChatRequest(listOf(ChatMessage(role = "user", content = "hi")), "model", false))

            assertEquals("ok", response.content)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun completeThrowsForHttpFailure() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"bad"}"""))
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            provider.complete(ChatRequest(listOf(ChatMessage(role = "user", content = "hi")), "model", false))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun completeThrowsForUnexpectedResponseShape() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"unexpected":true}"""))
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            provider.complete(ChatRequest(listOf(ChatMessage(role = "user", content = "hi")), "model", false))
        }
    }
}
