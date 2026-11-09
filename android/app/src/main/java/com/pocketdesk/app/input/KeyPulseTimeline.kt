package com.pocketdesk.app.input

data class KeyPulse(val downAtMs: Long, val upAtMs: Long)

class KeyPulseTimeline {
    private var nextAtMs = 0L

    fun reserve(nowMs: Long, holdMs: Long, gapMs: Long): KeyPulse {
        require(holdMs > 0 && gapMs > 0)
        val downAt = maxOf(nowMs, nextAtMs)
        val upAt = downAt + holdMs
        nextAtMs = upAt + gapMs
        return KeyPulse(downAt, upAt)
    }

    fun clear() {
        nextAtMs = 0
    }
}
