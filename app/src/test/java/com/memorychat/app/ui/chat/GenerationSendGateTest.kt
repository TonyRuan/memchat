package com.memorychat.app.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSendGateTest {
    @Test
    fun rejectsSecondStartUntilFinished() {
        val gate = GenerationSendGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())

        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test
    fun finishIsIdempotent() {
        val gate = GenerationSendGate()

        gate.finish()
        gate.finish()

        assertTrue(gate.tryStart())
    }
}
