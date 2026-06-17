package com.memorychat.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDebugSnapshotTest {
    @Test
    fun roundTripsSnapshotJsonForDebugScreen() {
        val snapshot = ConversationDebugSnapshot(
            conversationId = "conv-1",
            updatedAt = 123L,
            recallScene = "project",
            recalledMemories = listOf(
                DebugMemoryTrace(
                    id = "mem-1",
                    type = MemoryType.PROJECT,
                    content = "第一阶段优先验证记忆系统",
                    reason = "scene[project] recall"
                )
            ),
            contextMessageCount = 4,
            rollingSummary = "旧消息摘要",
            summaryWatermark = 99L,
            summaryUpdated = true,
            retryAfterContextLimit = true
        )

        val restored = ConversationDebugSnapshotJson.fromJson(
            ConversationDebugSnapshotJson.toJson(snapshot)
        )

        assertEquals(snapshot, restored)
    }
}
