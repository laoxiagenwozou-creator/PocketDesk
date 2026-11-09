package com.pocketdesk.app.input

data class PointerDelta(val x: Int, val y: Int, val wheel: Int, val pan: Int)

class PointerAccumulator {
    private var x = 0L
    private var y = 0L
    private var wheel = 0L
    private var pan = 0L

    val hasPending: Boolean get() = x != 0L || y != 0L || wheel != 0L || pan != 0L

    fun move(dx: Int, dy: Int) {
        x += dx
        y += dy
    }

    fun scroll(vertical: Int, horizontal: Int) {
        wheel += vertical
        pan += horizontal
    }

    fun take(pointerLimit: Int, horizontalScroll: Boolean): PointerDelta {
        val dx = x.coerceIn(-pointerLimit.toLong(), pointerLimit.toLong()).toInt()
        val dy = y.coerceIn(-pointerLimit.toLong(), pointerLimit.toLong()).toInt()
        val dw = wheel.coerceIn(-127, 127).toInt()
        val dp = if (horizontalScroll) pan.coerceIn(-127, 127).toInt() else 0
        x -= dx
        y -= dy
        wheel -= dw
        pan = if (horizontalScroll) pan - dp else 0
        return PointerDelta(dx, dy, dw, dp)
    }

    fun restore(delta: PointerDelta) {
        move(delta.x, delta.y)
        scroll(delta.wheel, delta.pan)
    }

    fun clear() {
        x = 0; y = 0; wheel = 0; pan = 0
    }
}
