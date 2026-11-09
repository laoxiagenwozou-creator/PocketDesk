package com.pocketdesk.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.pocketdesk.app.MainActivity
import com.pocketdesk.app.R
import com.pocketdesk.app.core.AppTheme
import com.pocketdesk.app.core.InputEvent
import com.pocketdesk.app.core.LogBus
import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.input.MouseButtonState
import com.pocketdesk.app.input.MouseButtonSource
import com.pocketdesk.app.input.TouchpadInput
import com.pocketdesk.app.input.KeyboardInput
import com.pocketdesk.app.input.AimPointer
import com.pocketdesk.app.input.ContinuousScrollState
import com.pocketdesk.app.transport.hid.BluetoothHidTransport
import com.pocketdesk.app.transport.hid.HidKeymap
import com.pocketdesk.app.transport.hid.HidProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Foreground owner of the input transport.
 *
 * The activity binds for UI; the HID profile and its Bluetooth connection outlive
 * the activity so a screen-off / app-switch does not drop the pointer link.
 */
class HidService : Service(), TouchpadInput, KeyboardInput {

    companion object {
        const val ACTION_DISCONNECT = "com.pocketdesk.app.action.DISCONNECT"
        private const val CHANNEL_ID = "pocketdesk_hid"
        private const val NOTIFICATION_ID = 0x9D0
        private const val PREFS = "pocketdesk"
        const val KEY_PROFILE = "hid_profile"
        const val KEY_LAST_DEVICE = "last_device"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_DIRECT_SEND = "direct_send"
        const val KEY_HOST_IS_MAC = "host_is_mac"
        const val KEY_HAPTICS = "keyboard_haptics"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ACCEL = "pointer_accel"
        const val KEY_SHAKE_FIND = "shake_find"
        const val KEY_AIM = "aim_pointer"
        const val KEY_AIM_SENSITIVITY = "aim_sensitivity"
        const val KEY_SCROLL_SIDE = "scroll_side"
        const val KEY_ENGLISH = "english_ui"
        const val KEY_HINT_SEEN = "hint_seen"
        const val KEY_THEME = "app_theme"

        /** Deviation from 1 g that counts as a deliberate shake. */
        const val SHAKE_THRESHOLD = 20f

        /** One circle per gesture, however long the shaking goes on. */
        const val SHAKE_COOLDOWN_MS = 1200L
    }

