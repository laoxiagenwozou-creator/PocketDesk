package com.pocketdesk.app.input

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure gesture math, kept out of Compose so it can be unit-tested on the JVM.
 *
 * Sub-pixel carry matters: a slow drag produces fractions of a "device unit" per
 * frame, and dropping them makes the pointer feel sticky at low speeds.
 */
class TouchpadMath(
    var sensitivity: Float = 1.8f,
    /**
     * Device units → the units the curve was tuned in. The UI passes
     * `TUNED_DENSITY / density`, so the same finger travel crosses the same physical
     * distance on another screen, and stays exactly 1.0 on the panel this was tuned on.
     */
    var gainScale: Float = 1f,
) {
    /**
     * Pointer acceleration, laptop-trackpad style: slow fingers stay linear and
     * precise, a fast flick travels much further. Off = the old linear curve.
     */
    var acceleration: Boolean = true

    private var carryX = 0f
    private var carryY = 0f

    private var scrollCarryY = 0f
    private var scrollCarryX = 0f

    /** Pixels of finger travel per wheel notch. */
    var pixelsPerNotch: Float = 56f

    fun reset() {
        carryX = 0f
        carryY = 0f
        scrollCarryY = 0f
        scrollCarryX = 0f
    }

    /**
     * Returns integer pointer deltas for a raw finger delta, keeping the remainder.
     *
     * [dtMs] is the time since the previous sample. The acceleration curve is defined
     * per [REF_FRAME_MS] frame, so a 120 Hz panel — smaller, more frequent events —
     * no longer reads as "slower fingers" than a 60 Hz one.
     */
    fun pointer(rawDx: Float, rawDy: Float, dtMs: Float = REF_FRAME_MS): Pair<Int, Int> {
        if (rawDx == 0f && rawDy == 0f) return 0 to 0
        val dx = rawDx * gainScale
        val dy = rawDy * gainScale
        val rate = REF_FRAME_MS / dtMs.coerceIn(MIN_FRAME_MS, MAX_FRAME_MS)
        val speed = sqrt(dx * dx + dy * dy) * rate
        val gain = sensitivity * speedFactor(speed)
        carryX += dx * gain
        carryY += dy * gain
        val outX = if (abs(carryX) >= 1f) carryX.roundToInt() else 0
        val outY = if (abs(carryY) >= 1f) carryY.roundToInt() else 0
        carryX -= outX
        carryY -= outY
        return outX to outY
    }

    private fun speedFactor(speed: Float): Float {
        if (!acceleration) return 1f
        // Touch events arrive roughly every 10 ms, so `speed` reads as px per event.
        // Quadratic ease-in: nothing changes below LOW_SPEED, +ACCEL_GAIN at the top.
        val t = ((speed - LOW_SPEED) / (HIGH_SPEED - LOW_SPEED)).coerceIn(0f, 1f)
        return 1f + t * t * ACCEL_GAIN
    }

    private companion object {
        /** The frame length the curve was tuned against (the "定点 8ms" pointer stream). */
        const val REF_FRAME_MS = 8f

        /** Guard rails for a stalled or burst-delivered event stream. */
        const val MIN_FRAME_MS = 2f
        const val MAX_FRAME_MS = 40f

        /** Below this speed (units per reference frame) the curve stays linear — precision work. */
        const val LOW_SPEED = 3f

        /** Speed at which the curve tops out. */
        const val HIGH_SPEED = 24f

        /** Extra travel multiplier at the top of the curve. */
        const val ACCEL_GAIN = 1.5f
    }

    /** Returns wheel notches for raw finger travel (Android Y grows downward). */
    fun scroll(rawDx: Float, rawDy: Float): Pair<Int, Int> {
        scrollCarryY += rawDy
        scrollCarryX += rawDx
        val notchesY = (scrollCarryY / pixelsPerNotch).toInt()
        val notchesX = (scrollCarryX / pixelsPerNotch).toInt()
        scrollCarryY -= notchesY * pixelsPerNotch
        scrollCarryX -= notchesX * pixelsPerNotch
        return notchesX to notchesY
    }
}
