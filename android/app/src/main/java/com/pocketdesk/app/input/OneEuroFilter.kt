package com.pocketdesk.app.input

import kotlin.math.abs
import kotlin.math.PI

internal class OneEuroFilter(var minCutoff: Float = 1.2f, var beta: Float = 0.8f) {
    private var previous: Float? = null
    private var previousRate = 0f
    private var previousTime = 0.0

    fun filter(value: Float, timeSeconds: Double): Float {
        val last = previous
        if (last == null || timeSeconds <= previousTime) {
            previous = value
            previousTime = timeSeconds
            return value
        }
        val elapsed = (timeSeconds - previousTime).toFloat().coerceIn(0.001f, 0.1f)
        previousTime = timeSeconds
        val derivativeAlpha = alpha(1f, elapsed)
        previousRate = derivativeAlpha * ((value - last) / elapsed) + (1f - derivativeAlpha) * previousRate
        val valueAlpha = alpha(minCutoff + beta * abs(previousRate), elapsed)
        val result = valueAlpha * value + (1f - valueAlpha) * last
        previous = result
        return result
    }

    fun reset() {
        previous = null
        previousRate = 0f
    }

    private fun alpha(cutoff: Float, elapsed: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff.coerceAtLeast(0.01f))
        return 1f / (1f + tau / elapsed)
    }
}
