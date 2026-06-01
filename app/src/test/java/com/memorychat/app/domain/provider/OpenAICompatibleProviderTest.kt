package com.memorychat.app.domain.provider

import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ChatRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun completeAddsMimoWebSearchToolWhenEnabled() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"searched"}}]}"""))
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            provider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = "latest news")),
                    model = "model",
                    stream = false,
                    enableWebSearch = true
                )
            )

            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains(""""tools""""))
            assertTrue(body.contains(""""web_search""""))
            assertTrue(body.contains(""""type":"web_search""""))
            assertTrue(body.contains(""""force_search":true"""))
            assertTrue(body.contains(""""max_keyword":3"""))
            assertTrue(body.contains(""""tool_choice":"auto""""))
        }
    }

    @Test
    fun completeDoesNotRunFallbackSearchWhenMimoReturnsWebSearchToolCall() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "",
                            "tool_calls": [
                              {
                                "type": "function",
                                "function": {
                                  "name": "web_search",
                                  "arguments": "{\"query\":\"MiMo V2.5 latest\"}"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.start()
            val provider = OpenAICompatibleProvider(
                apiKey = "key",
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                modelName = "model"
            )

            val response = provider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = "search")),
                    model = "model",
                    stream = false,
                    enableWebSearch = true
                )
            )

            assertEquals("", response.content)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun streamAddsMimoWebSearchToolWhenEnabled() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"choices":[{"delta":{"content":"ok"}}]}

                        data: [DONE]

                        """.trimIndent()
                    )
            )
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            val chunks = provider.streamChat(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = "latest news")),
                    model = "model",
                    stream = true,
                    enableWebSearch = true
                )
            ).toList()

            assertEquals("ok", chunks.first().content)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains(""""tools""""))
            assertTrue(body.contains(""""web_search""""))
            assertTrue(body.contains(""""type":"web_search""""))
            assertTrue(body.contains(""""force_search":true"""))
            assertTrue(body.contains(""""max_keyword":3"""))
            assertTrue(body.contains(""""tool_choice":"auto""""))
        }
    }
}
