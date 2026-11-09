package com.pocketdesk.app.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.input.KeyboardInput
import com.pocketdesk.app.transport.hid.HidKeymap
import com.pocketdesk.app.ui.theme.LocalNeumorph
import com.pocketdesk.app.ui.theme.NeuSunk
import com.pocketdesk.app.ui.theme.neuPressed
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Repeating navigation and delete keys share the same delay and cadence.
private const val REPEAT_START_MS = 380L
private const val REPEAT_EVERY_MS = 55L

private val LETTER_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    listOf("123", "⌘", "空格", "，", "。", "⏎"),
)

private val SYMBOL_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("-", "=", "[", "]", ";", "'", "/", "\\", "`"),
    listOf("⇧", "!", "@", "#", "$", "%", "^", "&", "*"),
    listOf("ABC", "⌘", "空格", "媒体", "⏎"),
)

@Composable
internal fun KeyboardPane(service: KeyboardInput, hostIsMac: Boolean) {
    var page by remember { mutableIntStateOf(0) }
    var shift by remember { mutableStateOf(false) }
    var cmd by remember { mutableStateOf(false) }
    val haptics = service.hapticsEnabled()

    val foreground = rememberInputForeground()
    BackHandler(enabled = page != 0 && foreground) {
        page = 0
        shift = false
        cmd = false
    }
    if (page == 2) {
        MediaPane(service = service, onPage = { page = it })
        return
    }
    val rows = if (page == 0) LETTER_ROWS else SYMBOL_ROWS
    KeyPad(rows = rows, shift = shift, cmd = cmd, haptics = haptics) { token ->
        when (token) {
            "⇧" -> shift = !shift
            "⌘" -> cmd = !cmd
            "123" -> {
                page = 1
                shift = false
                cmd = false
            }
            "ABC" -> {
                page = 0
                shift = false
                cmd = false
            }
            "媒体" -> {
                page = 2
                shift = false
                cmd = false
            }
            else -> {
                sendToken(service, token, shift, cmd, hostIsMac)
                shift = false
                cmd = false
            }
        }
    }
}

