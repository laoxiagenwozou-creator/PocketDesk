package com.pocketdesk.app.input

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

internal class AimPointer(var sensitivity: Float = 1.8f) {
    private var azimuth = 0f
    private var elevation = 0f
    private var hasSample = false
    private var lastTimeNs = 0L
    private var carryX = 0f
    private var carryY = 0f
    private val filterX = OneEuroFilter()
    private val filterY = OneEuroFilter()

    fun center() {
        azimuth = 0f
        elevation = 0f
        hasSample = false
        lastTimeNs = 0L
        carryX = 0f
        carryY = 0f
        filterX.reset()
        filterY.reset()
    }

    fun update(gx: Float, gy: Float, gz: Float, wx: Float, wy: Float, wz: Float, timeNs: Long): Pair<Int, Int> {
        val magnitude = sqrt(gx * gx + gy * gy + gz * gz)
        if (magnitude < 0.1f || timeNs <= lastTimeNs) return 0 to 0
        val nx = gx / magnitude
        val ny = gy / magnitude
        val nz = gz / magnitude
        val nextElevation = Math.toDegrees(asin(ny.coerceIn(-1f, 1f)).toDouble()).toFloat()
        val elapsed = ((timeNs - lastTimeNs) / 1_000_000_000f).let { if (it > 0.25f) 0f else it }
        val verticalRate = wx * nx + wy * ny + wz * nz
        val yaw = Math.toDegrees((verticalRate * elapsed).toDouble()).toFloat()
        azimuth += yaw
        azimuth = ((azimuth + 180f) % 360f + 360f) % 360f - 180f
        lastTimeNs = timeNs
        if (!hasSample) {
            hasSample = true
            elevation = nextElevation
            return 0 to 0
        }
        val dx = -filterX.filter(yaw, timeNs / 1_000_000_000.0)
        val dy = -filterY.filter(nextElevation - elevation, timeNs / 1_000_000_000.0)
        elevation = nextElevation
        // Relative HID cannot place the pointer at an absolute screen point. The current
        // aim direction is therefore converted into bounded pointer movement each sample.
        carryX += dx.coerceIn(-12f, 12f) * sensitivity * 7f
        carryY += dy.coerceIn(-12f, 12f) * sensitivity * 7f
        val outX = if (abs(carryX) >= 1f) carryX.toInt() else 0
        val outY = if (abs(carryY) >= 1f) carryY.toInt() else 0
        carryX -= outX
        carryY -= outY
        return outX.coerceIn(-127, 127) to outY.coerceIn(-127, 127)
    }
}
