package com.pocketdesk.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalEnglish = staticCompositionLocalOf { false }

@Composable
internal fun tr(chinese: String, english: String): String = if (LocalEnglish.current) english else chinese

internal fun englishError(message: String): String = when {
    message == "本机没有蓝牙适配器" -> "This device has no Bluetooth adapter"
    message == "缺少 BLUETOOTH_CONNECT 权限" -> "Bluetooth connect permission is missing"
    message == "蓝牙未开启" -> "Bluetooth is off"
    message == "系统回收了 HID Profile" -> "The system disconnected the HID profile"
    message == "HID 注册被系统撤销" -> "The system revoked HID registration"
    message.startsWith("连接超时") -> "Connection timed out. Remove this phone in the host Bluetooth settings and pair again."
    message.startsWith("系统拒绝提供 HID_DEVICE") -> "The system rejected the HID device profile; this phone may not support it."
    message.startsWith("5s 内未取得") -> "HID profile timed out; this phone may not support HID device mode."
    message.startsWith("8s 内未收到") -> "HID registration timed out; the system may have rejected the HID role."
    message.startsWith("registerApp 返回 false") -> "HID registration was rejected; another app may be using it."
    message.startsWith("HID connect 返回 false") -> "The HID connection request was rejected."
    message.startsWith("获取 HID Device Profile 失败:") -> "Failed to acquire HID profile: " + message.substringAfter(':').trim()
    message.startsWith("registerApp 抛异常:") -> "HID registration failed: " + message.substringAfter(':').trim()
    message.startsWith("createBond 失败:") -> "Pairing failed: " + message.substringAfter(':').trim()
    message.startsWith("HID connect 异常:") -> "HID connection failed: " + message.substringAfter(':').trim()
    else -> message
}

@Composable
internal fun profileLabel(profile: com.pocketdesk.app.transport.hid.HidProfile): String =
    if (!LocalEnglish.current) profile.label else when (profile.id) {
        "linkpad" -> "A · Keyboard first (keyboard 1 / mouse 2)"
        "beckon" -> "B · Mouse first (mouse 1 / keyboard 2)"
        else -> profile.label
    }

@Composable
internal fun profileNote(profile: com.pocketdesk.app.transport.hid.HidProfile): String =
    if (!LocalEnglish.current) profile.note else when (profile.id) {
        "linkpad" -> "Five mouse buttons and horizontal scroll. Keyboard uses report ID 1. Based on Linkpad."
        "beckon" -> "16-bit relative movement, three mouse buttons, no horizontal scroll. Based on Beckon."
        else -> profile.note
    }

@Composable
internal fun transportLabel(state: com.pocketdesk.app.core.TransportState): String {
    if (!LocalEnglish.current) return state.label
    return when (state) {
        com.pocketdesk.app.core.TransportState.Idle -> "Not started"
        com.pocketdesk.app.core.TransportState.Registering -> "Registering HID…"
        com.pocketdesk.app.core.TransportState.Ready -> "Registered · Waiting for host"
        is com.pocketdesk.app.core.TransportState.Connecting -> "Connecting · ${state.deviceName ?: "Unknown device"}"
        is com.pocketdesk.app.core.TransportState.Connected -> "Connected · ${state.deviceName ?: state.address}"
        is com.pocketdesk.app.core.TransportState.Error -> "Error · ${englishError(state.message)}"
    }
}
