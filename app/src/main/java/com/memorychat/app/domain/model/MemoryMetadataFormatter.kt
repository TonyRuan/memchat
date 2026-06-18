package com.memorychat.app.domain.model

import java.time.Instant

object MemoryMetadataFormatter {
    fun isoTimestamp(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    fun timestampMetadata(memory: Memory): String = buildString {
        append("created_at=${isoTimestamp(memory.createdAt)}")
        append(" updated_at=${isoTimestamp(memory.updatedAt)}")
        memory.lastUsedAt?.let { append(" last_used_at=${isoTimestamp(it)}") }
    }
}
