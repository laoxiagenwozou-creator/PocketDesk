package com.pocketdesk.app

import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.input.KeyboardState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStateTest {

    @Test
    fun `six keys are held in the order they were pressed`() {
        val kb = KeyboardState()
        (0x04..0x09).forEach { assertTrue(kb.press(it, Modifiers.NONE)) }
        assertArrayEquals(intArrayOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09), kb.keys())
    }

    @Test
    fun `a seventh key is dropped and reported`() {
        val kb = KeyboardState()
        (0x04..0x09).forEach { kb.press(it, Modifiers.NONE) }
        assertFalse("seventh key must not fit", kb.press(0x0A, Modifiers.NONE))
        assertArrayEquals(intArrayOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09), kb.keys())
    }

    @Test
    fun `releasing a key frees its slot for the next press`() {
        val kb = KeyboardState()
        (0x04..0x09).forEach { kb.press(it, Modifiers.NONE) }
        kb.release(0x06, Modifiers.NONE)
        assertTrue(kb.press(0x0A, Modifiers.NONE))
        assertArrayEquals(intArrayOf(0x04, 0x05, 0x0A, 0x07, 0x08, 0x09), kb.keys())
    }

    @Test
    fun `releasing an absent key leaves a reused slot alone`() {
        val kb = KeyboardState()
        kb.press(0x04, Modifiers.NONE)
        kb.release(0x04, Modifiers.NONE)
        kb.press(0x0B, Modifiers.NONE)
        // 0x04 is no longer held; releasing it again must not clear 0x0B.
        kb.release(0x04, Modifiers.NONE)
        assertArrayEquals(intArrayOf(0x0B, 0, 0, 0, 0, 0), kb.keys())
    }

    @Test
    fun `modifiers compose across chords`() {
        val kb = KeyboardState()
        kb.press(0x06, Modifiers.LEFT_CTRL)
        assertEquals(Modifiers.LEFT_CTRL, kb.modifiers)
        kb.press(0x19, Modifiers.LEFT_SHIFT)
        assertEquals(Modifiers.LEFT_CTRL or Modifiers.LEFT_SHIFT, kb.modifiers)
        kb.release(0x19, Modifiers.LEFT_SHIFT)
        assertEquals(Modifiers.LEFT_CTRL, kb.modifiers)
        kb.release(0x06, Modifiers.LEFT_CTRL)
        assertEquals(Modifiers.NONE, kb.modifiers)
    }

    @Test
    fun `press without a usage only sets modifiers`() {
        val kb = KeyboardState()
        assertTrue(kb.press(0, Modifiers.LEFT_GUI))
        assertEquals(Modifiers.LEFT_GUI, kb.modifiers)
        assertArrayEquals(IntArray(6), kb.keys())
    }

    @Test
    fun `repeated press of a held key does not consume a second slot`() {
        val kb = KeyboardState()
        kb.press(0x04, Modifiers.NONE)
        kb.press(0x04, Modifiers.NONE)
        assertArrayEquals(intArrayOf(0x04, 0, 0, 0, 0, 0), kb.keys())
    }

    @Test
    fun `clear forgets everything held`() {
        val kb = KeyboardState()
        kb.press(0x04, Modifiers.LEFT_SHIFT)
        kb.clear()
        assertEquals(Modifiers.NONE, kb.modifiers)
        assertArrayEquals(IntArray(6), kb.keys())
    }
    @Test
    fun `releasing one shifted key keeps shift for another held key`() {
        val kb = KeyboardState()
        kb.press(0x04, Modifiers.LEFT_SHIFT)
        kb.press(0x05, Modifiers.LEFT_SHIFT)
        kb.release(0x04, Modifiers.LEFT_SHIFT)
        assertEquals(Modifiers.LEFT_SHIFT, kb.modifiers)
    }

    @Test
    fun `rejected seventh key cannot add modifiers`() {
        val kb = KeyboardState()
        (0x04..0x09).forEach { kb.press(it, Modifiers.NONE) }
        assertFalse(kb.press(0x0A, Modifiers.LEFT_CTRL))
        assertEquals(Modifiers.NONE, kb.modifiers)
    }

    @Test
    fun `release of rejected key cannot clear another key modifier`() {
        val kb = KeyboardState()
        kb.press(0x04, Modifiers.LEFT_CTRL)
        kb.release(0x05, Modifiers.LEFT_CTRL)
        assertEquals(Modifiers.LEFT_CTRL, kb.modifiers)
    }

}
