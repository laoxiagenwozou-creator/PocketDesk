package com.pocketdesk.app.input

class ReportCadence(private val periodNanos: Long) {
    init {
        require(periodNanos > 0)
    }

    private var deadline = 0L

    fun reset(nowNanos: Long): Long {
        deadline = nowNanos + periodNanos
        return periodNanos
    }

    fun nextDelay(nowNanos: Long): Long {
        deadline += periodNanos
        if (deadline <= nowNanos) {
            deadline += ((nowNanos - deadline) / periodNanos + 1) * periodNanos
        }
        return deadline - nowNanos
    }
}
