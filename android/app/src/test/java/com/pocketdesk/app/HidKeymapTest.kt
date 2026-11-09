package com.pocketdesk.app

import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.transport.hid.HidKeymap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Key positions are the whole contract with the host: a wrong usage types the wrong
 * character, and nothing above this layer can notice.
 */
class HidKeymapTest {

    @Test
    fun `every printable ASCII character can be typed`() {
        val missing = (0x20..0x7E).map { it.toChar() }.filter { HidKeymap.forChar(it) == null }
        assertEquals("unmappable ASCII: $missing", emptyList<Char>(), missing)
    }

    @Test
    fun `letters and digits use the boot keyboard usages`() {
        assertEquals(0x04 to Modifiers.NONE, HidKeymap.forChar('a'))
        assertEquals(0x1D to Modifiers.NONE, HidKeymap.forChar('z'))
        assertEquals(0x1E to Modifiers.NONE, HidKeymap.forChar('1'))
        assertEquals(0x26 to Modifiers.NONE, HidKeymap.forChar('9'))
        assertEquals(0x27 to Modifiers.NONE, HidKeymap.forChar('0'))
    }

    @Test
    fun `uppercase and shifted symbols carry left shift`() {
        assertEquals(0x04 to Modifiers.LEFT_SHIFT, HidKeymap.forChar('A'))
        assertEquals(0x1E to Modifiers.LEFT_SHIFT, HidKeymap.forChar('!'))
        assertEquals(0x27 to Modifiers.LEFT_SHIFT, HidKeymap.forChar(')'))
        assertEquals(0x38 to Modifiers.LEFT_SHIFT, HidKeymap.forChar('?'))
        assertEquals(0x2D to Modifiers.LEFT_SHIFT, HidKeymap.forChar('_'))
        assertEquals(0x2D to Modifiers.NONE, HidKeymap.forChar('-'))
    }

    @Test
    fun `whitespace maps to its own keys`() {
        assertEquals(HidKeymap.SPACE to Modifiers.NONE, HidKeymap.forChar(' '))
        assertEquals(HidKeymap.ENTER to Modifiers.NONE, HidKeymap.forChar('\n'))
        assertEquals(HidKeymap.TAB to Modifiers.NONE, HidKeymap.forChar('\t'))
    }

    @Test
    fun `non ascii characters are refused instead of guessed`() {
        assertNull(HidKeymap.forChar('中'))
        assertNull(HidKeymap.forChar('é'))
        assertNull(HidKeymap.forChar('\uD83D')) // lone surrogate from an emoji
        assertFalse(HidKeymap.isTypeable("你好"))
        assertTrue(HidKeymap.isTypeable("Hello, world!"))
        assertNotNull(HidKeymap.forChar('~'))
    }

    @Test
    fun `function keys start at f1`() {
        assertEquals(0x3A, HidKeymap.fKey(1))
        assertEquals(0x45, HidKeymap.fKey(12))
    }
}
