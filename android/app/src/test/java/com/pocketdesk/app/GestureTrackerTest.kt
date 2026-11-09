package com.pocketdesk.app

import com.pocketdesk.app.core.Buttons
import com.pocketdesk.app.input.GestureArbiter
import com.pocketdesk.app.input.GestureContact
import com.pocketdesk.app.input.GestureEnd
import com.pocketdesk.app.input.GestureMotionKind
import com.pocketdesk.app.input.GestureTracker
import com.pocketdesk.app.input.TouchpadMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTrackerTest {
    private fun finger(id: Long, x: Float = 0f, y: Float = 0f) = GestureContact(id, x, y)

    private fun finish(tracker: GestureTracker, scrolled: Boolean = false) = GestureArbiter.finish(
        maxPointers = tracker.maxPointers,
        moved = tracker.totalMove > 26f,
        scrolled = scrolled,
        dragging = false,
        threeFingerMoved = false,
        heldMs = 1500,
        doubleTap = false,
        flingVertical = 0f,
        flingHorizontal = 0f,
    )

    @Test
    fun `consecutive scroll rounds respond on first movement with either finger lifting first`() {
        val tracker = GestureTracker()
        val math = TouchpadMath()
        repeat(6) { round ->
            val at = round * 2000L
            val first = round * 2L + 1
            val second = first + 1
            tracker.start(listOf(finger(first)), at, tapDragCandidate = true)
            math.reset()
            val landing = tracker.update(listOf(finger(first), finger(second, 100f)), at + 8)
            assertTrue(landing.contactsChanged)
            assertEquals(0f, landing.dy, 0f)
            assertFalse(tracker.tapDragArmed)
            val move = tracker.update(listOf(finger(first, y = 60f), finger(second, 100f, 60f)), at + 16)
            assertEquals(GestureMotionKind.SCROLL, move.kind)
            assertEquals(60f, move.dy, 0f)
            assertEquals(8f, move.dtMs, 0f)
            assertEquals(0 to 1, math.scroll(move.dx, move.dy))
            val survivor = if (round % 2 == 0) finger(first, y = 60f) else finger(second, 100f, 60f)
            val lift = tracker.update(listOf(survivor), at + 24)
            assertEquals(GestureMotionKind.NONE, lift.kind)
            val tail = tracker.update(listOf(survivor.copy(y = 300f)), at + 32)
            assertEquals(GestureMotionKind.NONE, tail.kind)
            assertEquals(0f, tail.dy, 0f)
            tracker.update(emptyList(), at + 40)
            assertEquals(GestureEnd.None, finish(tracker, scrolled = true))
        }
    }

    @Test
    fun `simultaneous two finger landing never arms tap drag`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2, 100f)), 100, tapDragCandidate = true)
        assertEquals(2, tracker.maxPointers)
        assertFalse(tracker.tapDragArmed)
        assertEquals(GestureMotionKind.SCROLL, tracker.update(listOf(finger(1, y = 10f), finger(2, 100f, 10f)), 108).kind)
    }

    @Test
    fun `two to one to two resumes scroll without centroid jump or pointer tail`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = true)
        tracker.update(listOf(finger(1, y = 30f), finger(2, 100f, 30f)), 8)
        assertEquals(GestureMotionKind.NONE, tracker.update(listOf(finger(2, 100f, 30f)), 16).kind)
        tracker.update(listOf(finger(2, 500f, 400f)), 24)
        val rejoin = tracker.update(listOf(finger(2, 500f, 400f), finger(3, 900f, 800f)), 32)
        assertTrue(rejoin.contactsChanged)
        assertEquals(0f, rejoin.dx, 0f)
        assertEquals(0f, rejoin.dy, 0f)
        val resumed = tracker.update(listOf(finger(2, 500f, 460f), finger(3, 900f, 860f)), 40)
        assertEquals(GestureMotionKind.SCROLL, resumed.kind)
        assertEquals(60f, resumed.dy, 0f)
        assertFalse(tracker.tapDragArmed)
    }

    @Test
    fun `replacing a pointer at the same count rebases by identity`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = false)
        val replaced = tracker.update(listOf(finger(1), finger(3, 1000f)), 8)
        assertTrue(replaced.contactsChanged)
        assertEquals(0f, replaced.dx, 0f)
        assertEquals(0f, tracker.totalMove, 0f)
        val moved = tracker.update(listOf(finger(3, 1010f), finger(1, 10f)), 16)
        assertFalse(moved.contactsChanged)
        assertEquals(10f, moved.dx, 0f)
    }

    @Test
    fun `scroll shorter than a wheel notch does not become right click`() {
        val tracker = GestureTracker()
        val math = TouchpadMath()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = false)
        val move = tracker.update(listOf(finger(1, y = 30f), finger(2, 100f, 30f)), 8)
        assertEquals(0 to 0, math.scroll(move.dx, move.dy))
        tracker.update(emptyList(), 16)
        assertEquals(GestureEnd.None, finish(tracker))
    }

    @Test
    fun `slow subpixel scroll samples accumulate into a notch`() {
        val tracker = GestureTracker()
        val math = TouchpadMath()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = false)
        var notches = 0
        repeat(224) { index ->
            val y = (index + 1) * 0.25f
            val move = tracker.update(listOf(finger(1, y = y), finger(2, 100f, y)), (index + 1) * 8L)
            notches += math.scroll(move.dx, move.dy).second
        }
        assertEquals(1, notches)
        assertEquals(56f, tracker.totalMove, 0f)
    }

    @Test
    fun `opposing finger movement is not mistaken for a stationary right tap`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = false)
        val move = tracker.update(listOf(finger(1, y = -30f), finger(2, 100f, 30f)), 8)
        assertEquals(0f, move.dy, 0f)
        assertEquals(GestureEnd.None, finish(tracker))
    }

    @Test
    fun `three finger navigation wins over two and one finger tails`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2, 100f)), 0, tapDragCandidate = false)
        tracker.update(listOf(finger(1), finger(2, 100f), finger(3, 200f)), 8)
        val swipe = tracker.update(listOf(finger(1, 80f), finger(2, 180f), finger(3, 280f)), 16)
        assertEquals(GestureMotionKind.NAVIGATION, swipe.kind)
        assertEquals(80f, swipe.dx, 0f)
        tracker.update(listOf(finger(2, 180f), finger(3, 280f)), 24)
        assertEquals(GestureMotionKind.NONE, tracker.update(listOf(finger(2, 250f), finger(3, 350f)), 32).kind)
        assertEquals(GestureMotionKind.NONE, tracker.update(listOf(finger(3, 350f)), 40).kind)
        tracker.update(emptyList(), 48)
        assertEquals(GestureEnd.None, finish(tracker))
    }

    @Test
    fun `stationary staggered two finger tap remains right click`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1)), 0, tapDragCandidate = false)
        tracker.update(listOf(finger(1), finger(2, 500f)), 8)
        tracker.update(listOf(finger(2, 500f)), 16)
        tracker.update(emptyList(), 24)
        assertEquals(GestureEnd.Click(Buttons.RIGHT), finish(tracker))
    }

    @Test
    fun `new gesture resets multi touch history and allows single finger tap drag`() {
        val tracker = GestureTracker()
        tracker.start(listOf(finger(1), finger(2)), 0, tapDragCandidate = false)
        tracker.update(listOf(finger(1, y = 100f), finger(2, y = 100f)), 8)
        tracker.update(emptyList(), 16)
        tracker.start(listOf(finger(3, 700f)), 100, tapDragCandidate = true)
        assertEquals(1, tracker.maxPointers)
        assertEquals(0f, tracker.totalMove, 0f)
        assertTrue(tracker.tapDragArmed)
        val move = tracker.update(listOf(finger(3, 705f)), 108)
        assertEquals(GestureMotionKind.POINTER, move.kind)
        assertEquals(5f, move.dx, 0f)
    }
}
