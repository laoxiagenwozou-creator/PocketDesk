package com.pocketdesk.app.input

enum class MouseButtonSource { LEFT_PAD, RIGHT_PAD, TAP_DRAG, DRAG_LOCK }

class MouseButtonState {
    private val owners = mutableMapOf<Any, Int>()

    val mask: Int get() = owners.values.fold(0) { mask, held -> mask or held }
    val heldMask: Int get() = owners.entries.fold(0) { mask, (owner, held) ->
        if (owner is MouseButtonSource) mask or held else mask
    }

    fun clear() { owners.clear() }

    fun set(owner: Any, mask: Int): Int {
        if (mask == 0) owners.remove(owner) else owners[owner] = mask
        return this.mask
    }
}
