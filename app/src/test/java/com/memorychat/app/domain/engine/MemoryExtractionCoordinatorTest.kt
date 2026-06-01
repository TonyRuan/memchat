package com.memorychat.app.domain.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryExtractionCoordinatorTest {
    @Test
    fun rejectsDuplicateConversationWhileExtractionIsRunning() = runTest {
        val coordinator = MemoryExtractionCoordinator()
        val release = CompletableDeferred<Unit>()
        var runCount = 0

        val firstStarted = coordinator.launchIfIdle("conv-1", backgroundScope) {
            runCount += 1
            release.await()
        }
        runCurrent()
        val secondStarted = coordinator.launchIfIdle("conv-1", backgroundScope) {
            runCount += 1
        }

        assertTrue(firstStarted)
        assertFalse(secondStarted)
        assertEquals(1, runCount)

        release.complete(Unit)
        runCurrent()
        val thirdStarted = coordinator.launchIfIdle("conv-1", backgroundScope) {
            runCount += 1
        }
        runCurrent()

        assertTrue(thirdStarted)
        assertEquals(2, runCount)
    }

    @Test
    fun allowsDifferentConversationsToRunInParallel() = runTest {
        val coordinator = MemoryExtractionCoordinator()
        val release = CompletableDeferred<Unit>()
        var runCount = 0

        val firstStarted = coordinator.launchIfIdle("conv-1", backgroundScope) {
            runCount += 1
            release.await()
        }
        val secondStarted = coordinator.launchIfIdle("conv-2", backgroundScope) {
            runCount += 1
        }
        runCurrent()

        assertTrue(firstStarted)
        assertTrue(secondStarted)
        assertEquals(2, runCount)
    }
}
