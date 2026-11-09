package com.pocketdesk.app

import com.pocketdesk.app.input.PointerAccumulator
import com.pocketdesk.app.input.PointerDelta
import com.pocketdesk.app.transport.hid.HidEncoder
import com.pocketdesk.app.transport.hid.HidProfiles
import org.junit.Assert.*
import org.junit.Test

class PointerAccumulatorTest {
    @Test fun excessWheelAndPanSurviveAcrossEncodedReports() {
        val pending = PointerAccumulator()
        pending.scroll(500, -350)
        var wheel = 0
        var pan = 0
        while (pending.hasPending) {
            val d = pending.take(127, true)
            val bytes = HidEncoder.mouseReport(HidProfiles.A_LINKPAD, 0, d.x, d.y, d.wheel, d.pan)
            wheel += bytes[3].toInt()
            pan += bytes[4].toInt()
        }
        assertEquals(500, wheel)
        assertEquals(-350, pan)
    }
    @Test fun failedReportCanBeRetriedWithoutLosingDistance() {
        val pending = PointerAccumulator()
        pending.move(300, -200)
        val failed = pending.take(127, true)
        pending.restore(failed)
        var x = 0
        var y = 0
        while (pending.hasPending) { val d = pending.take(127, true); x += d.x; y += d.y }
        assertEquals(300, x)
        assertEquals(-200, y)
    }
    @Test fun disconnectClearsMovementAndScroll() {
        val pending = PointerAccumulator()
        pending.move(100, 200)
        pending.scroll(30, 40)
        pending.clear()
        assertFalse(pending.hasPending)
        assertEquals(PointerDelta(0, 0, 0, 0), pending.take(127, true))
    }
    @Test fun unsupportedHorizontalScrollDoesNotCreateEndlessPendingData() {
        val pending = PointerAccumulator()
        pending.scroll(0, 500)
        assertEquals(0, pending.take(32767, false).pan)
        assertFalse(pending.hasPending)
    }
    @Test fun largeInputDoesNotOverflowAnIntegerBeforeClipping() {
        val pending = PointerAccumulator()
        pending.move(Int.MAX_VALUE, 0)
        pending.move(Int.MAX_VALUE, 0)
        assertEquals(32767, pending.take(32767, true).x)
    }
}