    inner class LocalBinder : Binder() {
        fun service(): HidService = this@HidService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var transport: BluetoothHidTransport
        private set

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        LogBus.init(File(filesDir, "logs"))
        transport = BluetoothHidTransport(this) { LogBus.append(it) }
        transport.setProfile(HidProfiles.byId(prefs.getString(KEY_PROFILE, null)))
        applySendMode()
        createChannel()
        startInForeground(TransportState.Registering)
        // Open-and-go: set the remembered host *before* start(), because registration
        // can complete on the HID thread before this line runs, and onAppStatusChanged
        // would then find no target and sit at Ready.
        _aimEnabled.value = prefs.getBoolean(KEY_AIM, false)
        _english.value = prefs.getBoolean(KEY_ENGLISH, false)
        transport.autoConnectAddress = lastDeviceAddress()
        transport.start()
        registerScreenReceiver()

        scope.launch {
            transport.state.collectLatest { state ->
                notifyState(state)
                // Listening to the accelerometer is only worth it with a live link and
                // a lit screen; the receiver keeps the two in step.
                updateShakeSensor()
                if (state is TransportState.Connected) {
                    prefs.edit().putString(KEY_LAST_DEVICE, state.address).apply()
                } else {
                    releasePointerInput()
                }
            }
        }
        LogBus.append("HidService 启动，profile=${transport.profile().id}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) transport.disconnect()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        releasePointerInput()
        scope.cancel()
        runCatching { sensorManager?.unregisterListener(shakeListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { transport.stop() }
        super.onDestroy()
    }

    // ────────────────────────────── UI-facing API ──────────────────────────────

    fun profileId(): String = transport.profile().id

    fun setProfileId(id: String) {
        releasePointerInput()
        prefs.edit().putString(KEY_PROFILE, id).apply()
        transport.setProfile(HidProfiles.byId(id))
    }

    fun lastDeviceAddress(): String? = prefs.getString(KEY_LAST_DEVICE, null)

    /** Pointer speed multiplier, persisted so a tuning session survives a restart. */
    fun sensitivity(): Float = prefs.getFloat(KEY_SENSITIVITY, 1.8f)

    fun setSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()
    }

    fun sendMode(): BluetoothHidTransport.SendMode =
        runCatching {
            BluetoothHidTransport.SendMode.valueOf(
                prefs.getString(KEY_DIRECT_SEND, null) ?: BluetoothHidTransport.SendMode.FIXED_CADENCE.name
            )
        }.getOrDefault(BluetoothHidTransport.SendMode.FIXED_CADENCE)

    fun setSendMode(mode: BluetoothHidTransport.SendMode) {
        prefs.edit().putString(KEY_DIRECT_SEND, mode.name).apply()
        applySendMode()
    }

    /** Which primary modifier the ⌘ key sends: Cmd on macOS, Ctrl on Windows. */
    fun hostIsMac(): Boolean = prefs.getBoolean(KEY_HOST_IS_MAC, false)

    fun setHostIsMac(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOST_IS_MAC, enabled).apply()
    }

    /** Keyboard key tick; uses the system's own keyboard-tap haptic so it follows system settings. */
    override fun hapticsEnabled(): Boolean = prefs.getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    /** Appearance skin: night (default) plus the three day palettes. */
    fun theme(): AppTheme {
        val saved = prefs.getString(KEY_THEME, null)
        if (saved != null) return AppTheme.byId(saved)
        // Migrate the old night/day boolean.
        val wasDay = prefs.contains(KEY_DARK_THEME) && !prefs.getBoolean(KEY_DARK_THEME, true)
        return if (wasDay) AppTheme.SLATE else AppTheme.NIGHT
    }

    fun setTheme(theme: AppTheme) {
        releasePointerInput()
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    /** Laptop-style pointer acceleration: slow = precise, fast flick = far. */
    fun pointerAccel(): Boolean = prefs.getBoolean(KEY_ACCEL, true)

    fun setPointerAccel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACCEL, enabled).apply()
    }

    /** Shake the phone to make the pointer draw a circle (find it on a busy remote screen). */
    fun shakeFindEnabled(): Boolean = prefs.getBoolean(KEY_SHAKE_FIND, true)

    private val _aimEnabled = MutableStateFlow(false)
    val aimMode = _aimEnabled.asStateFlow()
    @Volatile private var aimForeground = false
    fun setAimForeground(foreground: Boolean) {
        aimForeground = foreground
        aimPointer.center()
    }
    private val _english = MutableStateFlow(false)
    val englishUi = _english.asStateFlow()
    private val aimPointer = AimPointer()
    private var gravity = FloatArray(3)
    private var rotationRate = FloatArray(3)

