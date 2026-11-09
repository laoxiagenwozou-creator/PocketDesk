package com.pocketdesk.app

import com.pocketdesk.app.input.TouchpadMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadMathTest {

    @Test
    fun `sub-pixel movement is carried instead of dropped`() {
        val math = TouchpadMath(sensitivity = 1f)
        var total = 0
        repeat(10) { total += math.pointer(0.3f, 0f).first }
        assertTrue("carry must accumulate to movement, total=$total", total >= 2)
    }

    @Test
    fun `sensitivity scales movement`() {
        val math = TouchpadMath(sensitivity = 2f)
        math.acceleration = false
        val (dx, dy) = math.pointer(5f, -5f)
        assertEquals(10, dx)
        assertEquals(-10, dy)
    }

    @Test
    fun `slow movement stays linear even with acceleration on`() {
        val math = TouchpadMath(sensitivity = 1f)
        math.acceleration = true
        assertEquals(2 to 0, math.pointer(2f, 0f))
    }

    @Test
    fun `fast flick is boosted by the acceleration curve`() {
        val math = TouchpadMath(sensitivity = 1f)
        math.acceleration = true
        assertEquals(75, math.pointer(30f, 0f).first)
    }

    @Test
    fun `acceleration off is exactly linear`() {
        val math = TouchpadMath(sensitivity = 1f)
        math.acceleration = false
        assertEquals(30, math.pointer(30f, 0f).first)
    }

    @Test
    fun `scroll accumulates into notches`() {
        val math = TouchpadMath()
        math.pixelsPerNotch = 10f
        assertEquals(0 to 0, math.scroll(0f, 4f))
        assertEquals(0 to 1, math.scroll(0f, 6.5f))
    }

    @Test
    fun `scroll keeps horizontal separate`() {
        val math = TouchpadMath()
        math.pixelsPerNotch = 20f
        val (x, y) = math.scroll(25f, -45f)
        assertEquals(1, x)
        assertEquals(-2, y)
    }

    @Test
    fun `reset clears carry`() {
        val math = TouchpadMath(sensitivity = 1f)
        math.pointer(0.9f, 0.9f)
        math.reset()
        assertEquals(0 to 0, math.pointer(0.5f, 0.5f))
    }

    @Test
    fun `gain scale maps another panel's units onto the tuned ones`() {
        val math = TouchpadMath(sensitivity = 1f, gainScale = 0.5f)
        math.acceleration = false
        assertEquals(10 to 0, math.pointer(20f, 0f))
    }

    @Test
    fun `speed is judged per reference frame, not per event`() {
        val math = TouchpadMath(sensitivity = 1f)
        math.acceleration = true
        // 30 units in a 16 ms sample is 15 per 8 ms frame, not 30.
        assertEquals(45, math.pointer(30f, 0f, dtMs = 16f).first)
    }
}
