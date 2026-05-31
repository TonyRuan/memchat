package com.memorychat.app.ui.chat

import java.util.concurrent.atomic.AtomicBoolean

class GenerationSendGate {
    private val active = AtomicBoolean(false)

    fun tryStart(): Boolean {
        return active.compareAndSet(false, true)
    }

    fun finish() {
        active.set(false)
    }
}
