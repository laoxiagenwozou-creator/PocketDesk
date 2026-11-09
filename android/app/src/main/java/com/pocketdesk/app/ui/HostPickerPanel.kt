package com.pocketdesk.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketdesk.app.core.AppTheme
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.service.HidService

/**
 * Tap the status line to switch host. One HID link at a time, so picking a
 * device disconnects the current one first.
 */
@Composable
internal fun HostPicker(
    service: HidService,
    state: TransportState,
    onPick: (String) -> Unit,
    onDiscoverable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices = remember { service.bondedDevices() }
    val current = (state as? TransportState.Connected)?.address
    PanelSurface(modifier = modifier.fillMaxWidth(), quiet = true) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(tr("连接到", "Connect to"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                tr("点一个名字就换过去。电脑搜不到手机时，点最下面那个按钮。", "Tap a device to switch. If the host cannot find this phone, use the button below."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (devices.isEmpty()) {
                    Text(
                        tr("还没有配对过的设备。", "No paired devices yet."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    devices.forEach { device ->
                        HostRow(
                            name = service.deviceLabel(device),
                            selected = device.address == current,
                            onClick = { onPick(device.address) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyChip(tr("让电脑重新发现", "Make discoverable"), active = true, onClick = onDiscoverable)
        }
    }
}

@Composable
private fun HostRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(180),
        label = "hostRow",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "●" else "○",
            color = if (selected) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * The menu panel: the things that used to sit in the top bar or in diagnostics.
 * 外观 (five skins), 目标系统, 灵敏度, then the three pages.
 */
@Composable
internal fun MenuPanel(
    theme: AppTheme,
    onTheme: (AppTheme) -> Unit,
    hostIsMac: Boolean,
    onHostIsMac: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivity: (Float) -> Unit,
    aimEnabled: Boolean,
    onAimEnabled: (Boolean) -> Unit,
    aimSensitivity: Float,
    onAimSensitivity: (Float) -> Unit,
    english: Boolean,
    onEnglish: (Boolean) -> Unit,
    onComposer: () -> Unit,
    onTutorial: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier.width(252.dp), quiet = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            MenuLabel(tr("语言", "Language"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyChip("中文", active = !english, center = true, modifier = Modifier.weight(1f)) { onEnglish(false) }
                KeyChip("English", active = english, center = true, modifier = Modifier.weight(1f)) { onEnglish(true) }
            }
            Spacer(Modifier.height(12.dp))
            MenuLabel(tr("外观", "Appearance"))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppTheme.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { option ->
                            KeyChip(
                                label = if (english) when (option) { AppTheme.NIGHT -> "Night"; AppTheme.SAND -> "Sand"; AppTheme.SLATE -> "Slate"; AppTheme.MOSS -> "Moss"; AppTheme.HAPTIC -> "Porcelain" } else option.label,
                                active = option == theme,
                                center = true,
                                modifier = Modifier.weight(1f),
                            ) { onTheme(option) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MenuLabel(tr("目标系统", "Target system"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyChip("macOS", active = hostIsMac, center = true, modifier = Modifier.weight(1f)) {
                    onHostIsMac(true)
                }
                KeyChip("Windows", active = !hostIsMac, center = true, modifier = Modifier.weight(1f)) {
                    onHostIsMac(false)
                }
            }
            Spacer(Modifier.height(12.dp))
            MenuLabel(tr("触控板灵敏度", "Touchpad sensitivity") + " ${"%.1f".format(sensitivity)}×")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("慢", "Slow"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivity,
                    valueRange = 0.8f..3.5f,
                    colors = sliderColors(),
                    modifier = Modifier.weight(1f).height(28.dp),
                )
                Text(
                    tr("快", "Fast"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            MenuLabel(tr("手机移动控制鼠标", "Phone motion pointer"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyChip(tr("关闭", "Off"), active = !aimEnabled, center = true, modifier = Modifier.weight(1f)) { onAimEnabled(false) }
                KeyChip(tr("开启", "On"), active = aimEnabled, center = true, modifier = Modifier.weight(1f)) { onAimEnabled(true) }
            }
            MenuLabel(tr("移动灵敏度", "Motion sensitivity") + " ${"%.1f".format(aimSensitivity)}×")
            Slider(value = aimSensitivity, onValueChange = onAimSensitivity, valueRange = 1f..8f,
                colors = sliderColors(), modifier = Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
            Spacer(Modifier.height(4.dp))
            MenuEntry(tr("文字", "Text"), onComposer)
            MenuEntry(tr("教程", "Tutorial"), onTutorial)
            MenuEntry(tr("诊断", "Diagnostics"), onDiagnostics)
        }
    }
}
