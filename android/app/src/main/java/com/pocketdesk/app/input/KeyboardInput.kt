package com.pocketdesk.app.input

interface KeyboardInput {
    fun hapticsEnabled(): Boolean
    fun tapKey(usage: Int, modifiers: Int = 0)
    fun media(usage: Int)
    fun setContinuousScroll(position: Float)
}
