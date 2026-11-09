package com.pocketdesk.app.input

class ScrollAccumulator(private val pixelsPerStep: Float) {
    init { require(pixelsPerStep > 0f) }

    private var remainder = 0f

    fun add(pixels: Float): Int {
        remainder += pixels
        val steps = (remainder / pixelsPerStep).toInt()
        remainder -= steps * pixelsPerStep
        return steps
    }

    fun reset() { remainder = 0f }
}
