package com.pocketdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.input.KeyboardInput
import com.pocketdesk.app.transport.hid.HidKeymap
import kotlin.math.abs

private data class ExtendedKey(val label: String, val usage: Int, val repeats: Boolean = false)

private val COMMON_KEYS = listOf(
    ExtendedKey("Esc", HidKeymap.ESCAPE), ExtendedKey("Tab", HidKeymap.TAB),
    ExtendedKey("Home", HidKeymap.HOME), ExtendedKey("End", HidKeymap.END),
    ExtendedKey("PgUp", HidKeymap.PAGE_UP, true), ExtendedKey("PgDn", HidKeymap.PAGE_DOWN, true),
    ExtendedKey("↑", HidKeymap.UP_ARROW, true), ExtendedKey("Del", HidKeymap.DELETE, true),
    ExtendedKey("←", HidKeymap.LEFT_ARROW, true), ExtendedKey("↓", HidKeymap.DOWN_ARROW, true),
    ExtendedKey("→", HidKeymap.RIGHT_ARROW, true), ExtendedKey("Enter", HidKeymap.ENTER),
)
private val FUNCTION_KEYS = (1..12).map { ExtendedKey("F$it", HidKeymap.fKey(it)) }

@Composable
internal fun AuxiliaryPane(service: KeyboardInput, hostIsMac: Boolean, connected: Boolean) {
    var page by remember { mutableIntStateOf(0) }
    var modifiers by remember { mutableIntStateOf(0) }
    val foreground = rememberInputForeground()
    val haptics = service.hapticsEnabled()
    DisposableEffect(service, foreground, connected, page) {
        if (!foreground || !connected) modifiers = 0
        service.setContinuousScroll(0f)
        onDispose { service.setContinuousScroll(0f) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = maxHeight
        val rowHeight = ((panelHeight - 72.dp) / 4).coerceAtLeast(48.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("常用键", "Common"), "F1–F12", tr("持续滚动", "Auto scroll")).forEachIndexed { index, title ->
                    KeyButton(title, page == index, haptics, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        service.setContinuousScroll(0f)
                        modifiers = 0
                        page = index
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (page == 2) {
                    ScrollLever(service, connected && foreground, (panelHeight - 54.dp).coerceAtLeast(190.dp))
                } else {
                    val capturedModifiers = modifiers
                    (if (page == 0) COMMON_KEYS else FUNCTION_KEYS).chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { key ->
                                KeyButton(key.label, false, haptics, repeatOnHold = key.repeats,
                                    captureOnHold = true, modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    service.tapKey(key.usage, capturedModifiers)
                                    modifiers = 0
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Shift" to Modifiers.LEFT_SHIFT, "Ctrl" to Modifiers.LEFT_CTRL,
                            "Alt" to Modifiers.LEFT_ALT, (if (hostIsMac) "Cmd" else "Win") to Modifiers.LEFT_GUI)
                            .forEach { (label, bit) ->
                                KeyButton(label, modifiers and bit != 0, haptics,
                                    modifier = Modifier.weight(1f).fillMaxHeight()) { modifiers = modifiers xor bit }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollLever(service: KeyboardInput, enabled: Boolean, height: androidx.compose.ui.unit.Dp) {
    var position by remember { mutableFloatStateOf(0f) }
    var gestureEpoch by remember { mutableIntStateOf(0) }
    InputPauseEffect {
        gestureEpoch++
        position = 0f
        service.setContinuousScroll(0f)
    }
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val leverDescription = tr("持续滚动拨杆", "Continuous scroll lever")
    val leverState = when {
        !enabled -> tr("不可用", "Unavailable")
        position > 0.12f -> tr("向上滚动", "Scrolling up")
        position < -0.12f -> tr("向下滚动", "Scrolling down")
        else -> tr("已停止", "Stopped")
    }
    DisposableEffect(service, enabled) {
        position = 0f
        onDispose {
            position = 0f
            service.setContinuousScroll(0f)
        }
    }
    Column(Modifier.fillMaxWidth().height(height), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (enabled) tr("越远越快 · 松手停止", "Move farther for speed · release to stop") else tr("连接主机并关闭拖动后可用", "Connect a host and turn off drag lock"),
            style = MaterialTheme.typography.labelMedium, color = muted)
        Box(Modifier.weight(1f).widthIn(max = 240.dp).fillMaxWidth().padding(horizontal = 20.dp)
            .clip(KeyShape).background(surface)
            .semantics {
                contentDescription = leverDescription
                stateDescription = leverState
                if (!enabled) disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val epoch = gestureEpoch
                    fun update(y: Float) {
                        position = (1f - 2f * y / size.height).coerceIn(-1f, 1f)
                        service.setContinuousScroll(position)
                    }
                    try {
                        update(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (epoch != gestureEpoch || !change.pressed || change.isConsumed || change.position.x !in 0f..size.width.toFloat() ||
                                change.position.y !in 0f..size.height.toFloat()) break
                            change.consume()
                            update(change.position.y)
                        }
                    } finally {
                        position = 0f
                        service.setContinuousScroll(0f)
                    }
                }
            }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                val x = size.width / 2
                drawLine(muted.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), 4.dp.toPx(), StrokeCap.Round)
                drawLine(muted.copy(alpha = 0.35f), Offset(x - 16.dp.toPx(), size.height / 2),
                    Offset(x + 16.dp.toPx(), size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                val y = size.height / 2 * (1f - position)
                drawCircle(if (enabled && abs(position) > 0.12f) accent else muted, 12.dp.toPx(), Offset(x, y))
            }
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(tr("↑ 向上", "↑ Up"), style = MaterialTheme.typography.labelMedium, color = muted)
                Text(tr("↓ 向下", "↓ Down"), style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
    }
}
