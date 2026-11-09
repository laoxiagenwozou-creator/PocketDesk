package com.pocketdesk.app.core

import kotlinx.coroutines.flow.StateFlow

/** Observable state of an input transport, shared by UI, service and diagnostics. */
sealed interface TransportState {
    data object Idle : TransportState
    data object Registering : TransportState
    data object Ready : TransportState
    data class Connecting(val deviceName: String?) : TransportState
    data class Connected(val deviceName: String?, val address: String) : TransportState
    data class Error(val message: String) : TransportState

    val label: String
        get() = when (this) {
            Idle -> "未启动"
            Registering -> "注册 HID 中…"
            Ready -> "已注册 · 等待主机连接"
            is Connecting -> "连接中 · ${deviceName ?: "未知设备"}"
            is Connected -> "已连接 · ${deviceName ?: address}"
            is Error -> "错误 · $message"
        }

    val isConnected: Boolean get() = this is Connected
}

/**
 * A transport is the only thing that knows how input reaches the target device.
 * Bluetooth HID today; Wi-Fi Pad-Bridge / LAN Host later, without touching UI.
 */
interface InputTransport {
    val state: StateFlow<TransportState>
    fun start()
    fun stop()
    fun connect(address: String?)
    fun disconnect()
    fun send(event: InputEvent)
}
