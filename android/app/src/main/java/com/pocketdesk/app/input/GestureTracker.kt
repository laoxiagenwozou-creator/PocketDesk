package com.pocketdesk.app.input

import kotlin.math.sqrt

data class GestureContact(val id: Long, val x: Float, val y: Float)

enum class GestureMotionKind { NONE, POINTER, SCROLL, NAVIGATION }

data class GestureMotion(
    val kind: GestureMotionKind,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val dtMs: Float = 1f,
    val contactsChanged: Boolean = false,
)

class GestureTracker {
    private var previous = emptyList<GestureContact>()
    private var previousAt = 0L
    private var tapDragCandidate = false

    var maxPointers = 0
        private set
    var totalMove = 0f
        private set

    val tapDragArmed: Boolean get() = tapDragCandidate && maxPointers == 1

    fun start(contacts: List<GestureContact>, uptimeMillis: Long, tapDragCandidate: Boolean) {
        previous = contacts
        previousAt = uptimeMillis
        this.tapDragCandidate = tapDragCandidate
        maxPointers = contacts.size
        totalMove = 0f
    }

    fun update(contacts: List<GestureContact>, uptimeMillis: Long): GestureMotion {
        maxPointers = maxOf(maxPointers, contacts.size)
        val changed = previous.map { it.id }.toSet() != contacts.map { it.id }.toSet()
        val dtMs = (uptimeMillis - previousAt).toFloat().coerceAtLeast(1f)
        val kind = when {
            contacts.isEmpty() -> GestureMotionKind.NONE
            contacts.size >= 3 -> GestureMotionKind.NAVIGATION
            contacts.size == 2 && maxPointers == 2 -> GestureMotionKind.SCROLL
            contacts.size == 1 && maxPointers == 1 -> GestureMotionKind.POINTER
            else -> GestureMotionKind.NONE
        }
        var dx = 0f
        var dy = 0f
        if (!changed && contacts.isNotEmpty() && kind != GestureMotionKind.NONE) {
            var distance = 0f
            for (contact in contacts) {
                val old = previous.first { it.id == contact.id }
                val x = contact.x - old.x
                val y = contact.y - old.y
                dx += x
                dy += y
                distance += sqrt(x * x + y * y)
            }
            dx /= contacts.size
            dy /= contacts.size
            totalMove += distance / contacts.size
        }
        previous = contacts
        previousAt = uptimeMillis
        return GestureMotion(kind, dx, dy, dtMs, changed)
    }
}
