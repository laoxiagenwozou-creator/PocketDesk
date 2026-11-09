package com.pocketdesk.app.input

import kotlin.math.abs
import kotlin.math.sign

internal class ContinuousScrollState {
    var rate: Float = 0f
        private set
    private var remainder = 0f

    fun setPosition(position: Float) {
        val p = if (position.isFinite()) position.coerceIn(-1f, 1f) else 0f
        val magnitude = ((abs(p) - 0.12f) / 0.88f).coerceAtLeast(0f)
        val next = sign(p) * magnitude * magnitude * 48f
        if (next == 0f || sign(next) != sign(rate)) remainder = 0f
        rate = next
    }

    fun advance(elapsedMs: Long): Int {
        remainder += rate * elapsedMs.coerceIn(0, 50) / 1000f
        val whole = remainder.toInt()
        remainder -= whole
        return whole
    }

    fun stop() {
        rate = 0f
        remainder = 0f
    }
}
