package com.memorychat.app.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLengthErrorDetectorTest {
    @Test
    fun detectsOpenAiStyleContextLengthExceededErrors() {
        assertTrue(
            ContextLengthErrorDetector.isContextLengthExceeded(
                "HTTP 400: {\"error\":{\"code\":\"context_length_exceeded\",\"message\":\"maximum context length\"}}"
            )
        )
    }

    @Test
    fun ignoresUnrelatedNetworkErrors() {
        assertFalse(ContextLengthErrorDetector.isContextLengthExceeded("HTTP 401: invalid api key"))
    }
}
