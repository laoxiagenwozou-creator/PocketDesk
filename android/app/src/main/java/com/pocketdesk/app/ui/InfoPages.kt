package com.pocketdesk.app.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketdesk.app.core.LogBus
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.transport.hid.BluetoothHidTransport
import com.pocketdesk.app.transport.hid.HidKeymap
import com.pocketdesk.app.transport.hid.HidProfiles
import kotlinx.coroutines.delay

// ─────────────────────────────── 教程 ───────────────────────────────

private data class TutorialItem(
    val title: String,
    val body: String,
    val fingers: List<Offset> = emptyList(),
    val moves: List<Pair<Offset, Offset>> = emptyList(),
    /** Draw a ripple around each finger: this gesture is a tap, not a slide. */
    val tap: Boolean = false,
    /** Draw a pressed button bar: something is being held down. */
    val hold: Boolean = false,
    /** Draw arrow heads on both ends of the movement line. */
    val bothWays: Boolean = false,
)

private val TUTORIAL_ITEMS = listOf(
    TutorialItem(
        "手机移动控制鼠标",
        "在右上角菜单开启手机移动控制鼠标，并用移动灵敏度单独调节速度。触控板灵敏度不会改变这一设置。",
    ),
    TutorialItem(
        "侧边滚动条",
        "点触控板左上角或右上角的小圆环，显示或隐藏对应一侧的滚动条。沿细线滑动可以滚动页面。",
    ),
    TutorialItem(
        "扩展按键",
        "点动作栏的「扩展」，切换常用键或 F1–F12。先点 Shift、Ctrl、Alt 或 Win/Cmd，再点目标键即可组合；方向、翻页和 Del 支持长按重复。点「收起」或系统返回恢复原键盘。",
    ),
    TutorialItem(
        "持续滚动拨杆",
        "在「扩展 → 持续滚动」中，按住拨杆向上或向下推，离中心越远越快。松手、移出拨杆、收起、切页、切后台或断线都会停止；开启拖动时不可用。",
    ),
    TutorialItem(
        "单指移动",
        "手指在上方触控板区域滑动，主机上的指针会跟着移动。",
        listOf(Offset(0.28f, 0.68f)),
        listOf(Offset(0.28f, 0.68f) to Offset(0.64f, 0.30f)),
    ),
    TutorialItem(
        "轻点 = 左键",
        "手指轻轻碰一下再抬起，等于单击鼠标左键。",
        listOf(Offset(0.5f, 0.5f)),
        tap = true,
    ),
    TutorialItem(
        "双击 = 双击",
        "快速连点两下，等于双击左键，用来打开文件或文件夹。",
        listOf(Offset(0.42f, 0.5f), Offset(0.58f, 0.5f)),
        tap = true,
    ),
    TutorialItem(
        "轻点后按住滑 = 拖拽",
        "先轻点一下，紧接着把手指按上去滑动，就是按住左键拖动（不用去按下面的按钮）。",
        listOf(Offset(0.30f, 0.66f)),
        listOf(Offset(0.30f, 0.66f) to Offset(0.72f, 0.32f)),
    ),
    TutorialItem(
        "三指轻点 = 中键",
        "三根手指一起轻点，等于单击鼠标中键（常用于新标签打开链接）。",
        listOf(Offset(0.38f, 0.5f), Offset(0.5f, 0.5f), Offset(0.62f, 0.5f)),
        tap = true,
    ),
    TutorialItem(
        "三指左右滑 = 切换桌面",
        "三根手指一起向左或向右滑，切到上一个 / 下一个桌面。Windows 是虚拟桌面，Mac 是调度中心的空间。",
        listOf(Offset(0.40f, 0.52f), Offset(0.52f, 0.52f), Offset(0.64f, 0.52f)),
        listOf(Offset(0.36f, 0.52f) to Offset(0.74f, 0.52f)),
        bothWays = true,
    ),
    TutorialItem(
        "三指上滑 = 全部窗口",
        "三根手指一起向上一滑，打开 Mac 的调度中心或 Windows 的任务视图，所有窗口一眼看完。",
        listOf(Offset(0.40f, 0.66f), Offset(0.52f, 0.66f), Offset(0.64f, 0.66f)),
        listOf(Offset(0.52f, 0.72f) to Offset(0.52f, 0.30f)),
    ),
    TutorialItem(
        "晃一晃手机 = 找鼠标",
        "远程画面里找不到指针时，拿手机轻轻晃一下：先震一下告诉你收到了，指针在原地画个圈。熄屏或没连接时不响应。",
        listOf(Offset(0.5f, 0.5f)),
        tap = true,
    ),
    TutorialItem(
        "按住左键拖拽",
        "按住左下角的「左键」，另一根手指在板面滑动，就能拖窗口、选文字；也可以按住左键后直接滑动这根手指。左键不松开，板面可以抬指后继续滑。松开左键就放下。",
        listOf(Offset(0.34f, 0.62f), Offset(0.60f, 0.40f)),
        listOf(Offset(0.60f, 0.40f) to Offset(0.82f, 0.24f)),
        hold = true,
    ),
    TutorialItem(
        "双指上下滑 = 滚动",
        "两根手指一起上下滑动，滚动网页或文档。快速一甩会带着惯性继续滑一段，跟笔记本触控板一样。",
        listOf(Offset(0.42f, 0.52f), Offset(0.58f, 0.52f)),
        listOf(Offset(0.5f, 0.66f) to Offset(0.5f, 0.30f)),
        bothWays = true,
    ),
    TutorialItem(
        "双指左右滑 = 横滚",
        "两根手指一起左右滑动，横向滚动表格或宽页面。",
        listOf(Offset(0.44f, 0.54f), Offset(0.56f, 0.54f)),
        listOf(Offset(0.60f, 0.54f) to Offset(0.86f, 0.54f)),
        bothWays = true,
    ),
    TutorialItem(
        "双指轻点 = 右键",
        "两根手指同时轻点一下，等于单击右键。",
        listOf(Offset(0.44f, 0.5f), Offset(0.58f, 0.5f)),
        tap = true,
    ),
    TutorialItem(
        "拖动锁",
        "左右两块大按钮中间那个小胶囊：点一下，左键一直按住；再点一下松开。适合长距离拖拽。",
        listOf(Offset(0.5f, 0.42f)),
        hold = true,
    ),
    TutorialItem(
        "灵敏度",
        "右上角菜单里的滑杆调整指针快慢，改完立刻生效。",
        listOf(Offset(0.3f, 0.5f)),
    ),
    TutorialItem(
        "复制 / 粘贴 / 截图",
        "键盘上方那条悬浮栏：复制、粘贴会按目标系统自动用 Ctrl 或 ⌘；截图在 Windows 是 Win+Shift+S、在 Mac 是 ⌘⇧4，按完直接在画面上拖一圈框选。目标系统在右上角菜单里切换。",
    ),
    TutorialItem(
        "键盘",
        "最上面一排是数字。点 ⇧ 或 ⌘ 会锁定一次，接着按字母就是大写或快捷键（⌘ 在 Windows 上是 Ctrl）。点 123 换符号页；按住 ⌫ 会连续删除。",
        listOf(Offset(0.5f, 0.5f)),
    ),
)

