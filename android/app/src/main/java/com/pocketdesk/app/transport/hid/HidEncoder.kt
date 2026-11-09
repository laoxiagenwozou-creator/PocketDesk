package com.pocketdesk.app.transport.hid

/**
 * Pure HID report encoding — no Android imports, unit-tested on the JVM.
 *
 * Report layouts are defined by the descriptor in [HidProfile]; a mismatch here
 * shows up on the host as "device connected but nothing moves", which is why the
 * encoder is kept separate from the Bluetooth plumbing.
 */
object HidEncoder {

    private fun clamp8(value: Int): Int = value.coerceIn(-127, 127)

    fun mouseReport(
        profile: HidProfile,
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int,
        pan: Int,
    ): ByteArray {
        return if (profile.widePointer) {
            val x = dx.coerceIn(-32767, 32767)
            val y = dy.coerceIn(-32767, 32767)
            byteArrayOf(
                (buttons and 0x07).toByte(),
                (x and 0xFF).toByte(),
                ((x shr 8) and 0xFF).toByte(),
                (y and 0xFF).toByte(),
                ((y shr 8) and 0xFF).toByte(),
                clamp8(wheel).toByte()
            )
        } else {
            byteArrayOf(
                (buttons and 0x1F).toByte(),
                clamp8(dx).toByte(),
                clamp8(dy).toByte(),
                clamp8(wheel).toByte(),
                clamp8(if (profile.hasHorizontalScroll) pan else 0).toByte()
            )
        }
    }

    fun keyboardReport(profile: HidProfile, modifiers: Int, keys: IntArray): ByteArray {
        val report = ByteArray(profile.keyboardReportSize)
        report[0] = (modifiers and 0xFF).toByte()
        report[1] = 0
        for (i in 0 until minOf(6, profile.keyboardReportSize - 2)) {
            report[2 + i] = (keys.getOrElse(i) { 0 } and 0xFF).toByte()
        }
        return report
    }

    fun consumerReport(profile: HidProfile, usage: Int): ByteArray {
        val report = ByteArray(profile.consumerReportSize)
        report[0] = (usage and 0xFF).toByte()
        if (report.size > 1) report[1] = ((usage shr 8) and 0xFF).toByte()
        return report
    }
}
