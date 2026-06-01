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
    fun completeParsesMimoSearchAnnotationsAndUsage() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "根据搜索结果，深圳周末有雨。",
                            "annotations": [
                              {
                                "type": "url_citation",
                                "url": "https://example.com/weather",
                                "title": "深圳天气预报",
                                "summary": "深圳周末降雨",
                                "site_name": "天气网",
                                "publish_time": "2026-06-01T08:00:00+08:00"
                              }
                            ]
                          }
                        }
                      ],
                      "usage": {
                        "web_search_usage": {
                          "tool_usage": 3,
                          "page_usage": 18
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            val response = provider.complete(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = "深圳这周末会下雨吗")),
                    model = "model",
                    stream = false,
                    enableWebSearch = true
                )
            )

            assertEquals("根据搜索结果，深圳周末有雨。", response.content)
            assertEquals(3, response.webSearchUsage?.keywordCount)
            assertEquals(18, response.webSearchUsage?.pageCount)
            assertEquals("深圳天气预报", response.searchCitations.single().title)
            assertEquals("天气网", response.searchCitations.single().siteName)
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

    @Test
    fun streamEmitsMimoSearchAnnotationsBeforeContent() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"choices":[{"delta":{"annotations":[{"type":"url_citation","title":"深圳天气","url":"https://example.com","site_name":"天气网"}]}}],"usage":{"web_search_usage":{"tool_usage":2,"page_usage":5}}}

                        data: {"choices":[{"delta":{"content":"有雨"}}]}

                        data: [DONE]

                        """.trimIndent()
                    )
            )
            server.start()
            val provider = OpenAICompatibleProvider("key", server.url("/v1").toString().trimEnd('/'), "model")

            val chunks = provider.streamChat(
                ChatRequest(
                    messages = listOf(ChatMessage(role = "user", content = "深圳周末天气")),
                    model = "model",
                    stream = true,
                    enableWebSearch = true
                )
            ).toList()

            val metadataChunk = chunks.first()
            assertEquals("", metadataChunk.content)
            assertEquals(2, metadataChunk.webSearchUsage?.keywordCount)
            assertEquals(5, metadataChunk.webSearchUsage?.pageCount)
            assertEquals("深圳天气", metadataChunk.searchCitations.single().title)
            assertEquals("有雨", chunks[1].content)
        }
    }
}