    fun aimSensitivity(): Float = prefs.getFloat(KEY_AIM_SENSITIVITY, 3.2f)
    fun setAimSensitivity(value: Float) { prefs.edit().putFloat(KEY_AIM_SENSITIVITY, value.coerceIn(1f, 8f)).apply() }
    fun scrollOnRight(): Boolean = prefs.getBoolean(KEY_SCROLL_SIDE, false)
    fun setScrollOnRight(enabled: Boolean) { prefs.edit().putBoolean(KEY_SCROLL_SIDE, enabled).apply() }
    fun setEnglish(enabled: Boolean) {
        _english.value = enabled
        prefs.edit().putBoolean(KEY_ENGLISH, enabled).apply()
        notifyState(transport.state.value)
    }
    fun setAimEnabled(enabled: Boolean) {
        _aimEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AIM, enabled).apply()
        if (!enabled) aimPointer.center()
        updateShakeSensor()
    }
    override fun centerAim() = aimPointer.center()

    fun setShakeFindEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_FIND, enabled).apply()
        updateShakeSensor()
    }

    /** The on-pad usage hint shows until the pad has been used once. */
    override fun hintSeen(): Boolean = prefs.getBoolean(KEY_HINT_SEEN, false)

    override fun setHintSeen() {
        prefs.edit().putBoolean(KEY_HINT_SEEN, true).apply()
    }

    fun applySendMode() {
        transport.sendMode = sendMode()
    }

    fun bondedDevices(): List<BluetoothDevice> = transport.bondedDevices()

    fun deviceLabel(device: BluetoothDevice): String = transport.deviceName(device)

    fun connect(address: String?) {
        releasePointerInput()
        transport.connect(address)
    }

    fun disconnect() {
        releasePointerInput()
        transport.disconnect()
    }

    fun send(event: InputEvent) = transport.send(event)

    override fun move(dx: Int, dy: Int) {
        stopContinuousScroll()
        transport.send(InputEvent.PointerMove(dx, dy))
    }

    private val _dragLocked = MutableStateFlow(false)
    val dragLocked = _dragLocked.asStateFlow()

    fun setDragLocked(enabled: Boolean) {
        if (enabled && transport.state.value !is TransportState.Connected) return
        _dragLocked.value = enabled
        setHeldButtons(MouseButtonSource.DRAG_LOCK, if (enabled) com.pocketdesk.app.core.Buttons.LEFT else 0)
    }

    fun releasePointerInput() {
        cancelFling()
        circleJob?.cancel()
        val previous = buttonMask
        mouseButtons.clear()
        _dragLocked.value = false
        if (previous != 0) transport.send(InputEvent.PointerButtons(0))
    }

    private val mouseButtons = MouseButtonState()
    private val buttonMask: Int get() = mouseButtons.mask

    private fun updateButtons(owner: Any, mask: Int) {
        val previous = buttonMask
        val combined = mouseButtons.set(owner, mask)
        if (combined != previous) {
            LogBus.append("鼠标按键 → 0x%02X".format(combined))
            transport.send(InputEvent.PointerButtons(combined))
        }
    }

    override fun setHeldButtons(source: MouseButtonSource, mask: Int) {
        if (mask != 0) {
            circleJob?.cancel()
            stopContinuousScroll()
        }
        updateButtons(source, mask)
    }

    override fun hasHeldButtons(): Boolean = mouseButtons.heldMask != 0

    override fun click(mask: Int, holdMs: Long) {
        stopContinuousScroll()
        val clickMask = mask and buttonMask.inv()
        if (clickMask == 0) return
        val owner = Any()
        updateButtons(owner, clickMask)
        scope.launch {
            try {
                delay(holdMs)
            } finally {
                updateButtons(owner, 0)
            }
        }
    }

    override fun doubleClick(mask: Int) {
        click(mask, 32)
        scope.launch {
            delay(90)
            click(mask, 32)
        }
    }

    override fun cancelFling() {
        stopContinuousScroll()
        circleJob?.cancel()
        flingJob?.cancel()
        flingJob = null
    }

    /**
     * Trackpad inertia: keep scrolling after the fingers lift, decaying each frame.
     * [vertical]/[horizontal] are notches per 16 ms frame at the moment of release.
     *
     * A new fling replaces the running one: two flicks in a row used to stack, so the
     * screen kept moving after the fingers had stopped.
     */
    override fun fling(vertical: Float, horizontal: Float) {
        cancelFling()
        flingJob = scope.launch {
            var vy = vertical
            var vx = horizontal
            while (kotlin.math.abs(vy) > 0.2f || kotlin.math.abs(vx) > 0.2f) {
                delay(16)
                val ny = vy.toInt()
                val nx = vx.toInt()
                if (ny != 0 || nx != 0) scroll(ny, nx)
                vy = (vy - ny) * 0.92f
                vx = (vx - nx) * 0.92f
            }
        }
    }

    private var flingJob: Job? = null

    override fun scroll(vertical: Int, horizontal: Int) {
        stopContinuousScroll()
        transport.send(InputEvent.Scroll(vertical, horizontal))
    }

    private val continuousScroll = ContinuousScrollState()
    private var continuousJob: Job? = null
    private var continuousSession: Long? = null

    override fun setContinuousScroll(position: Float) {
        if (position == 0f || !position.isFinite() || hasHeldButtons() ||
            transport.state.value !is TransportState.Connected) {
            stopContinuousScroll()
            return
        }
        continuousScroll.setPosition(position)
        if (continuousScroll.rate == 0f) {
            stopContinuousScroll()
            return
        }
        if (continuousJob?.isActive == true) return
        circleJob?.cancel()
        flingJob?.cancel()
        flingJob = null
        val session = transport.beginContinuousScroll()
        continuousSession = session
        continuousJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (transport.state.value is TransportState.Connected && !hasHeldButtons()) {
                delay(16)
                val now = SystemClock.elapsedRealtime()
                val wheel = continuousScroll.advance(now - last)
                last = now
                if (wheel != 0) transport.send(InputEvent.WheelPulse(wheel, session))
            }
        }
    }

    private fun stopContinuousScroll() {
        continuousJob?.cancel()
        continuousJob = null
        continuousScroll.stop()
        continuousSession?.let { transport.stopContinuousScroll(it) }
        continuousSession = null
    }

    fun typeText(text: String): Boolean {
        if (transport.state.value !is TransportState.Connected) return false
        transport.send(InputEvent.Text(text))
        return true
    }

    override fun media(usage: Int) = transport.send(InputEvent.Consumer(usage))

    /** Press and release a key with an optional modifier mask. */
    override fun tapKey(usage: Int, modifiers: Int) {
        transport.send(InputEvent.KeyTap(usage, modifiers))
    }

    /** Ctrl/Cmd + key, resolved for the selected host OS. */
    fun shortcut(usage: Int, hostIsMac: Boolean, shift: Boolean = false) {
        var mods = Modifiers.primary(hostIsMac)
        if (shift) mods = mods or Modifiers.LEFT_SHIFT
        tapKey(usage, mods)
    }

    /**
     * Region screenshot, resolved for the host OS:
     * Windows Win+Shift+S (Snipping Tool), macOS Cmd+Shift+4 (selection to file).
     */
    fun screenshot(hostIsMac: Boolean) {
        val (usage, _) = if (hostIsMac) HidKeymap.forChar('4')!! else HidKeymap.forChar('s')!!
        tapKey(usage, Modifiers.LEFT_GUI or Modifiers.LEFT_SHIFT)
    }

    // ────────────────────────────── three-finger navigation ──────────────────────────────

    /** Three-finger horizontal swipe: previous / next virtual desktop or space. */
    override fun desktopSwitch(hostIsMac: Boolean, next: Boolean) {
        val arrow = if (next) HidKeymap.RIGHT_ARROW else HidKeymap.LEFT_ARROW
        if (hostIsMac) {
            tapKey(arrow, Modifiers.LEFT_CTRL)
        } else {
            tapKey(arrow, Modifiers.LEFT_CTRL or Modifiers.LEFT_GUI)
        }
    }

    /** Three-finger upward swipe: Mission Control (macOS) or Task View (Windows). */
    override fun overview(hostIsMac: Boolean) {
        if (hostIsMac) {
            tapKey(HidKeymap.UP_ARROW, Modifiers.LEFT_CTRL)
        } else {
            tapKey(HidKeymap.TAB, Modifiers.LEFT_GUI)
        }
    }

    // ────────────────────────────── shake to find the pointer ──────────────────────────────

    private var sensorManager: SensorManager? = null
    private var sensorRegistered = false

    /**
     * The accelerometer is only listened to when it can actually fire: switch on,
     * link up, screen on. Gating here instead of inside the callback keeps the sensor
     * (and its wake-ups) off for the hours the app spends in the background.
     */
    private fun updateShakeSensor() {
        val sm = sensorManager ?: run {
            val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            sensorManager = manager
            manager
        }
        val wanted = (prefs.getBoolean(KEY_SHAKE_FIND, true) || _aimEnabled.value) &&
            transport.state.value is TransportState.Connected &&
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
        if (wanted == sensorRegistered) return
        if (wanted) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
                LogBus.append("无加速度传感器，晃动找鼠标不可用")
                return
            }
            runCatching {
                sm.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_GAME)
                sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(aimListener, it, SensorManager.SENSOR_DELAY_GAME) }
            }.onFailure { LogBus.append("注册加速度传感器失败: ${it.message}") }
            sensorRegistered = true
        } else {
            runCatching { sm.unregisterListener(shakeListener); sm.unregisterListener(aimListener) }
            sensorRegistered = false
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) releasePointerInput()
            updateShakeSensor()
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }.onFailure { LogBus.append("注册亮熄屏广播失败: ${it.message}") }
    }

    private val aimListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) rotationRate = event.values.clone()
            if (!aimForeground || !_aimEnabled.value || transport.state.value !is TransportState.Connected || hasHeldButtons()) return
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power?.isInteractive != true) return
            aimPointer.sensitivity = prefs.getFloat(KEY_AIM_SENSITIVITY, 3.2f)
            val (dx, dy) = aimPointer.update(gravity[0], gravity[1], gravity[2], rotationRate[0], rotationRate[1], rotationRate[2], event.timestamp)
            if (dx != 0 || dy != 0) move(dx, dy)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var lastShakeAt = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            gravity = event.values.clone()
            if (_aimEnabled.value) return
            if (!prefs.getBoolean(KEY_SHAKE_FIND, true)) return
            if (transport.state.value !is TransportState.Connected || hasHeldButtons()) return
            // Only react while the screen is on and the phone is in hand, never in a pocket.
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power?.isInteractive != true) return
            val v = event.values
            val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (abs(magnitude - SensorManager.GRAVITY_EARTH) < SHAKE_THRESHOLD) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastShakeAt < SHAKE_COOLDOWN_MS) return
            lastShakeAt = now
            LogBus.append("检测到晃动 → 指针画圈")
            vibrateTick()
            drawPointerCircle()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun vibrateTick() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /**
     * The lost-pointer trick: draw a closed circle around the current position so the
     * eye snaps to it. The deltas are pre-rounded to integers and the last one is
     * corrected, so the pointer comes back exactly where it started.
     */
    private var circleJob: Job? = null

    private fun drawPointerCircle(radius: Float = 80f, steps: Int = 16) {
        if (hasHeldButtons()) return
        stopContinuousScroll()
        circleJob?.cancel()
        circleJob = scope.launch {
            val points = (0 until steps).map { i ->
                val angle = 2.0 * PI * i / steps
                (radius * cos(angle)).toFloat() to (radius * sin(angle)).toFloat()
            }
            val deltas = ArrayList<Pair<Int, Int>>(steps)
            var sumX = 0
            var sumY = 0
            for (i in 0 until steps) {
                val (ax, ay) = points[i]
                val (bx, by) = points[(i + 1) % steps]
                val dx = (bx - ax).roundToInt()
                val dy = (by - ay).roundToInt()
                deltas.add(dx to dy)
                sumX += dx
                sumY += dy
            }
            // Remove the rounding drift so the loop closes exactly.
            deltas[0] = (deltas[0].first - sumX) to (deltas[0].second - sumY)
            for ((dx, dy) in deltas) {
                if (dx != 0 || dy != 0) move(dx, dy)
                delay(28)
            }
        }
    }

    // ────────────────────────────── notification ──────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_description) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun startInForeground(state: TransportState) {
        val notification = buildNotification(state)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            LogBus.append("startForeground 失败: ${t.message}")
            stopSelf()
        }
    }

    private fun notifyState(state: TransportState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: TransportState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, HidService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val status = if (!_english.value) state.label else when (state) {
            TransportState.Idle -> "Not started"
            TransportState.Registering -> "Registering HID…"
            TransportState.Ready -> "Registered · Waiting for host"
            is TransportState.Connecting -> "Connecting · ${state.deviceName ?: "Unknown device"}"
            is TransportState.Connected -> "Connected · ${state.deviceName ?: state.address}"
            is TransportState.Error -> "Connection error · Open diagnostics"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, if (_english.value) "Disconnect" else getString(R.string.action_disconnect), disconnect)
            .build()
    }
}
