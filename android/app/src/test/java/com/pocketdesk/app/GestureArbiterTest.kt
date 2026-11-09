package com.pocketdesk.app

import com.pocketdesk.app.core.Buttons
import com.pocketdesk.app.input.GestureArbiter
import com.pocketdesk.app.input.GestureEnd
import org.junit.Assert.assertEquals
import org.junit.Test

/** The trackpad's end-of-gesture contract, one case per row of the product spec. */
class GestureArbiterTest {

    private fun end(
        maxPointers: Int = 1,
        moved: Boolean = false,
        scrolled: Boolean = false,
        dragging: Boolean = false,
        threeFingerMoved: Boolean = false,
        heldMs: Long = 100,
        doubleTap: Boolean = false,
        flingVertical: Float = 0f,
        flingHorizontal: Float = 0f,
    ) = GestureArbiter.finish(
        maxPointers = maxPointers,
        moved = moved,
        scrolled = scrolled,
        dragging = dragging,
        threeFingerMoved = threeFingerMoved,
        heldMs = heldMs,
        doubleTap = doubleTap,
        flingVertical = flingVertical,
        flingHorizontal = flingHorizontal,
    )

    @Test
    fun `single tap is a left click`() {
        assertEquals(GestureEnd.Click(Buttons.LEFT), end())
    }

    @Test
    fun `second tap within the double-tap window becomes a double click`() {
        assertEquals(GestureEnd.DoubleClick, end(doubleTap = true))
    }

    @Test
    fun `two-finger tap is a right click`() {
        assertEquals(GestureEnd.Click(Buttons.RIGHT), end(maxPointers = 2))
    }

    @Test
    fun `two-finger scroll is never a right click`() {
        assertEquals(GestureEnd.None, end(maxPointers = 2, scrolled = true, heldMs = 2000))
    }

    @Test
    fun `three-finger tap is a middle click`() {
        assertEquals(GestureEnd.Click(Buttons.MIDDLE), end(maxPointers = 3))
    }

    @Test
    fun `three-finger nudge that never reached the swipe threshold is not a middle click`() {
        assertEquals(GestureEnd.None, end(maxPointers = 3, moved = true))
    }

    @Test
    fun `three-finger swipe that already fired does not also click`() {
        assertEquals(GestureEnd.None, end(maxPointers = 3, threeFingerMoved = true))
    }

    @Test
    fun `finished drag releases the left button`() {
        assertEquals(GestureEnd.EndDrag, end(moved = true, dragging = true))
    }

    @Test
    fun `short scroll coasts`() {
        assertEquals(
            GestureEnd.Fling(2.5f, -1f),
            end(maxPointers = 2, moved = true, scrolled = true, heldMs = 300, flingVertical = 2.5f, flingHorizontal = -1f),
        )
    }

    @Test
    fun `two-finger movement below a wheel notch does not right click`() {
        assertEquals(GestureEnd.None, end(maxPointers = 2, moved = true))
    }

    @Test
    fun `adding fingers to a drag still releases the left button`() {
        for (count in 2..3) {
            assertEquals(GestureEnd.EndDrag, end(maxPointers = count, dragging = true))
        }
    }

    @Test
    fun `three-finger navigation suppresses earlier scroll inertia`() {
        assertEquals(
            GestureEnd.None,
            end(maxPointers = 3, moved = true, scrolled = true, threeFingerMoved = true, flingVertical = 3f),
        )
    }

    @Test
    fun `scroll before a stationary third finger does not middle click`() {
        assertEquals(GestureEnd.None, end(maxPointers = 3, scrolled = true))
    }

    @Test
    fun `long deliberate scroll does not coast`() {
        assertEquals(
            GestureEnd.None,
            end(maxPointers = 2, moved = true, scrolled = true, heldMs = 1500),
        )
    }
}
