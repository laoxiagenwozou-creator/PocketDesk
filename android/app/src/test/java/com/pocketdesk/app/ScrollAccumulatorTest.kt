package com.pocketdesk.app

import com.pocketdesk.app.input.ScrollAccumulator
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollAccumulatorTest {
    @Test fun smallMovesAccumulateAndReverse() {
        val scroll = ScrollAccumulator(42f)
        assertEquals(0, scroll.add(20f))
        assertEquals(1, scroll.add(25f))
        assertEquals(0, scroll.add(-44f))
        assertEquals(-1, scroll.add(-3f))
    }

    @Test fun resetDiscardsRemainderBetweenGestures() {
        val scroll = ScrollAccumulator(42f)
        assertEquals(0, scroll.add(41f))
        scroll.reset()
        assertEquals(0, scroll.add(1f))
        assertEquals(2, scroll.add(84f))
    }
}
