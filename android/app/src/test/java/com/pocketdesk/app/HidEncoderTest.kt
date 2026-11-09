package com.pocketdesk.app

import com.pocketdesk.app.transport.hid.HidEncoder
import com.pocketdesk.app.transport.hid.HidProfiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HidEncoderTest {

    private val linkpad = HidProfiles.A_LINKPAD
    private val beckon = HidProfiles.B_BECKON

    @Test
    fun `linkpad mouse report is five bytes and clamps deltas`() {
        val report = HidEncoder.mouseReport(linkpad, buttons = 0x01, dx = 200, dy = -300, wheel = 3, pan = -2)
        assertEquals(linkpad.mouseReportSize, report.size)
        assertEquals(0x01, report[0].toInt())
        assertEquals(127, report[1].toInt())
        assertEquals(-127, report[2].toInt())
        assertEquals(3, report[3].toInt())
        assertEquals(-2, report[4].toInt())
    }

    @Test
    fun `beckon mouse report encodes 16-bit little endian deltas`() {
        val report = HidEncoder.mouseReport(beckon, buttons = 0, dx = 300, dy = -2, wheel = 1, pan = 0)
        assertEquals(6, report.size)
        assertEquals(0, report[0].toInt())
        assertEquals(300 and 0xFF, report[1].toInt() and 0xFF)
        assertEquals((300 shr 8) and 0xFF, report[2].toInt() and 0xFF)
        assertEquals((-2) and 0xFF, report[3].toInt() and 0xFF)
        assertEquals(((-2) shr 8) and 0xFF, report[4].toInt() and 0xFF)
        assertEquals(1, report[5].toInt())
    }

    @Test
    fun `keyboard report matches boot layout`() {
        val report = HidEncoder.keyboardReport(linkpad, modifiers = 0x08, keys = intArrayOf(0x06, 0x19))
        assertEquals(8, report.size)
        assertEquals(0x08, report[0].toInt())
        assertEquals(0, report[1].toInt())
        assertEquals(0x06, report[2].toInt())
        assertEquals(0x19, report[3].toInt())
        assertEquals(0, report[4].toInt())
    }

    @Test
    fun `empty keyboard report releases all keys`() {
        val report = HidEncoder.keyboardReport(linkpad, modifiers = 0, keys = IntArray(6))
        assertArrayEquals(ByteArray(8), report)
    }

    @Test
    fun `consumer report is little endian usage`() {
        val report = HidEncoder.consumerReport(linkpad, 0x00CD)
        assertEquals(2, report.size)
        assertEquals(0xCD, report[0].toInt() and 0xFF)
        assertEquals(0x00, report[1].toInt())
    }

    @Test
    fun `button mask is cut to what each descriptor declares`() {
        // Five buttons on the Linkpad layout, three on Beckon; a stray high bit must
        // not travel as a phantom button.
        assertEquals(0x1F, HidEncoder.mouseReport(linkpad, 0xFF, 0, 0, 0, 0)[0].toInt() and 0xFF)
        assertEquals(0x07, HidEncoder.mouseReport(beckon, 0xFF, 0, 0, 0, 0)[0].toInt() and 0xFF)
    }

    @Test
    fun `wide pointer keeps large deltas that an 8-bit report would clip`() {
        val report = HidEncoder.mouseReport(beckon, buttons = 0, dx = 40000, dy = -40000, wheel = 0, pan = 0)
        assertEquals(32767 and 0xFF, report[1].toInt() and 0xFF)
        assertEquals((32767 shr 8) and 0xFF, report[2].toInt() and 0xFF)
        assertEquals((-32767) and 0xFF, report[3].toInt() and 0xFF)
        assertEquals(((-32767) shr 8) and 0xFF, report[4].toInt() and 0xFF)
    }

    @Test
    fun `report ids differ between profiles`() {
        assertEquals(1, linkpad.keyboardReportId)
        assertEquals(2, linkpad.mouseReportId)
        assertEquals(1, beckon.mouseReportId)
        assertEquals(2, beckon.keyboardReportId)
    }
}
