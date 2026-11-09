package com.pocketdesk.app.ui

import android.os.Build
import android.view.InputDevice
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketdesk.app.R
import com.pocketdesk.app.core.AppTheme
import com.pocketdesk.app.core.Buttons
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.ui.theme.LocalNeumorph
import com.pocketdesk.app.ui.theme.PocketDeskTheme
import com.pocketdesk.app.ui.theme.neuDots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun BrandMark(modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.onSurface
    val dot = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        drawCircle(ring, size.minDimension * 0.36f, style = Stroke(size.minDimension * 0.075f))
        drawCircle(dot, size.minDimension * 0.13f, Offset(size.width * 0.66f, size.height * 0.66f))
    }
}

/** Touch surface and keyboard share one screen; auxiliary input opens below the action island. */
@Composable
fun PocketDeskApp(
    service: HidService?,
    onOpenBluetoothSettings: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onShareLog: (String) -> Unit,
) {
    val english = service?.englishUi?.collectAsStateWithLifecycle()?.value ?: false
    CompositionLocalProvider(LocalEnglish provides english) {
        PocketDeskContent(service, onOpenBluetoothSettings, onMakeDiscoverable, onShareLog)
    }
}

@Composable
private fun PocketDeskContent(
    service: HidService?,
    onOpenBluetoothSettings: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onShareLog: (String) -> Unit,
) {
    // The service binds a moment after the first frame, so an override is kept until
    // the user picks a skin; the persisted value is read live once the service exists.
    var themeOverride by remember { mutableStateOf<AppTheme?>(null) }
    val theme = themeOverride ?: (service?.theme() ?: AppTheme.NIGHT)
    val themeView = LocalView.current
    // Keep the system bars and the window background in step with the chosen skin,
    // otherwise a light UI sits under a dark status bar. The bars snap with the
    // skin: an animated colour here would only be read once per recomposition and
    // freeze at the previous skin's colour (measured on-device: bars were always
    // one skin behind). The content crossfade already smooths the transition.
    SideEffect {
        val activity = themeView.context as? android.app.Activity
        activity?.window?.let { window ->
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, themeView)
            bars.isAppearanceLightStatusBars = theme.isLight
            bars.isAppearanceLightNavigationBars = theme.isLight
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = theme.barColor.toInt()
                window.navigationBarColor = theme.barColor.toInt()
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(theme.barColor.toInt()))
        }
    }
    // Skin switch = a short crossfade of the whole screen, not a snap.
    Crossfade(targetState = theme, animationSpec = tween(220), label = "skin") { activeTheme ->
        PocketDeskTheme(theme = activeTheme) {
            // Soft-UI: the sheet itself is part of the material — a faint dot grid so
            // the porcelain has something to sit on.
            val neu = LocalNeumorph.current
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (neu) Modifier.neuDots(Color(0x7AB9C1CE))
                        else Modifier
                    ),
            ) {
                if (service == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tr("正在启动输入服务…", "Starting input service…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val state by service.transport.state.collectAsStateWithLifecycle()
                    var showDiagnostics by remember { mutableStateOf(false) }
                    var showTutorial by remember { mutableStateOf(false) }
                    var showComposer by remember { mutableStateOf(false) }
                    val dragLock by service.dragLocked.collectAsStateWithLifecycle()
                    var sensitivity by remember { mutableFloatStateOf(service.sensitivity()) }
                    val aimEnabled by service.aimMode.collectAsStateWithLifecycle()
                    var aimSensitivity by remember { mutableFloatStateOf(service.aimSensitivity()) }
                    var hostIsMac by remember { mutableStateOf(service.hostIsMac()) }
                    var acceleration by remember { mutableStateOf(service.pointerAccel()) }

                    // Ask the input dispatcher not to batch touch events: the pointer then
                    // follows the panel's own sampling rate instead of one update per frame.
                    val view = LocalView.current
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                view.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER)
                            }
                        }
                    }

                    var menuOpen by remember { mutableStateOf(false) }
                    var hostsOpen by remember { mutableStateOf(false) }
                    // Pages crossfade into each other instead of snapping.
                    val page = when {
                        showComposer -> "composer"
                        showTutorial -> "tutorial"
                        showDiagnostics -> "diagnostics"
                        else -> "main"
                    }
                    BackHandler(enabled = menuOpen || hostsOpen || page != "main") {
                        when {
                            menuOpen -> menuOpen = false
                            hostsOpen -> hostsOpen = false
                            else -> {
                                showComposer = false
                                showTutorial = false
                                showDiagnostics = false
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Crossfade(targetState = page, animationSpec = tween(150), label = "page") { which ->
                            when (which) {
                                "composer" ->
                                    ComposerView(service = service, onClose = { showComposer = false })
                                "tutorial" ->
                                    TutorialView(onClose = { showTutorial = false })
                                "diagnostics" ->
                                    DiagnosticsView(
                                        service = service,
                                        sensitivity = sensitivity,
                                        onSensitivity = {
                                            sensitivity = it
                                            service.setSensitivity(it)
                                        },
                                        acceleration = acceleration,
                                        onAcceleration = {
                                            acceleration = it
                                            service.setPointerAccel(it)
                                        },
                                        onClose = { showDiagnostics = false },
                                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                                        onMakeDiscoverable = onMakeDiscoverable,
                                        onShareLog = onShareLog,
                                    )
                                else ->
                                    MainView(
                                        service = service,
                                        inputActive = page == "main" && !menuOpen && !hostsOpen,
                                        state = state,
                                        dragLock = dragLock,
                                        onDragLock = service::setDragLocked,
                                        sensitivity = sensitivity,
                                        onSensitivity = {
                                            sensitivity = it
                                            service.setSensitivity(it)
                                        },
                                        hostIsMac = hostIsMac,
                                        acceleration = acceleration,
                                        onMenu = { service.releasePointerInput(); menuOpen = true },
                                        onHosts = { service.releasePointerInput(); hostsOpen = true },
                                    )
                            }
                        }
                        AnimatedVisibility(
                            visible = hostsOpen,
                            enter = fadeIn(tween(140)),
                            exit = fadeOut(tween(110)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (theme.isLight) 0.22f else 0.42f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { hostsOpen = false },
                            )
                        }
                        AnimatedVisibility(
                            visible = hostsOpen,
                            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 8 },
                            exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 8 },
                            modifier = Modifier.align(Alignment.TopCenter),
                        ) {
                            HostPicker(
                                service = service,
                                state = state,
                                onPick = { address ->
                                    hostsOpen = false
                                    service.connect(address)
                                },
                                onDiscoverable = {
                                    hostsOpen = false
                                    onMakeDiscoverable()
                                },
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(top = 40.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)
                                    .fillMaxSize(),
                            )
                        }
                        AnimatedVisibility(
                            visible = menuOpen,
                            enter = fadeIn(tween(130)),
                            exit = fadeOut(tween(110)),
                        ) {
                            // Scrim: a tap anywhere outside closes the panel.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (theme.isLight) 0.22f else 0.42f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { menuOpen = false },
                            )
                        }
                        AnimatedVisibility(
                            visible = menuOpen,
                            enter = fadeIn(tween(140)) + slideInVertically(tween(170)) { -it / 12 },
                            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 12 },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            MenuPanel(
                                theme = theme,
                                onTheme = {
                                    themeOverride = it
                                    service.setTheme(it)
                                },
                                hostIsMac = hostIsMac,
                                onHostIsMac = {
                                    hostIsMac = it
                                    service.setHostIsMac(it)
                                },
                                sensitivity = sensitivity,
                                onSensitivity = {
                                    sensitivity = it
                                    service.setSensitivity(it)
                                },
                                aimEnabled = aimEnabled,
                                onAimEnabled = service::setAimEnabled,
                                aimSensitivity = aimSensitivity,
                                onAimSensitivity = { aimSensitivity = it; service.setAimSensitivity(it) },
                                english = LocalEnglish.current,
                                onEnglish = service::setEnglish,
                                onComposer = { menuOpen = false; showComposer = true },
                                onTutorial = { menuOpen = false; showTutorial = true },
                                onDiagnostics = { menuOpen = false; showDiagnostics = true },
                                modifier = Modifier
                                    // Drop below the status bar and the 32dp top strip, so the
                                    // whole top bar (and the menu button itself) stays visible.
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(top = 40.dp, end = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainView(
    service: HidService,
    inputActive: Boolean,
    state: TransportState,
    dragLock: Boolean,
    onDragLock: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivity: (Float) -> Unit,
    hostIsMac: Boolean,
    acceleration: Boolean,
    onMenu: () -> Unit,
    onHosts: () -> Unit,
) {
    var toolsVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = toolsVisible && inputActive) { toolsVisible = false }
    var receipt by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val flash: (String) -> Unit = { message ->
        scope.launch {
            receipt = message
            delay(1100)
            receipt = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // The app draws edge to edge; without this the top row (PocketDesk / 诊断)
            // disappears under the system status bar and the keyboard hits the nav bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        StatusStrip(state = state, onMenu = onMenu, onHosts = onHosts)
        // A long error ("电脑要先在蓝牙里删除这台手机，再重新配对") does not fit the
        // one-line status strip; without this it was only ever seen as "错误 · 连接超…".
        ErrorBanner(state)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(if (toolsVisible) 8f else 10f).fillMaxWidth()) {
            TouchpadPanel(
                service = service,
                state = state,
                dragLock = dragLock,
                onDragLock = onDragLock,
                sensitivity = sensitivity,
                hostIsMac = hostIsMac,
                acceleration = acceleration,
                receipt = receipt,
                scrollOnRight = service.scrollOnRight(),
            )
        }
        Spacer(Modifier.height(6.dp))
        ActionIsland(service = service, hostIsMac = hostIsMac, onFlash = flash,
            toolsVisible = toolsVisible, onTools = {
                service.releasePointerInput()
                toolsVisible = !toolsVisible
            })
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(if (toolsVisible) 7f else 5f).fillMaxWidth()) {
            CompositionLocalProvider(LocalInputActive provides inputActive) {
                if (toolsVisible) AuxiliaryPane(service, hostIsMac, state is TransportState.Connected && !dragLock)
                else KeyboardPane(service = service, hostIsMac = hostIsMac)
            }
        }
    }
}