private val TUTORIAL_ENGLISH = listOf(
    "Phone motion pointer" to "Enable phone motion control in the menu. Its sensitivity is independent of the touchpad setting.",
    "Edge scrolling" to "Tap the small ring at either top corner of the touchpad to show or hide that side's scroll strip. Slide along the line to scroll.",
    "Extra keys" to "Choose Extra to open common keys or F1–F12. Select Shift, Ctrl, Alt or Win/Cmd before a key for a shortcut. Arrows, page keys and Delete repeat when held. Choose Close or Back to return.",
    "Continuous scrolling" to "In Extra → Continuous scroll, hold the lever up or down. Moving farther scrolls faster. Releasing, leaving the lever, changing pages, backgrounding or disconnecting stops it. Drag lock must be off.",
    "Move with one finger" to "Slide one finger on the touchpad to move the pointer.",
    "Tap = left click" to "Tap once for a left click.",
    "Double tap" to "Tap twice quickly to open a file or folder.",
    "Tap, hold and slide to drag" to "Tap, then immediately touch and slide to drag with the left button held.",
    "Three-finger tap = middle click" to "Tap with three fingers to middle-click, often used to open a link in a new tab.",
    "Three-finger horizontal swipe" to "Swipe left or right with three fingers to switch desktops or spaces.",
    "Three-finger swipe up" to "Swipe up with three fingers to open Mission Control on Mac or Task View on Windows.",
    "Shake to find the pointer" to "Gently shake the phone to draw a circle with the pointer. The screen must be on and the device connected.",
    "Hold left button to drag" to "Hold the left button while sliding on the pad, or slide the finger holding the button. You can lift and reposition the other finger while continuing to hold the button.",
    "Two-finger vertical scroll" to "Slide two fingers up or down to scroll. A quick flick continues with momentum.",
    "Two-finger horizontal scroll" to "Slide two fingers left or right to scroll horizontally.",
    "Two-finger tap = right click" to "Tap with two fingers to right-click.",
    "Drag lock" to "Tap the pill between the mouse buttons to hold the left button; tap again to release it.",
    "Sensitivity" to "Adjust touchpad speed in the menu. Changes take effect immediately.",
    "Copy / paste / screenshot" to "The action bar uses the shortcut for the selected target system. Screenshots use Win+Shift+S or Cmd+Shift+4.",
    "Keyboard" to "Tap Shift or Cmd for one key. Choose 123 for symbols; hold Backspace to repeat.",
)

