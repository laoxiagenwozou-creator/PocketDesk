package com.pocketdesk.app.transport.hid

import com.pocketdesk.app.core.Modifiers

/**
 * US-layout HID keyboard usage mapping.
 *
 * HID sends key positions, not characters: the host layout decides the glyph.
 * That is why non-Latin text (Chinese) cannot be produced this way without an IME
 * on the host — Phase P0 records this limit instead of pretending it works.
 */
object HidKeymap {

    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val CAPS_LOCK = 0x39
    const val DELETE = 0x4C

    const val RIGHT_ARROW = 0x4F
    const val LEFT_ARROW = 0x50
    const val DOWN_ARROW = 0x51
    const val UP_ARROW = 0x52

    const val HOME = 0x4A
    const val END = 0x4D
    const val PAGE_UP = 0x4B
    const val PAGE_DOWN = 0x4E

    fun fKey(n: Int): Int = 0x3A + (n - 1).coerceIn(0, 11)

    /** Consumer-control usages (USB HID Usage Tables 1.4, §15). */
    object Consumer {
        const val PLAY_PAUSE = 0x00CD
        const val NEXT_TRACK = 0x00B5
        const val PREV_TRACK = 0x00B6
        const val STOP = 0x00B7
        const val VOLUME_UP = 0x00E9
        const val VOLUME_DOWN = 0x00EA
        const val MUTE = 0x00E2
    }

    private val shiftedDigits = mapOf(
        '!' to 0x1E, '@' to 0x1F, '#' to 0x20, '$' to 0x21, '%' to 0x22,
        '^' to 0x23, '&' to 0x24, '*' to 0x25, '(' to 0x26, ')' to 0x27,
    )

    private val shiftedPunctuation = mapOf(
        '_' to 0x2D, '+' to 0x2E, '{' to 0x2F, '}' to 0x30, '|' to 0x31,
        ':' to 0x33, '"' to 0x34, '~' to 0x35, '<' to 0x36, '>' to 0x37,
        '?' to 0x38,
    )

    private val plainPunctuation = mapOf(
        '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30, '\\' to 0x31,
        ';' to 0x33, '\'' to 0x34, '`' to 0x35, ',' to 0x36, '.' to 0x37,
        '/' to 0x38,
    )

    /** Returns usage + required modifier mask, or null when HID cannot type this char. */
    fun forChar(c: Char): Pair<Int, Int>? {
        return when {
            c in 'a'..'z' -> (0x04 + (c - 'a')) to Modifiers.NONE
            c in 'A'..'Z' -> (0x04 + (c - 'A')) to Modifiers.LEFT_SHIFT
            c in '1'..'9' -> (0x1E + (c - '1')) to Modifiers.NONE
            c == '0' -> 0x27 to Modifiers.NONE
            shiftedDigits.containsKey(c) -> shiftedDigits.getValue(c) to Modifiers.LEFT_SHIFT
            plainPunctuation.containsKey(c) -> plainPunctuation.getValue(c) to Modifiers.NONE
            shiftedPunctuation.containsKey(c) -> shiftedPunctuation.getValue(c) to Modifiers.LEFT_SHIFT
            c == ' ' -> SPACE to Modifiers.NONE
            c == '\n' -> ENTER to Modifiers.NONE
            c == '\t' -> TAB to Modifiers.NONE
            else -> null
        }
    }

    /** True when every character of [text] can be sent over HID in a US layout. */
    fun isTypeable(text: String): Boolean = text.all { forChar(it) != null }
}
