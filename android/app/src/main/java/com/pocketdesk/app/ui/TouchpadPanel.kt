package com.pocketdesk.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdesk.app.core.Buttons
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.input.MouseButtonSource
import com.pocketdesk.app.input.GestureArbiter
import com.pocketdesk.app.input.GestureContact
import com.pocketdesk.app.input.GestureEnd
import com.pocketdesk.app.input.GestureMotionKind
import com.pocketdesk.app.input.GestureTracker
import com.pocketdesk.app.input.TouchpadInput
import com.pocketdesk.app.input.TouchpadMath
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.transport.hid.HidKeymap
import com.pocketdesk.app.ui.theme.LocalNeumorph
import com.pocketdesk.app.ui.theme.NeuSunk
import com.pocketdesk.app.ui.theme.neuDial
import com.pocketdesk.app.ui.theme.neuInset
import com.pocketdesk.app.ui.theme.neuPressed
import com.pocketdesk.app.ui.theme.neuRaised
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Density of the panel the pointer curve was tuned on (Find N5, 520 dpi). */
private const val TUNED_DENSITY = 3.25f

/** A second tap inside this window becomes a double click. */
private const val DOUBLE_TAP_WINDOW_MS = 320L

@Composable
internal fun TouchpadPanel(
    service: TouchpadInput,
    state: TransportState,
    dragLock: Boolean,
    onDragLock: (Boolean) -> Unit,
    sensitivity: Float,
    hostIsMac: Boolean,
    acceleration: Boolean,
    receipt: String?,
    scrollOnRight: Boolean = false,
) {
    // Pointer travel is expressed in the units the curve was tuned in, so the same
    // finger movement crosses the same physical distance on a denser or coarser panel.
    val density = LocalDensity.current.density.coerceAtLeast(0.5f)
    val math = remember {
        TouchpadMath(sensitivity = sensitivity, gainScale = TUNED_DENSITY / density)
    }
    math.sensitivity = sensitivity
    math.acceleration = acceleration
    math.gainScale = TUNED_DENSITY / density
    // Time of the last single tap: a second touch right after it becomes a drag,
    // and a second quick tap-release becomes a double click (laptop trackpad behaviour).
    var lastTapAt by remember { mutableLongStateOf(0L) }
    // The usage hint disappears for good after the pad has been used once.
    var showHint by remember { mutableStateOf(!service.hintSeen()) }
    var leftScroll by remember { mutableStateOf(true) }
    var rightScroll by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val neu = LocalNeumorph.current
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (neu) {
                        // The pad is the deepest well on the screen: everything the
                        // thumb presses sits on a different level than the sheet.
                        Modifier.neuInset(24.dp, MaterialTheme.colorScheme.surface, offset = 4.dp, blur = 8.dp)
                    } else {
                        Modifier
                            .clip(CardShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                CardShape,
                            )
                    },
                )
                .pointerInput(dragLock, hostIsMac, leftScroll, rightScroll) {
                    val slop = viewConfiguration.touchSlop
                    // Movement starts on the very first pixel. The old code waited for a
                    // full touch slop (~26 px) before sending anything, which read as
                    // "my finger moved but the pointer is late".
                    val moveThreshold = 1.5f
                    // Three fingers moving past this = switch desktop / open overview.
                    val swipeThreshold = 70f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val withinScrollHeight = down.position.y >= 54.dp.toPx() && down.position.y < size.height - 54.dp.toPx()
                        val onScrollEdge = withinScrollHeight &&
                            ((leftScroll && down.position.x < 24.dp.toPx()) ||
                                (rightScroll && down.position.x >= size.width - 24.dp.toPx()))
                        if (onScrollEdge) {
                            lastTapAt = 0L
                            return@awaitEachGesture
                        }
                        service.cancelFling()
                        // Ignore presses owned by another gesture handler.
                        if (down.isConsumed) {
                            lastTapAt = 0L
                            return@awaitEachGesture
                        }
                        math.reset()
                        val startedAt = down.uptimeMillis
                        val previousTapAt = lastTapAt
                        lastTapAt = 0L
                        val tracker = GestureTracker()
                        tracker.start(
                            currentEvent.changes.filter { it.pressed }.map {
                                GestureContact(it.id.value, it.position.x, it.position.y)
                            },
                            startedAt,
                            tapDragCandidate = !dragLock && !service.hasHeldButtons() && previousTapAt != 0L &&
                                startedAt - previousTapAt in 0..DOUBLE_TAP_WINDOW_MS,
                        )
                        currentEvent.changes.forEach { it.consume() }
                        var heldDuringGesture = service.hasHeldButtons()
                        var scrolled = false
                        var dragging = false
                        var dragHeld = false
                        var flingY = 0f
                        var flingX = 0f
                        var threeDx = 0f
                        var threeDy = 0f
                        var threeFingerMoved = false
                        var endedAt = startedAt
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) {
                                    lastTapAt = 0L
                                    return@awaitEachGesture
                                }
                                heldDuringGesture = heldDuringGesture || service.hasHeldButtons()
                                val pressed = event.changes.filter { it.pressed }
                                endedAt = event.changes.first().uptimeMillis
                                val motion = tracker.update(
                                    pressed.map { GestureContact(it.id.value, it.position.x, it.position.y) },
                                    endedAt,
                                )
                                event.changes.forEach { it.consume() }
                                if (pressed.isEmpty()) break
                                if (motion.contactsChanged) {
                                    math.reset()
                                    if (pressed.size >= 2) {
                                        flingY = 0f
                                        flingX = 0f
                                    }
                                    threeDx = 0f
                                    threeDy = 0f
                                }
                                if (dragHeld && tracker.maxPointers > 1) {
                                    service.setHeldButtons(MouseButtonSource.TAP_DRAG, 0)
                                    dragHeld = false
                                }
                                if (tracker.totalMove > moveThreshold && showHint) {
                                    showHint = false
                                    service.setHintSeen()
                                }
                                when (motion.kind) {
                                    GestureMotionKind.NAVIGATION -> {
                                        threeDx += motion.dx
                                        threeDy += motion.dy
                                        when {
                                            threeDx > swipeThreshold -> {
                                                service.desktopSwitch(hostIsMac, next = true)
                                                threeDx = 0f; threeDy = 0f
                                                threeFingerMoved = true
                                            }
                                            threeDx < -swipeThreshold -> {
                                                service.desktopSwitch(hostIsMac, next = false)
                                                threeDx = 0f; threeDy = 0f
                                                threeFingerMoved = true
                                            }
                                            threeDy < -swipeThreshold -> {
                                                service.overview(hostIsMac)
                                                threeDx = 0f; threeDy = 0f
                                                threeFingerMoved = true
                                            }
                                        }
                                    }
                                    GestureMotionKind.SCROLL -> {
                                        val (notchesX, notchesY) = math.scroll(motion.dx, motion.dy)
                                        if (notchesX != 0 || notchesY != 0) {
                                            service.scroll(notchesY, notchesX)
                                            scrolled = true
                                            flingY = flingY * 0.6f + (notchesY / motion.dtMs * 16f) * 0.4f
                                            flingX = flingX * 0.6f + (notchesX / motion.dtMs * 16f) * 0.4f
                                        }
                                    }
                                    GestureMotionKind.POINTER -> {
                                        if (tracker.totalMove > moveThreshold) {
                                            if (tracker.tapDragArmed && !dragging) {
                                                dragging = true
                                                dragHeld = true
                                                service.setHeldButtons(MouseButtonSource.TAP_DRAG, Buttons.LEFT)
                                            }
                                            val (dx, dy) = math.pointer(motion.dx, motion.dy, motion.dtMs)
                                            if (dx != 0 || dy != 0) service.move(dx, dy)
                                        }
                                    }
                                    GestureMotionKind.NONE -> Unit
                                }
                            }
                            if (heldDuringGesture) return@awaitEachGesture
                            val doubleTap = tracker.maxPointers == 1 && tracker.totalMove <= slop &&
                                previousTapAt != 0L && endedAt - previousTapAt in 0..DOUBLE_TAP_WINDOW_MS
                            when (
                                val end = GestureArbiter.finish(
                                    maxPointers = tracker.maxPointers,
                                    moved = tracker.totalMove > slop,
                                    scrolled = scrolled,
                                    dragging = dragging,
                                    threeFingerMoved = threeFingerMoved,
                                    heldMs = endedAt - startedAt,
                                    doubleTap = doubleTap,
                                    flingVertical = flingY,
                                    flingHorizontal = flingX,
                                )
                            ) {
                                is GestureEnd.Click -> {
                                    service.click(end.button)
                                    if (end.button == Buttons.LEFT) lastTapAt = endedAt
                                }
                                GestureEnd.DoubleClick -> service.click(Buttons.LEFT)
                                is GestureEnd.Fling -> service.fling(end.vertical, end.horizontal)
                                GestureEnd.EndDrag, GestureEnd.None -> Unit
                            }
                        } finally {
                            if (dragHeld) service.setHeldButtons(MouseButtonSource.TAP_DRAG, 0)
                        }
                    }
                },
        ) {
            // The pad is the object: one soft signal ring, plus the first-run hint
            // (which disappears for good once the pad has been used). On the soft-UI
            // skin the ring becomes the reference dial: a raised disc with a slim
            // inset collar and one mint index mark.
            ScrollEdge(service, Modifier.align(Alignment.CenterStart), leftScroll)
            ScrollEdge(service, Modifier.align(Alignment.CenterEnd), rightScroll)
            ScrollDot(leftScroll, Modifier.align(Alignment.TopStart)) { leftScroll = !leftScroll }
            ScrollDot(rightScroll, Modifier.align(Alignment.TopEnd)) { rightScroll = !rightScroll }
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArcadeDial(active = state is TransportState.Connected)
                if (showHint) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("轻点 = 左键 · 双指滑动 = 滚动", "Tap = left click · Two fingers = scroll"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Action receipt: 复制 / 粘贴 / 截图 say what they did, right where the thumb is.
            AnimatedVisibility(
                visible = receipt != null,
                enter = fadeIn(tween(110)) + slideInVertically(tween(150)) { it / 5 },
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 116.dp),
            ) {
                ReceiptChip(message = receipt ?: "")
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            HoldPad(
                label = tr("左键", "Left"),
                haptics = service.hapticsEnabled(),
                sensitivity = sensitivity,
                acceleration = acceleration,
                onMove = service::move,
                onDown = {
                    service.cancelFling()
                    service.setHeldButtons(MouseButtonSource.LEFT_PAD, Buttons.LEFT)
                },
                onUp = { service.setHeldButtons(MouseButtonSource.LEFT_PAD, 0) },
            )
            DragPill(active = dragLock) { onDragLock(!dragLock) }
            HoldPad(
                label = tr("右键", "Right"),
                haptics = service.hapticsEnabled(),
                sensitivity = sensitivity,
                acceleration = acceleration,
                onMove = service::move,
                onDown = {
                    service.cancelFling()
                    service.setHeldButtons(MouseButtonSource.RIGHT_PAD, Buttons.RIGHT)
                },
                onUp = { service.setHeldButtons(MouseButtonSource.RIGHT_PAD, 0) },
            )
        }
    }
}

