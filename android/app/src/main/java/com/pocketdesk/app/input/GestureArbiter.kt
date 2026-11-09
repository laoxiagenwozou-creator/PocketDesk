package com.pocketdesk.app.input

import com.pocketdesk.app.core.Buttons

/**
 * What one finished touch sequence should send.
 *
 * Kept separate from the gesture loop so the arbitration (which gesture won) can be
 * unit-tested: the ordering below is the whole trackpad contract, and getting it wrong
 * is felt as "a swipe turned into a middle click".
 */
sealed interface GestureEnd {
    data object None : GestureEnd
    data class Click(val button: Int) : GestureEnd
    data object DoubleClick : GestureEnd
    /** A tap-and-a-half drag finished; the caller releases the left button. */
    data object EndDrag : GestureEnd
    data class Fling(val vertical: Float, val horizontal: Float) : GestureEnd
}

object GestureArbiter {

    /** A scroll that lasted longer than this is placed deliberately, not flung. */
    const val MAX_FLING_HOLD_MS = 1200L

    /** Decide what a lifted finger sequence means, releasing an active drag first. */
    fun finish(
        maxPointers: Int,
        moved: Boolean,
        scrolled: Boolean,
        dragging: Boolean,
        threeFingerMoved: Boolean,
        heldMs: Long,
        doubleTap: Boolean,
        flingVertical: Float,
        flingHorizontal: Float,
    ): GestureEnd = when {
        dragging -> GestureEnd.EndDrag
        maxPointers >= 3 ->
            if (!moved && !scrolled && !threeFingerMoved) GestureEnd.Click(Buttons.MIDDLE)
            else GestureEnd.None
        maxPointers == 2 && !moved && !scrolled -> GestureEnd.Click(Buttons.RIGHT)
        maxPointers == 1 && !moved ->
            if (doubleTap) GestureEnd.DoubleClick else GestureEnd.Click(Buttons.LEFT)
        maxPointers == 2 && scrolled && heldMs < MAX_FLING_HOLD_MS ->
            GestureEnd.Fling(flingVertical, flingHorizontal)
        else -> GestureEnd.None
    }
}
