package com.memorychat.app.domain.engine

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemoryExtractionCoordinator {
    private val inFlight = ConcurrentHashMap<String, Job>()
    private val _activeConversationIds = MutableStateFlow<Set<String>>(emptySet())
    val activeConversationIds: StateFlow<Set<String>> = _activeConversationIds
    private val _isAnyActive = MutableStateFlow(false)
    val isAnyActive: StateFlow<Boolean> = _isAnyActive

    fun launchIfIdle(
        conversationId: String,
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean {
        while (true) {
            val existing = inFlight[conversationId]
            if (existing != null) {
                if (existing.isActive) return false
                inFlight.remove(conversationId, existing)
                continue
            }

            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    inFlight.remove(conversationId, coroutineContext[Job])
                    updateActiveConversationIds()
                }
            }
            if (inFlight.putIfAbsent(conversationId, job) == null) {
                job.start()
                updateActiveConversationIds()
                return true
            }
            job.cancel()
        }
    }

    private fun updateActiveConversationIds() {
        _activeConversationIds.value = inFlight
            .filterValues { it.isActive }
            .keys
            .toSet()
        _isAnyActive.value = _activeConversationIds.value.isNotEmpty()
    }
}
