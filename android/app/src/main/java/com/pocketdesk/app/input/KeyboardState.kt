package com.pocketdesk.app.input

class KeyboardState(private val maxKeys: Int = 6) {
    private val slots = IntArray(maxKeys)
    private val slotModifiers = IntArray(maxKeys)
    private var standaloneModifiers = 0

    val modifiers: Int get() = slotModifiers.fold(standaloneModifiers) { mask, held -> mask or held }

    fun press(usage: Int, modifiers: Int): Boolean {
        if (usage == 0) {
            standaloneModifiers = standaloneModifiers or modifiers
            return true
        }
        val existing = slots.indexOf(usage)
        val index = if (existing >= 0) existing else slots.indexOf(0)
        if (index < 0) return false
        slots[index] = usage
        slotModifiers[index] = slotModifiers[index] or modifiers
        return true
    }

    fun release(usage: Int, modifiers: Int) {
        if (usage == 0) {
            standaloneModifiers = standaloneModifiers and modifiers.inv()
            return
        }
        val index = slots.indexOf(usage)
        if (index >= 0) {
            slots[index] = 0
            slotModifiers[index] = 0
        }
    }

    fun keys(): IntArray = slots.copyOf()

    fun clear() {
        standaloneModifiers = 0
        slots.fill(0)
        slotModifiers.fill(0)
    }
}