/**
 * The pad's centre mark. Classic skins: a thin signal ring. Soft-UI: the dial
 * from the reference surface — an extruded disc, a recessed collar, and one
 * index mark that lights up when a host is attached.
 */
@Composable
private fun ArcadeDial(active: Boolean) {
    val neu = LocalNeumorph.current
    val surface = MaterialTheme.colorScheme.surface
    val signal = MaterialTheme.colorScheme.secondary
    val lit by animateColorAsState(
        if (active) signal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(260),
        label = "dialLit",
    )
    if (!neu) {
        Box(
            Modifier
                .size(118.dp)
                .clip(CircleShape)
                .border(1.dp, signal.copy(alpha = 0.35f), CircleShape),
        )
        return
    }
    // The reference dial, rebuilt: extruded platter, tick ring, recessed face
    // with a four-way mark, and one index dot at twelve o'clock.
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
    val glyphColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    Box(
        Modifier
            .size(152.dp)
            .neuDial(surface, offset = 4.dp, blur = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rOut = size.minDimension / 2f
            val rIn = rOut * 0.80f
            val rEnd = rOut * 0.90f
            for (i in 0 until 60) {
                val a = (i * 6.0 - 90.0) * PI / 180.0
                val cosA = cos(a).toFloat()
                val sinA = sin(a).toFloat()
                drawLine(
                    color = tickColor,
                    start = Offset(cx + cosA * rIn, cy + sinA * rIn),
                    end = Offset(cx + cosA * rEnd, cy + sinA * rEnd),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Box(Modifier.size(106.dp).neuInset(53.dp, surface, offset = 3.dp, blur = 6.dp))
        Canvas(Modifier.size(26.dp)) {
            val c = size.minDimension / 2f
            val sw = size.minDimension * 0.06f
            val inner = size.minDimension * 0.13f
            val outer = size.minDimension * 0.33f
            listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
                drawLine(
                    color = glyphColor,
                    start = Offset(c + dx * inner, c + dy * inner),
                    end = Offset(c + dx * outer, c + dy * outer),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(signal, radius = size.minDimension * 0.078f, center = Offset(c, c))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(lit),
        )
    }
}

@Composable
private fun ScrollEdge(service: TouchpadInput, modifier: Modifier, visible: Boolean) {
    if (!visible) return
    Box(modifier.fillMaxHeight().width(24.dp).padding(vertical = 54.dp)
        .pointerInput(service) {
            val scroll = com.pocketdesk.app.input.ScrollAccumulator(42.dp.toPx())
            detectVerticalDragGestures(
                onDragStart = { scroll.reset(); service.cancelFling(); service.centerAim() },
                onDragEnd = { scroll.reset() },
                onDragCancel = { scroll.reset() },
                onVerticalDrag = { change, drag ->
                    change.consume()
                    val notches = scroll.add(drag)
                    if (notches != 0) service.scroll(notches)
                },
            )
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().width(4.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)))
    }
}

@Composable
private fun ScrollDot(active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.secondary
    Canvas(modifier.padding(10.dp).size(16.dp).clip(CircleShape).clickable(onClick = onClick)) {
        drawCircle(tint.copy(alpha = if (active) 0.9f else 0.28f), size.minDimension * 0.34f, style = Stroke(1.4.dp.toPx()))
        if (active) drawCircle(tint, size.minDimension * 0.12f)
    }
}

/** Press-and-hold mouse button, sized for a thumb: hold keeps the host button down. */
@Composable
private fun HoldPad(
    label: String,
    haptics: Boolean = true,
    sensitivity: Float,
    acceleration: Boolean,
    onMove: (Int, Int) -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    val view = LocalView.current
    val neu = LocalNeumorph.current
    // pointerInput(Unit) keeps the first block it was given, so the callbacks have to
    // be read through state: with a captured lambda the pad kept releasing the button
    // with the drag-lock value from first composition (turning the lock on did nothing
    // once the thumb pad had been used).
    val currentDown by rememberUpdatedState(onDown)
    val currentUp by rememberUpdatedState(onUp)
    val currentHaptics by rememberUpdatedState(haptics)
    val currentMove by rememberUpdatedState(onMove)
    val density = LocalDensity.current.density.coerceAtLeast(0.5f)
    val math = remember { TouchpadMath() }
    math.sensitivity = sensitivity
    math.acceleration = acceleration
    math.gainScale = TUNED_DENSITY / density
    var held by remember { mutableStateOf(false) }
    // Press feel: colour eases over 90 ms, the pad gives a 3% settle on the way down.
    // On soft-UI the pad keeps its colour and travels instead — a shallower scale,
    // and the raised shadows roll over into inner bands.
    val padColor by animateColorAsState(
        if (held) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(90),
        label = "pad",
    )
    val padScale by animateFloatAsState(
        if (held) (if (neu) 0.982f else 0.97f) else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
        label = "padScale",
    )
    val press by animateFloatAsState(if (held) 1f else 0f, tween(90), label = "padPress")
    val onContent = when {
        neu -> MaterialTheme.colorScheme.onSurface
        held -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurface
    }
    val onSub = when {
        neu -> MaterialTheme.colorScheme.onSurfaceVariant
        held -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val markColor = when {
        neu -> MaterialTheme.colorScheme.secondary
        held -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.secondary
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .width(88.dp)
            .height(78.dp)
            .graphicsLayer {
                scaleX = padScale
                scaleY = padScale
            }
            .then(
                if (neu) {
                    Modifier.neuPressed(
                        press = press,
                        corner = 22.dp,
                        surface = MaterialTheme.colorScheme.surface,
                        sunk = NeuSunk,
                        offset = 4.dp,
                        blur = 9.dp,
                    )
                } else {
                    Modifier
                        .shadow(3.dp, shape)
                        .clip(shape)
                        .background(padColor)
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    held = true
                    if (currentHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    math.reset()
                    var previousAt = down.uptimeMillis
                    currentDown()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                ?: break
                            change.consume()
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            val dtMs = (change.uptimeMillis - previousAt).toFloat().coerceAtLeast(1f)
                            previousAt = change.uptimeMillis
                            val (dx, dy) = math.pointer(delta.x, delta.y, dtMs)
                            if (dx != 0 || dy != 0) currentMove(dx, dy)
                        }
                    } finally {
                        held = false
                        currentUp()
                    }
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(markColor),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = onContent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr("按住不放", "Hold"),
                style = TextStyle(fontSize = 10.sp),
                color = onSub,
            )
        }
    }
}

/** Drag lock: tap to keep the left button down, tap again to release. */
@Composable
private fun DragPill(active: Boolean, onClick: () -> Unit) {
    val neu = LocalNeumorph.current
    val pillColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        animationSpec = tween(120),
        label = "dragpill",
    )
    val press by animateFloatAsState(if (active) 1f else 0f, tween(120), label = "dragPress")
    val label = if (active) tr("拖动 · 开", "Drag · On") else tr("拖动 · 关", "Drag · Off")
    val textColor = when {
        neu -> if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
        active -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .then(
                if (neu) {
                    Modifier
                        .neuPressed(
                            press = press,
                            corner = 999.dp,
                            surface = MaterialTheme.colorScheme.surface,
                            sunk = NeuSunk,
                            offset = 3.dp,
                            blur = 7.dp,
                        )
                        .clip(CircleShape)
                } else {
                    Modifier.clip(CircleShape).background(pillColor)
                },
            )
            .clickable { onClick() },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        )
    }
}

/** Small receipt chip: what just happened (已复制 / 已粘贴 / 截图…). */
@Composable
private fun ReceiptChip(message: String, modifier: Modifier = Modifier) {
    val neu = LocalNeumorph.current
    val body: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(7.dp))
            Text(message, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (neu) {
        Box(
            modifier.neuRaised(
                corner = 999.dp,
                surface = MaterialTheme.colorScheme.surface,
                offset = 3.dp,
                blur = 8.dp,
            ),
        ) { body() }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            shadowElevation = 4.dp,
            modifier = modifier,
        ) { body() }
    }
}

