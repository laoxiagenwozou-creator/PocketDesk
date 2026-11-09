package com.pocketdesk.app.input

interface TouchpadInput {
    fun hapticsEnabled(): Boolean
    fun hintSeen(): Boolean
    fun setHintSeen()
    fun cancelFling()
    fun move(dx: Int, dy: Int)
    fun scroll(vertical: Int, horizontal: Int = 0)
    fun setHeldButtons(source: MouseButtonSource, mask: Int)
    fun hasHeldButtons(): Boolean
    fun click(mask: Int, holdMs: Long = 32)
    fun doubleClick(mask: Int)
    fun fling(vertical: Float, horizontal: Float)
    fun desktopSwitch(hostIsMac: Boolean, next: Boolean)
    fun overview(hostIsMac: Boolean)
    fun centerAim() = Unit
}