@Composable
internal fun TutorialView(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("教程", "Tutorial"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(tr("返回", "Back")) }
        }
        Text(
            tr("上半部分是触控板（两个下角是左右键），中间是常用动作条，最下面是键盘。", "The touchpad is at the top, mouse buttons are in its lower corners, followed by the action bar and keyboard."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        TUTORIAL_ITEMS.forEachIndexed { index, item ->
            val translated = if (LocalEnglish.current) TUTORIAL_ENGLISH[index] else null
            TutorialRow(if (translated == null) item else item.copy(title = translated.first, body = translated.second))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            tr("中文打不出来是正常的：蓝牙键盘只能发送按键位置，中文要在电脑上用自己的输入法。", "Bluetooth HID sends key positions. Use the host input method for Chinese text."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TutorialRow(item: TutorialItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.fingers.isNotEmpty() || item.moves.isNotEmpty() || item.tap || item.hold) {
            GestureArt(
                fingers = item.fingers,
                moves = item.moves,
                tap = item.tap,
                hold = item.hold,
                bothWays = item.bothWays,
                modifier = Modifier
                    .size(width = 84.dp, height = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tiny diagram: finger dots plus movement arrows, in normalized coordinates. */
@Composable
private fun GestureArt(
    fingers: List<Offset>,
    moves: List<Pair<Offset, Offset>>,
    tap: Boolean = false,
    hold: Boolean = false,
    bothWays: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.secondary
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        moves.forEach { (from, to) ->
            val a = Offset(from.x * w, from.y * h)
            val b = Offset(to.x * w, to.y * h)
            drawLine(accent.copy(alpha = 0.85f), a, b, strokeWidth = 3f)
            arrowHead(a, b, accent)
            if (bothWays) arrowHead(b, a, accent)
        }
        if (hold) {
            val barW = w * 0.42f
            val barH = h * 0.16f
            val center = Offset(w * 0.5f, h * 0.86f)
            drawRoundRect(
                color = accent.copy(alpha = 0.55f),
                topLeft = Offset(center.x - barW / 2, center.y - barH / 2),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH / 2),
            )
        }
        fingers.forEach { p ->
            val center = Offset(p.x * w, p.y * h)
            drawCircle(color = ink.copy(alpha = 0.9f), radius = 8f, center = center)
            if (tap) {
                drawCircle(
                    color = accent.copy(alpha = 0.7f),
                    radius = 15f,
                    center = center,
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

private fun DrawScope.arrowHead(from: Offset, tip: Offset, color: Color) {
    val dir = tip - from
    val len = dir.getDistance().coerceAtLeast(1f)
    val unit = dir / len
    val perp = Offset(-unit.y, unit.x)
    val base = tip - unit * 9f
    drawLine(color, tip, base + perp * 5f, 3f)
    drawLine(color, tip, base - perp * 5f, 3f)
}

// ─────────────────────────────── 文字通道 ───────────────────────────────

/**
 * Type (or dictate) on the phone, then push the text to the host.
 *
 * Bluetooth HID can only carry key positions, so this sends ASCII exactly and
 * reports what it cannot send instead of silently dropping it. The host IME handles Chinese.
 */
@Composable
internal fun ComposerView(service: HidService, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val english = LocalEnglish.current
    val quickPhrases = listOf("hello", "thanks", "one moment please")

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("文字", "Text"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(tr("返回", "Back")) }
        }
        Text(
            tr("用手机输入法打字或语音输入，点「发送」直接打到电脑上。", "Type or dictate on your phone, then send supported characters to the host."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text(tr("要发送的文字", "Text to send")) },
            placeholder = { Text(tr("英文 / 数字 / 符号可以直接发；中文见下方说明", "ASCII letters, numbers and symbols can be sent.")) },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyChip(tr("发送", "Send"), active = true) {
                val typeable = text.filter { HidKeymap.forChar(it) != null }
                val dropped = text.length - typeable.length
                if (typeable.isEmpty()) {
                    status = if (english) "No supported characters to send." else "没有可发送的字符：中文暂时发不出去（见下方说明）"
                } else if (!service.typeText(typeable)) {
                    status = if (english) "Connect to a host before sending." else "尚未连接主机，请连接后再发送"
                } else {
                    status = if (english) {
                        "Queued ${typeable.length} characters; skipped $dropped unsupported characters."
                    } else if (dropped == 0) {
                        "已排队 ${typeable.length} 个字符"
                    } else {
                        "已排队 ${typeable.length} 个字符；跳过 $dropped 个无法发送的字符"
                    }
                }
            }
            KeyChip(tr("清空", "Clear")) { text = ""; status = "" }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickPhrases.forEach { phrase ->
                KeyChip(phrase) {
                    status = if (service.typeText(phrase)) {
                        if (english) "Queued: $phrase" else "已排队：$phrase"
                    } else if (english) "Connect to a host before sending." else "尚未连接主机，请连接后再发送"
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            status.ifEmpty { "　" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            tr("蓝牙键盘发送的是按键位置。中文请在电脑上用已有输入法输入。", "Bluetooth HID sends key positions. Use the host input method for Chinese text."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────── 诊断 ───────────────────────────────

@Composable
internal fun DiagnosticsView(
    service: HidService,
    sensitivity: Float,
    onSensitivity: (Float) -> Unit,
    acceleration: Boolean,
    onAcceleration: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onShareLog: (String) -> Unit,
) {
    val state by service.transport.state.collectAsStateWithLifecycle()
    val logs by LogBus.lines.collectAsStateWithLifecycle()
    val devices = remember { service.bondedDevices() }
    var sendMode by remember { mutableStateOf(service.sendMode()) }
    var haptics by remember { mutableStateOf(service.hapticsEnabled()) }
    var shakeFind by remember { mutableStateOf(service.shakeFindEnabled()) }
    var testText by remember { mutableStateOf("") }
    var sentText by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    LaunchedEffect((state as? com.pocketdesk.app.core.TransportState.Connected)?.address) {
        testText = ""
        sentText = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("诊断", "Diagnostics"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(tr("返回", "Back")) }
        }
        SectionTitle(tr("连接状态", "Connection"))
        InfoRow(tr("状态", "Status"), transportLabel(state))
        TransportCounters(service.transport)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyChip(tr("蓝牙设置", "Bluetooth settings")) { onOpenBluetoothSettings() }
            KeyChip(tr("分享日志", "Share log")) { onShareLog(LogBus.exportText()) }
        }
        Spacer(Modifier.height(12.dp))
        KeyChip(if (advanced) tr("收起高级诊断", "Hide advanced diagnostics") else tr("展开高级诊断", "Show advanced diagnostics"), active = advanced) { advanced = !advanced }
        if (advanced) {
        SectionTitle(tr("设备", "Device"))
        InfoRow(tr("型号", "Model"), "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow(tr("系统", "System"), "Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}")
        InfoRow(tr("蓝牙名", "Bluetooth name"), service.transport.adapterName())
        InfoRow(tr("蓝牙地址", "Bluetooth address"), service.transport.adapterAddress())
        InfoRow(tr("蓝牙状态", "Bluetooth"), if (service.transport.isBluetoothEnabled()) tr("已开启", "On") else tr("已关闭", "Off"))

        SectionTitle(tr("HID 通道", "HID channel"))
        InfoRow(tr("状态", "Status"), transportLabel(state))
        InfoRow(tr("当前描述符", "Current descriptor"), profileLabel(HidProfiles.byId(service.profileId())))
        TransportCounters(service.transport)

        SectionTitle(tr("手感调参", "Input settings"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("灵敏度", "Sensitivity") + " ${"%.1f".format(sensitivity)}×",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(108.dp),
            )
            Slider(
                value = sensitivity,
                onValueChange = onSensitivity,
                valueRange = 0.8f..3.5f,
                colors = sliderColors(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modeLabel = when (sendMode) {
                BluetoothHidTransport.SendMode.COALESCED -> tr("发送节奏：合并 6ms", "Send: merged 6ms")
                BluetoothHidTransport.SendMode.DIRECT -> tr("发送节奏：直接", "Send: direct")
                BluetoothHidTransport.SendMode.FIXED_CADENCE -> tr("发送节奏：定点 8ms", "Send: fixed 8ms")
            }
            KeyChip(modeLabel, active = sendMode != BluetoothHidTransport.SendMode.COALESCED) {
                sendMode = when (sendMode) {
                    BluetoothHidTransport.SendMode.COALESCED -> BluetoothHidTransport.SendMode.FIXED_CADENCE
                    BluetoothHidTransport.SendMode.FIXED_CADENCE -> BluetoothHidTransport.SendMode.DIRECT
                    BluetoothHidTransport.SendMode.DIRECT -> BluetoothHidTransport.SendMode.COALESCED
                }
                service.setSendMode(sendMode)
            }
            KeyChip(if (haptics) tr("键盘震动：开", "Haptics: on") else tr("键盘震动：关", "Haptics: off"), active = haptics) {
                haptics = !haptics
                service.setHapticsEnabled(haptics)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyChip(if (acceleration) tr("指针加速：开", "Acceleration: on") else tr("指针加速：关", "Acceleration: off"), active = acceleration) {
                onAcceleration(!acceleration)
            }
            KeyChip(if (shakeFind) tr("晃动找鼠标：开", "Shake to find: on") else tr("晃动找鼠标：关", "Shake to find: off"), active = shakeFind) {
                shakeFind = !shakeFind
                service.setShakeFindEnabled(shakeFind)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr("合并 = 6ms 窗口内的事件并成一份报告；直接 = 每个触摸事件各发一份；定点 = 固定 8ms 节拍发送。指针加速：慢动保持精细、快甩走得更远。晃动找鼠标：连接后轻晃手机，指针原地画圈（亮屏才响应）。", "Merged combines events in a 6 ms window; direct sends each event; fixed sends every 8 ms. Acceleration moves farther on fast swipes. Shake to find draws a circle while connected and the screen is on."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(tr("文字测试（英文/符号，经 HID 直发）", "Text test (ASCII via HID)"))
        OutlinedTextField(
            value = testText,
            onValueChange = { raw ->
                val next = raw.filter { HidKeymap.forChar(it) != null }
                // The host cursor sits at the end of what we typed, so any edit becomes
                // "delete back to the first difference, then retype the rest". Comparing
                // lengths (the old code) sent the wrong keys when the edit was mid-string.
                val common = commonPrefixLength(sentText, next)
                repeat(sentText.length - common) { service.tapKey(HidKeymap.BACKSPACE) }
                val tail = next.substring(common)
                if (tail.isNotEmpty()) service.typeText(tail)
                sentText = next
                testText = next
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("输入即发送到主机", "Typing sends to host")) },
            enabled = state is com.pocketdesk.app.core.TransportState.Connected,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            minLines = 2,
        )
        Text(
            tr("HID 发送按键位置而非字符：中文需要在主机侧用 IME，本版未实现。", "HID sends key positions. Use an input method on the host for Chinese."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(tr("描述符方案（切换后需在主机端重新配对）", "HID descriptor (re-pair on the host after changing)"))
        HidProfiles.all.forEach { profile ->
            val selected = service.profileId() == profile.id
            Column(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { service.setProfileId(profile.id) }
                    .padding(12.dp),
            ) {
                Text(
                    (if (selected) "● " else "○ ") + profileLabel(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    profileNote(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle(tr("已配对设备", "Paired devices"))
        if (devices.isEmpty()) {
            Text(
                tr("没有已配对设备。请先在系统蓝牙设置里把手机与 Pad 配对。", "No paired devices. Pair the phone and host in Bluetooth settings first."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            devices.forEach { device ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        service.deviceLabel(device),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { service.connect(device.address) }) { Text(tr("连接", "Connect")) }
                }
            }
        }

        SectionTitle(tr("操作", "Actions"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyChip(tr("断开连接", "Disconnect")) { service.disconnect() }
            KeyChip(tr("蓝牙设置", "Bluetooth settings")) { onOpenBluetoothSettings() }
            KeyChip(tr("可被发现", "Make discoverable")) { onMakeDiscoverable() }
            KeyChip(tr("分享日志", "Share log")) { onShareLog(LogBus.exportText()) }
            KeyChip(tr("清空日志", "Clear log")) { LogBus.clear() }
        }

        SectionTitle(tr("日志（不含输入内容）", "Log (excludes input content)"))
        Text(
            logs.joinToString("\n").ifEmpty { if (LocalEnglish.current) "No log entries" else "暂无日志" },
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(bottom = 24.dp),
        )
        }
    }
}

/**
 * Live counters, with their own one-second tick.
 *
 * These used to ride a page-wide timer, which rebuilt the whole diagnostics page —
 * including the few thousand lines of log text — every second just to refresh a number.
 */
@Composable
private fun TransportCounters(transport: BluetoothHidTransport) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    InfoRow(
        tr("报告发送", "Reports sent"),
        "${transport.reportsSent.get()} · ${tr("失败", "Failed")} ${transport.reportsFailed.get()}",
    )
    InfoRow(tr("最近错误", "Last error"), transport.lastError ?: tr("无", "None"))
}

/** Length of the shared prefix of [a] and [b]. */
private fun commonPrefixLength(a: String, b: String): Int {
    val limit = minOf(a.length, b.length)
    var i = 0
    while (i < limit && a[i] == b[i]) i++
    return i
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.width(86.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