/**
 * The floating action island between pad and keyboard: 复制 / 粘贴 / 截图 / 扩展.
 * Compact and centred on purpose — a full-width bar reads as another toolbar row,
 * while a small pill with a shadow reads as one object hovering over the screen.
 */
@Composable
internal fun ActionIsland(
    service: HidService,
    hostIsMac: Boolean,
    onFlash: (String) -> Unit,
    toolsVisible: Boolean = false,
    onTools: () -> Unit = {},
) {
    val copied = tr("已复制", "Copied")
    val pasted = tr("已粘贴", "Pasted")
    val screenshot = tr("截图：在画面上拖一圈框选", "Screenshot: drag to select an area")
    ActionIsland(
        toolsVisible = toolsVisible,
        onTools = onTools,
        onCopy = { service.shortcut(HidKeymap.forChar('c')!!.first, hostIsMac); onFlash(copied) },
        onPaste = { service.shortcut(HidKeymap.forChar('v')!!.first, hostIsMac); onFlash(pasted) },
        onScreenshot = { service.screenshot(hostIsMac); onFlash(screenshot) },
    )
}

@Composable
internal fun ActionIsland(
    toolsVisible: Boolean,
    onTools: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onScreenshot: () -> Unit,
) {
    val neu = LocalNeumorph.current
    val shape = RoundedCornerShape(18.dp)
    val buttons: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IslandButton(IslandIcon.COPY, tr("复制", "Copy"), Modifier.weight(1f), onCopy)
            IslandButton(IslandIcon.PASTE, tr("粘贴", "Paste"), Modifier.weight(1f), onPaste)
            IslandButton(IslandIcon.CROP, tr("截图", "Capture"), Modifier.weight(1f), onScreenshot)
            IslandButton(IslandIcon.KEYS, if (toolsVisible) tr("收起", "Close") else tr("扩展", "Extra"), Modifier.weight(1f), onTools)
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (neu) {
            // Soft-UI: no border at all — the island is read purely from its
            // extrusion, one level above the sheet and two above the pad.
            Box(
                Modifier.neuRaised(
                    corner = 18.dp,
                    surface = MaterialTheme.colorScheme.surface,
                    offset = 4.5.dp,
                    blur = 10.dp,
                ),
            ) { buttons() }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f)),
                shadowElevation = 10.dp,
            ) { buttons() }
        }
    }
}

