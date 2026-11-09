package com.pocketdesk.app.core

/**
 * Transport-independent input vocabulary.
 *
 * The UI and gesture layer only ever produce these events; nothing above this
 * layer knows that Bluetooth HID exists. A future Wi-Fi / Pad-Bridge / LAN-Host
 * adapter implements [InputTransport] and receives exactly the same events.
 */
sealed interface InputEvent {

    /** Relative pointer movement in "device" units (already sensitivity-scaled). */
    data class PointerMove(val dx: Int, val dy: Int) : InputEvent

    /** Absolute button state: bit0 left, bit1 right, bit2 middle, bit3 back, bit4 forward. */
    data class PointerButtons(val mask: Int) : InputEvent

    data class WheelPulse(val vertical: Int, val session: Long, val createdAtNanos: Long = System.nanoTime()) : InputEvent

    /** Raw HID wheel notches; positive vertical follows the host's wheel-up direction. */
    data class Scroll(val vertical: Int, val horizontal: Int = 0) : InputEvent

    /** A single key transition. [usage] is a HID keyboard usage id, [modifiers] a HID modifier mask. */
    data class KeyChord(val usage: Int, val modifiers: Int = 0, val down: Boolean) : InputEvent

    data class KeyTap(val usage: Int, val modifiers: Int = 0, val holdMs: Long = 14) : InputEvent

    /** Type printable ASCII text (HID cannot carry arbitrary Unicode / IME composition). */
    data class Text(val text: String) : InputEvent

    /** Consumer-control usage (media keys), pressed and released once. */
    data class Consumer(val usage: Int) : InputEvent
}

/** HID mouse button bits shared by UI and transports. */
object Buttons {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04
    const val BACK = 0x08
    const val FORWARD = 0x10
}

/** HID keyboard modifier bits. */
object Modifiers {
    const val NONE = 0x00
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_GUI = 0x08
    const val RIGHT_CTRL = 0x10
    const val RIGHT_SHIFT = 0x20
    const val RIGHT_ALT = 0x40
    const val RIGHT_GUI = 0x80

    /** macOS Command or Windows Ctrl, chosen by the user's host profile. */
    fun primary(hostIsMac: Boolean): Int = if (hostIsMac) LEFT_GUI else LEFT_CTRL
}
