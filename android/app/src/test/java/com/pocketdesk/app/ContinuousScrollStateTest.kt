package com.pocketdesk.app

import com.pocketdesk.app.input.ContinuousScrollState
import org.junit.Assert.*
import org.junit.Test

class ContinuousScrollStateTest {
    @Test fun centerAndInvalidPositionNeverScroll() {
        val state = ContinuousScrollState()
        listOf(0f, 0.1f, -0.1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            state.setPosition(it)
            assertEquals(0f, state.rate, 0f)
            assertEquals(0, state.advance(1000))
        }
    }

    @Test fun directionAndAccelerationFollowLever() {
        val state = ContinuousScrollState()
        state.setPosition(0.5f)
        val medium = state.rate
        state.setPosition(1f)
        assertTrue(state.rate > medium)
        assertEquals(48f, state.rate, 0.01f)
        state.setPosition(-1f)
        assertEquals(-48f, state.rate, 0.01f)
        assertTrue(state.advance(50) < 0)
    }

    @Test fun fractionsAccumulateAcrossFrames() {
        val state = ContinuousScrollState()
        state.setPosition(1f)
        val total = (1..100).sumOf { state.advance(10) }
        assertTrue(total in 47..48)
    }

    @Test fun stalledFrameDoesNotCatchUpWithABurst() {
        val state = ContinuousScrollState()
        state.setPosition(1f)
        assertEquals(2, state.advance(10_000))
    }

    @Test fun stopAndReversalDiscardOldFractions() {
        val state = ContinuousScrollState()
        state.setPosition(1f)
        assertEquals(0, state.advance(16))
        state.setPosition(-1f)
        assertEquals(-1, state.advance(32))
        state.stop()
        assertEquals(0, state.advance(1000))
        state.setPosition(1f)
        assertEquals(0, state.advance(16))
        state.setPosition(0f)
        state.setPosition(1f)
        assertEquals(0, state.advance(16))
    }
}