@Composable
private fun IslandButton(icon: IslandIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    val neu = LocalNeumorph.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(70), label = "islandPress")
    val shape = RoundedCornerShape(12.dp)
    val body = if (neu) {
        // A quiet key on the island: barely raised, and it sinks under the thumb.
        Modifier
            .neuPressed(
                press = press,
                corner = 12.dp,
                surface = MaterialTheme.colorScheme.surface,
                sunk = NeuSunk,
                offset = 2.dp,
                blur = 5.dp,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    } else {
        Modifier.clip(shape).clickable { onClick() }
    }
    Box(modifier.then(body).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (LocalDensity.current.fontScale <= 1.15f) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                IslandIcon(icon, Modifier.size(12.dp))
            }
            Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private enum class IslandIcon { COPY, PASTE, CROP, KEYS }

/**
 * The island glyphs are drawn, not typeset: a font that lacks them would show
 * tofu boxes on some devices, and these shapes stay legible at small sizes.
 */
@Composable
private fun IslandIcon(kind: IslandIcon, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * 0.11f
        val r = CornerRadius(s * 0.14f)
        when (kind) {
            IslandIcon.COPY -> {
                // two sheets, the front one offset down-right
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.18f, s * 0.12f),
                    size = Size(s * 0.54f, s * 0.54f),
                    cornerRadius = r,
                    style = Stroke(sw),
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.32f, s * 0.30f),
                    size = Size(s * 0.54f, s * 0.54f),
                    cornerRadius = r,
                    style = Stroke(sw),
                )
            }
            IslandIcon.PASTE -> {
                // clipboard: board plus the clip on top
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.18f, s * 0.24f),
                    size = Size(s * 0.64f, s * 0.64f),
                    cornerRadius = r,
                    style = Stroke(sw),
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.36f, s * 0.06f),
                    size = Size(s * 0.28f, s * 0.24f),
                    cornerRadius = CornerRadius(s * 0.09f),
                    style = Stroke(sw),
                )
            }
            IslandIcon.KEYS -> {
                drawRoundRect(tint, Offset(s * 0.08f, s * 0.20f), Size(s * 0.84f, s * 0.60f), r, style = Stroke(sw))
                for (row in 0..1) for (col in 0..3) {
                    drawCircle(tint, s * 0.04f, Offset(s * (0.24f + col * 0.17f), s * (0.37f + row * 0.20f)))
                }
            }
            IslandIcon.CROP -> {
                // four corner brackets, like the crop frame of a screenshot tool
                val m = s * 0.14f
                val arm = s * 0.30f
                val e = s - m
                val cap = StrokeCap.Round
                drawLine(tint, Offset(m, m + arm), Offset(m, m), sw, cap)
                drawLine(tint, Offset(m, m), Offset(m + arm, m), sw, cap)
                drawLine(tint, Offset(e - arm, m), Offset(e, m), sw, cap)
                drawLine(tint, Offset(e, m), Offset(e, m + arm), sw, cap)
                drawLine(tint, Offset(m, e - arm), Offset(m, e), sw, cap)
                drawLine(tint, Offset(m, e), Offset(m + arm, e), sw, cap)
                drawLine(tint, Offset(e - arm, e), Offset(e, e), sw, cap)
                drawLine(tint, Offset(e, e - arm), Offset(e, e), sw, cap)
            }
        }
    }
}