@Composable
private fun KeyPad(
    rows: List<List<String>>,
    shift: Boolean,
    cmd: Boolean,
    haptics: Boolean,
    onToken: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { index, row ->
            // The digits row is a slim auxiliary strip: shorter, dimmer, quiet.
            val slim = index == 0
            Row(
                if (slim) Modifier.fillMaxWidth().height(30.dp) else Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { token ->
                    KeyButton(
                        label = token,
                        active = (token == "⇧" && shift) || (token == "⌘" && cmd),
                        haptics = haptics,
                        slim = slim,
                        repeatOnHold = token == "⌫",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onToken(token) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun KeyButton(
    label: String,
    active: Boolean,
    haptics: Boolean = false,
    slim: Boolean = false,
    repeatOnHold: Boolean = false,
    captureOnHold: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val foreground = rememberInputForeground()
    val inputEnabled = enabled && foreground
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val neu = LocalNeumorph.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val currentClick by rememberUpdatedState(onClick)
    val currentHaptics by rememberUpdatedState(haptics)
    // The repeating key drives its own press state: it does not use `clickable`, so
    // the interaction source never sees the press.
    var repeatHeld by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    InputPauseEffect {
        repeatJob?.cancel()
        repeatJob = null
        repeatHeld = false
    }
    val press by animateFloatAsState(if (pressed || repeatHeld) 1f else 0f, tween(70), label = "keyPress")
    val bodyColor = when {
        active -> MaterialTheme.colorScheme.secondary
        slim -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        active && neu -> MaterialTheme.colorScheme.onSecondary
        active -> MaterialTheme.colorScheme.background
        slim -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val tapModifier = if (repeatOnHold) {
        Modifier.pointerInput(inputEnabled) {
            if (!inputEnabled) return@pointerInput
            detectTapGestures(
                onPress = press@ {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@press
                    repeatHeld = true
                    if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val heldClick = currentClick
                    heldClick()
                    val repeat = scope.launch {
                        delay(REPEAT_START_MS)
                        while (isActive) {
                            if (captureOnHold) heldClick() else currentClick()
                            delay(REPEAT_EVERY_MS)
                        }
                    }
                    repeatJob = repeat
                    try {
                        tryAwaitRelease()
                    } finally {
                        repeat.cancel()
                        if (repeatJob === repeat) repeatJob = null
                        repeatHeld = false
                    }
                },
            )
        }
    } else {
        Modifier.clickable(enabled = inputEnabled, interactionSource = interaction, indication = null) {
            if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentClick()
        }
    }
    val shape = if (neu) {
        Modifier
            .neuPressed(
                press = press,
                corner = 14.dp,
                surface = bodyColor,
                sunk = if (active) MaterialTheme.colorScheme.secondary else NeuSunk,
                offset = if (slim) 2.5.dp else 3.dp,
                blur = if (slim) 5.dp else 7.dp,
            )
            .then(tapModifier)
    } else {
        Modifier
            .clip(KeyShape)
            .background(bodyColor)
            .then(
                if (repeatOnHold) tapModifier
                else Modifier.clickable(enabled = inputEnabled) {
                    if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentClick()
                },
            )
    }
    Box(modifier.then(if (neu) Modifier.graphicsLayer() else Modifier).then(shape)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when (label) {
                    "空格" -> tr("空格", "Space")
                    "媒体" -> tr("媒体", "Media")
                    "播放/暂停" -> tr("播放/暂停", "Play/Pause")
                    "下一曲" -> tr("下一曲", "Next")
                    "上一曲" -> tr("上一曲", "Previous")
                    "静音" -> tr("静音", "Mute")
                    "音量 +" -> tr("音量 +", "Volume +")
                    "音量 −" -> tr("音量 −", "Volume −")
                    else -> label
                },
                style = if (slim) MaterialTheme.typography.labelSmall
                else MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

private fun sendToken(
    service: KeyboardInput,
    token: String,
    shift: Boolean,
    cmd: Boolean,
    hostIsMac: Boolean,
) {
    var mods = 0
    if (shift) mods = mods or Modifiers.LEFT_SHIFT
    if (cmd) mods = mods or Modifiers.primary(hostIsMac)
    when (token) {
        "空格" -> service.tapKey(HidKeymap.SPACE, mods)
        "⌫" -> service.tapKey(HidKeymap.BACKSPACE, mods)
        "⏎" -> service.tapKey(HidKeymap.ENTER, mods)
        "，" -> service.tapKey(HidKeymap.forChar(',')!!.first, mods)
        "。" -> service.tapKey(HidKeymap.forChar('.')!!.first, mods)
        else -> {
            val char = token.firstOrNull() ?: return
            val mapped = HidKeymap.forChar(char) ?: return
            service.tapKey(mapped.first, mapped.second or mods)
        }
    }
}

@Composable
private fun MediaPane(service: KeyboardInput, onPage: (Int) -> Unit) {
    val haptics = service.hapticsEnabled()
    val items = listOf(
        "播放/暂停" to HidKeymap.Consumer.PLAY_PAUSE,
        "下一曲" to HidKeymap.Consumer.NEXT_TRACK,
        "上一曲" to HidKeymap.Consumer.PREV_TRACK,
        "静音" to HidKeymap.Consumer.MUTE,
        "音量 +" to HidKeymap.Consumer.VOLUME_UP,
        "音量 −" to HidKeymap.Consumer.VOLUME_DOWN,
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { (label, usage) ->
                    KeyButton(
                        label = label,
                        active = false,
                        haptics = haptics,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { service.media(usage) },
                    )
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyButton("ABC", false, haptics, modifier = Modifier.weight(1f).fillMaxHeight()) { onPage(0) }
            KeyButton("123", false, haptics, modifier = Modifier.weight(1f).fillMaxHeight()) { onPage(1) }
            KeyButton("媒体", true, haptics, modifier = Modifier.weight(1f).fillMaxHeight()) { }
        }
    }
}
