package com.pocketdesk.app

import com.pocketdesk.app.core.Buttons
import com.pocketdesk.app.input.MouseButtonSource
import com.pocketdesk.app.input.MouseButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

class MouseButtonStateTest {
    @Test fun releasingRightDoesNotReleaseHeldLeft() {
        val state = MouseButtonState()
        state.set(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
        assertEquals(Buttons.LEFT or Buttons.RIGHT, state.set(MouseButtonSource.RIGHT_PAD, Buttons.RIGHT))
        assertEquals(Buttons.LEFT, state.set(MouseButtonSource.RIGHT_PAD, 0))
        assertEquals(0, state.set(MouseButtonSource.LEFT_PAD, 0))
    }

    @Test fun releasingPadDoesNotCancelDragLock() {
        val state = MouseButtonState()
        state.set(MouseButtonSource.DRAG_LOCK, Buttons.LEFT)
        state.set(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
        assertEquals(Buttons.LEFT, state.set(MouseButtonSource.LEFT_PAD, 0))
        assertEquals(0, state.set(MouseButtonSource.DRAG_LOCK, 0))
    }

    @Test fun delayedClickReleaseDoesNotReleaseNewDrag() {
        val state = MouseButtonState()
        val click = Any()
        state.set(click, Buttons.LEFT)
        state.set(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
        assertEquals(Buttons.LEFT, state.set(click, 0))
    }

    @Test fun overlappingClicksOwnTheirReleases() {
        val state = MouseButtonState()
        val first = Any()
        val second = Any()
        state.set(first, Buttons.LEFT)
        state.set(second, Buttons.LEFT)
        assertEquals(Buttons.LEFT, state.set(first, 0))
        assertEquals(0, state.set(second, 0))
    }

    @Test fun gestureCancellationKeepsPhysicalButton() {
        val state = MouseButtonState()
        state.set(MouseButtonSource.TAP_DRAG, Buttons.LEFT)
        state.set(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
        assertEquals(Buttons.LEFT, state.set(MouseButtonSource.TAP_DRAG, 0))
    }

    @Test fun temporaryClickIsNotAPersistentHold() {
        val state = MouseButtonState()
        state.set(Any(), Buttons.LEFT)
        assertEquals(0, state.heldMask)
        state.set(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
        assertEquals(Buttons.LEFT, state.heldMask)
    }
}
