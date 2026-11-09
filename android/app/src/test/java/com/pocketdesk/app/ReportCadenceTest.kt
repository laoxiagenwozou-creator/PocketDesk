package com.pocketdesk.app

import com.pocketdesk.app.input.ReportCadence
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCadenceTest {
    @Test
    fun `send cost is subtracted from the next wait`() {
        val cadence = ReportCadence(8)
        assertEquals(8L, cadence.reset(0))
        assertEquals(6L, cadence.nextDelay(10))
        assertEquals(6L, cadence.nextDelay(18))
    }

    @Test
    fun `missed slots are skipped instead of replayed in a burst`() {
        val cadence = ReportCadence(8)
        cadence.reset(0)
        assertEquals(4L, cadence.nextDelay(28))
        assertEquals(7L, cadence.nextDelay(33))
    }

    @Test
    fun `exact deadline skips to the next future slot`() {
        val cadence = ReportCadence(8)
        cadence.reset(0)
        assertEquals(8L, cadence.nextDelay(24))
    }

    @Test
    fun `restarting establishes a fresh timeline`() {
        val cadence = ReportCadence(8)
        cadence.reset(0)
        cadence.nextDelay(10)
        assertEquals(8L, cadence.reset(100))
        assertEquals(5L, cadence.nextDelay(111))
    }
}
