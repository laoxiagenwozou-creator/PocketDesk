package com.pocketdesk.app

import com.pocketdesk.app.input.KeyPulseTimeline
import org.junit.Assert.*
import org.junit.Test

class KeyPulseTimelineTest {
    @Test fun separateTextRequestsDoNotOverlap() {
        val timeline = KeyPulseTimeline()
        val first = timeline.reserve(100, 6, 6)
        val second = timeline.reserve(100, 6, 6)
        assertTrue(second.downAtMs > first.upAtMs)
    }
    @Test fun multipleBackspacesAndReplacementStayInOrder() {
        val timeline = KeyPulseTimeline()
        val deletes = (1..5).map { timeline.reserve(100, 14, 6) }
        val replacement = timeline.reserve(100, 6, 6)
        assertTrue(replacement.downAtMs > deletes.last().upAtMs)
        deletes.zipWithNext().forEach { (a, b) -> assertTrue(b.downAtMs > a.upAtMs) }
    }
    @Test fun fastRepeatedCharactersEachHaveAnUpGap() {
        val timeline = KeyPulseTimeline()
        val pulses = (1..100).map { timeline.reserve(100, 6, 6) }
        pulses.zipWithNext().forEach { (a, b) -> assertEquals(6L, b.downAtMs - a.upAtMs) }
    }
    @Test fun reconnectDoesNotInheritOldQueueDelay() {
        val timeline = KeyPulseTimeline()
        repeat(1000) { timeline.reserve(100, 6, 6) }
        timeline.clear()
        assertEquals(200L, timeline.reserve(200, 14, 6).downAtMs)
    }
    @Test fun idleKeyboardStartsImmediately() {
        val timeline = KeyPulseTimeline()
        timeline.reserve(100, 6, 6)
        assertEquals(500L, timeline.reserve(500, 14, 6).downAtMs)
    }
}
