package com.memorychat.app.testutil

import com.memorychat.app.domain.model.ChatChunk
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.ChatResponse
import com.memorychat.app.domain.provider.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmProvider(
    completeResponses: List<String> = emptyList(),
    streamResponses: List<String> = emptyList()
) : LlmProvider {
    private val completeResponses = ArrayDeque(completeResponses)
    private val streamResponses = ArrayDeque(streamResponses)
    val completeRequests = mutableListOf<ChatRequest>()
    val streamRequests = mutableListOf<ChatRequest>()

    override fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        streamRequests += request
        val text = streamResponses.removeFirstOrNull().orEmpty()
        if (text.isNotEmpty()) {
            emit(ChatChunk(content = text, done = false))
        }
        emit(ChatChunk(content = "", done = true))
    }

    override suspend fun complete(request: ChatRequest): ChatResponse {
        completeRequests += request
        return ChatResponse(content = completeResponses.removeFirstOrNull().orEmpty())
    }
}
