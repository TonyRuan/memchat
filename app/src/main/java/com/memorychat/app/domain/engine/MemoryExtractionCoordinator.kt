package com.memorychat.app.domain.engine

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MemoryExtractionCoordinator {
    private val inFlight = ConcurrentHashMap<String, Job>()

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
                }
            }
            if (inFlight.putIfAbsent(conversationId, job) == null) {
                job.start()
                return true
            }
            job.cancel()
        }
    }
}