/** The full text of the current error, tappable to dismiss. */
@Composable
private fun ErrorBanner(state: TransportState) {
    val error = state as? TransportState.Error ?: return
    var dismissed by remember(error.message) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !dismissed,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable { dismissed = true },
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    if (LocalEnglish.current) englishError(error.message) else error.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tr("点一下收起", "Tap to dismiss"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One thin line: state dot, mark, product name and the full host status, then the
 * menu button. Everything else (文字 / 教程 / 诊断 / skins / sensitivity) lives in
 * the menu, which leaves the whole strip short so the touchpad gets the height.
 */
@Composable
private fun StatusStrip(state: TransportState, onMenu: () -> Unit, onHosts: () -> Unit) {
    val menuDescription = tr("菜单", "Menu")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(32.dp),
    ) {
        val dotColor by animateColorAsState(
            when (state) {
                is TransportState.Connected -> Color(0xFF7FB89A)
                is TransportState.Error -> MaterialTheme.colorScheme.error
                TransportState.Ready, TransportState.Registering, is TransportState.Connecting ->
                    MaterialTheme.colorScheme.secondary
                TransportState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = tween(250),
            label = "dot",
        )
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dotColor))
        Spacer(Modifier.width(7.dp))
        BrandMark(Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            "PocketDesk",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            transportLabel(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onHosts() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        // Menu: three bars, drawn rather than typed so no font can substitute it.
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable { onMenu() }
                .semantics { contentDescription = menuDescription },
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}
