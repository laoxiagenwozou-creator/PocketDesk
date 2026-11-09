package com.pocketdesk.app.transport.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pocketdesk.app.core.InputEvent
import com.pocketdesk.app.core.InputTransport
import com.pocketdesk.app.core.LogBus
import com.pocketdesk.app.core.Modifiers
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.input.KeyboardState
import com.pocketdesk.app.input.HostSelection
import com.pocketdesk.app.input.KeyPulseTimeline
import com.pocketdesk.app.input.PointerAccumulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Phone-as-Bluetooth-HID-device transport.
 *
 * Everything here is deliberately independent of Compose and of the service: the
 * UI sees [InputEvent]s and [TransportState] only. Threading rule: one HID thread
 * (the executor handed to the framework) owns registration, reports and callbacks;
 * public methods may be called from any thread.
 */
@SuppressLint("MissingPermission")
class BluetoothHidTransport(
    context: Context,
    private val reportWriter: ((Int, ByteArray) -> Boolean)? = null,
    private val log: (String) -> Unit = { LogBus.append(it) },
) : InputTransport {

    companion object {
        /** Event-driven coalescing window; ~166 Hz ceiling. */
        const val COALESCE_MS = 6L

        /** Fixed-cadence tick; ~125 Hz, the rate a classic HID link carries comfortably. */
        const val CADENCE_MS = 8L

        /** Idle link keep-alive; short enough that the host never parks the link. */
        const val KEEP_ALIVE_MS = 60L

        /** Spaced auto-reconnect attempts after an unexpected link drop. */
        const val MAX_RECONNECTS = 3

        /**
         * Character pacing for [queueText]. The hold has to be long enough that the
         * host sees a real key event; the gap is what a fast typist's keyboard would
         * send, and keeps a long paste from taking seconds.
         */
        const val TEXT_HOLD_MS = 6L
        const val TEXT_STEP_MS = 12L
    }

    private val appContext: Context = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun newExecutor(): ScheduledThreadPoolExecutor =
        ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "pocketdesk-hid").apply {
                priority = Thread.NORM_PRIORITY + 2
                isDaemon = true
            }
        }.apply {
            removeOnCancelPolicy = true
            executeExistingDelayedTasksAfterShutdownPolicy = false
            continueExistingPeriodicTasksAfterShutdownPolicy = false
        }

    private var executor = newExecutor()

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var connectedDevice: BluetoothDevice? = null
    @Volatile private var registered = false
    @Volatile private var currentProfile: HidProfile = HidProfiles.A_LINKPAD
    private val selection = HostSelection()
    var autoConnectAddress: String?
        get() = selection.automaticAddress
        set(value) { selection.automaticAddress = value }
    private var reconnectAttempts = 0
    @Volatile private var inputGeneration = 0L
    private val continuousLock = Any()
    private val continuousGeneration = AtomicLong(0)

    /** Diagnostics counters surfaced in the app. */
    val reportsSent = AtomicLong(0)
    val reportsFailed = AtomicLong(0)
    @Volatile var lastError: String? = null
        private set

    /** Live throughput line shown in the UI and mirrored to logcat once a second. */
    private val _stats = MutableStateFlow("")
    val stats: StateFlow<String> = _stats.asStateFlow()

    /**
     * How pointer reports are paced:
     *  - [SendMode.COALESCED]     merge everything inside a 6 ms window (event driven)
     *  - [SendMode.DIRECT]        one report per touch event
     *  - [SendMode.FIXED_CADENCE] emit on a fixed 8 ms tick, so an uneven touch stream
     *    still leaves the phone as an even pointer stream (what a hardware mouse does)
     */
    enum class SendMode { COALESCED, DIRECT, FIXED_CADENCE }

    @Volatile var sendMode: SendMode = SendMode.COALESCED
        set(value) {
            field = value
            if (value == SendMode.FIXED_CADENCE && connectedDevice != null) startCadenceLoop() else stopCadenceLoop()
        }

    private var cadenceTask: ScheduledFuture<*>? = null
    private var cadenceGeneration = 0L

    private val movesReceived = AtomicInteger(0)
    private val clippedReports = AtomicInteger(0)
    private var lastMouseReportAt = 0L
    private var gapSum = 0L
    private var gapMax = 0L
    private var gapCount = 0
    private var lastSentSnapshot = 0L
    private var lastFailedSnapshot = 0L
    private var statsTask: ScheduledFuture<*>? = null

    private val pointer = PointerAccumulator()
    private val keyTimeline = KeyPulseTimeline()
    private val consumerTimeline = KeyPulseTimeline()
    private val buttons = AtomicInteger(0)
    @Volatile private var flushScheduled = false

    private val kbLock = Any()
    private val keyboard = KeyboardState()

    private var receiverRegistered = false
    private var keepAliveTask: java.util.concurrent.ScheduledFuture<*>? = null

    private val qos = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        /* tokenRate */ 800,
        /* tokenBucketSize */ 9,
        /* peakBandwidth */ 0,
        /* latency */ 11250,
        /* delayVariation */ 11250,
    )

    // ────────────────────────────── lifecycle ──────────────────────────────

    fun setProfile(profile: HidProfile) {
        if (profile.id == currentProfile.id) return
        // Apply synchronously so a start() racing this call cannot register the old descriptor.
        currentProfile = profile
        execute {
            log("切换 HID 描述符 → ${profile.label}（主机需重新配对才能生效）")
            if (registered) {
                runCatching { hidDevice?.unregisterApp() }
                    .onFailure { log("unregisterApp 失败: ${it.message}") }
                connectedDevice = null
                registered = false
                _state.value = TransportState.Registering
                registerApp()
            }
        }
    }

    fun profile(): HidProfile = currentProfile

    override fun start() {
        // shutdown() is permanent for a ScheduledThreadPoolExecutor, so a transport
        // that was stopped and then had Bluetooth switched back on would reject every
        // task it submitted. Rebuild the pool and its periodic work instead.
        if (executor.isShutdown) {
            executor = newExecutor()
            statsTask = null
            cadenceTask = null
            keepAliveTask = null
            receiverRegistered = false
            if (sendMode == SendMode.FIXED_CADENCE) startCadenceLoop()
        }
        val a = adapter ?: run {
            fail("本机没有蓝牙适配器")
            return
        }
        if (!hasConnectPermission()) {
            fail("缺少 BLUETOOTH_CONNECT 权限")
            return
        }
        if (!a.isEnabled) {
            fail("蓝牙未开启")
            return
        }
        registerBondReceiver()
        startStatsLogger()
        _state.value = TransportState.Registering
        val accepted = runCatching {
            a.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
        }.getOrElse {
            fail("获取 HID Device Profile 失败: ${it.message}")
            false
        }
        if (!accepted) {
            fail("系统拒绝提供 HID_DEVICE Profile（厂商可能裁剪了该 Profile）")
            return
        }
        executor.schedule({
            if (hidDevice == null) {
                fail("5s 内未取得 HID Device Profile —— 该机型可能不支持手机作为 HID Device")
            }
        }, 5, TimeUnit.SECONDS)
    }

    override fun stop() {
        stopCadenceLoop()
        execute {
            clearInputState(releaseToHost = true)
            stopKeepAlive()
            runCatching { hidDevice?.unregisterApp() }
            runCatching { hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
            hidDevice = null
            connectedDevice = null
            registered = false
            _state.value = TransportState.Idle
            log("HID 通道已停止")
        }
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(bondReceiver) }
            receiverRegistered = false
        }
        executor.shutdown()
    }

    override fun connect(address: String?) {
        val a = adapter ?: return
        val target = address?.let { runCatching { a.getRemoteDevice(it) }.getOrNull() }
        if (target == null) {
            log("connect: 没有目标设备地址")
            return
        }
        val ticket = selection.request(target.address)
        execute {
            if (!selection.isCurrent(ticket, target.address)) return@execute
            val current = connectedDevice
            if (current?.address == target.address) return@execute
            clearInputState(releaseToHost = true)
            if (current != null) {
                runCatching { hidDevice?.disconnect(current) }
                connectedDevice = null
                stopKeepAlive()
                executor.schedule({
                    if (selection.isCurrent(ticket, target.address)) connectInternal(target)
                }, 400, TimeUnit.MILLISECONDS)
            } else {
                connectInternal(target)
            }
        }
    }

    override fun disconnect() {
        selection.disconnect()
        execute {
            clearInputState(releaseToHost = true)
            val device = connectedDevice
            if (device != null) {
                runCatching { hidDevice?.disconnect(device) }
                    .onFailure { log("disconnect 失败: ${it.message}") }
            }
            connectedDevice = null
            stopKeepAlive()
            _state.value = if (registered) TransportState.Ready else TransportState.Idle
        }
    }

    fun bondedDevices(): List<BluetoothDevice> =
        runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrElse { emptyList() }

    fun deviceName(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull() ?: device.address

    fun adapterName(): String = runCatching { adapter?.name }.getOrNull() ?: "未知"

    fun adapterAddress(): String = runCatching { adapter?.address }.getOrNull() ?: "不可见"

    fun isBluetoothEnabled(): Boolean = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    // ────────────────────────────── registration ──────────────────────────────

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as? BluetoothHidDevice ?: return
            execute {
                hidDevice = hid
                log("取得 HID Device Profile：${proxy.javaClass.simpleName}")
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            execute {
                log("HID Device Profile 断开")
                clearInputState()
                stopKeepAlive()
                hidDevice = null
                connectedDevice = null
                registered = false
                _state.value = TransportState.Error("系统回收了 HID Profile")
            }
        }
    }

    private fun registerApp() {
        val hid = hidDevice ?: return
        val profile = currentProfile
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "PocketDesk",
            "Wireless touchpad and keyboard",
            "PocketDesk",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            profile.descriptor,
        )
        val accepted = runCatching {
            hid.registerApp(sdp, null, qos, executor, hidCallback)
        }.getOrElse {
            fail("registerApp 抛异常: ${it.message}")
            return
        }
        _state.value = TransportState.Registering
        log("registerApp(${profile.id}) → $accepted，等待系统确认…")
        if (!accepted) {
            fail("registerApp 返回 false（HID 角色可能已被其他应用占用）")
            return
        }
        executor.schedule({
            if (!registered) fail("8s 内未收到注册成功回调（系统可能静默拒绝 HID 角色）")
        }, 8, TimeUnit.SECONDS)
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidTransport.registered = registered
            log("onAppStatusChanged(registered=$registered, plugged=${pluggedDevice?.address ?: "null"})")
            if (registered) {
                _state.value = TransportState.Ready
                val target = selection.target(pluggedDevice?.address)
                    ?.let { runCatching { adapter?.getRemoteDevice(it) }.getOrNull() }
                if (target != null) connectInternal(target)
            } else {
                connectedDevice = null
                clearInputState()
                stopKeepAlive()
                _state.value = TransportState.Error("HID 注册被系统撤销")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            device ?: return
            if (state != BluetoothProfile.STATE_DISCONNECTED && !selection.accepts(device.address)) {
                if (state == BluetoothProfile.STATE_CONNECTED) runCatching { hidDevice?.disconnect(device) }
                log("忽略过期的连接回调: ${device.address}")
                return
            }
            if (state == BluetoothProfile.STATE_DISCONNECTED) {
                val current = connectedDevice?.address
                val desired = selection.target()
                if ((current != null && current != device.address) ||
                    (current == null && desired != null && desired != device.address)) return
            }
            when (state) {
                BluetoothProfile.STATE_CONNECTING ->
                    _state.value = TransportState.Connecting(deviceName(device))

                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    reconnectAttempts = 0
                    selection.connected(device.address)
                    _state.value = TransportState.Connected(deviceName(device), device.address)
                    if (sendMode == SendMode.FIXED_CADENCE) startCadenceLoop()
                    startKeepAlive()
                    log("主机已连接: ${deviceName(device)} (${device.address})")
                }

                BluetoothProfile.STATE_DISCONNECTING ->
                    log("主机断开中…")

                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    // The host forgets its buttons and modifiers when a HID device
                    // disappears; holding them here brought back a stuck key after
                    // a reconnect.
                    clearInputState()
                    stopKeepAlive()
                    _state.value = if (registered) TransportState.Ready else TransportState.Idle
                    log("主机已断开")
                    scheduleReconnect(device)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val profile = currentProfile
            val size = when (id.toInt()) {
                profile.keyboardReportId -> profile.keyboardReportSize
                profile.mouseReportId -> profile.mouseReportSize
                profile.consumerReportId -> profile.consumerReportSize
                else -> {
                    runCatching {
                        hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                    }
                    return
                }
            }
            runCatching { hidDevice?.replyReport(device, type, id, ByteArray(size)) }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // Keyboard LED state arrives here; ack it or Windows hosts stall.
            runCatching { hidDevice?.replyReport(device, type, id, data ?: ByteArray(0)) }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            connectedDevice = null
            clearInputState()
            stopKeepAlive()
            _state.value = if (registered) TransportState.Ready else TransportState.Idle
            log("onVirtualCableUnplug: ${device?.address ?: "null"}")
        }
    }

    /**
     * A link that dropped on its own (host slept, walked out of range) comes back by
     * itself; a disconnect the user asked for stays disconnected. A few spaced
     * attempts, then give up — the manual 连接 button still works.
     */
    private fun scheduleReconnect(device: BluetoothDevice) {
        if (!selection.accepts(device.address)) return
        if (device.address != selection.lastConnectedAddress) return
        if (reconnectAttempts >= MAX_RECONNECTS) return
        reconnectAttempts++
        val ticket = selection.generation
        val delayS = 2L * reconnectAttempts
        log("链路自行断开，${delayS}s 后自动重连（第 $reconnectAttempts 次）")
        executor.schedule({
            if (selection.isCurrent(ticket, device.address) && registered && connectedDevice == null) {
                connectInternal(device)
            }
        }, delayS, TimeUnit.SECONDS)
    }

    private fun connectInternal(device: BluetoothDevice) {
        if (!selection.accepts(device.address) || connectedDevice?.address == device.address) return
        val ticket = selection.generation
        val hid = hidDevice
        if (hid == null) {
            log("connect: HID Profile 尚未就绪")
            return
        }
        if (!registered) {
            log("connect: HID 尚未注册完成")
            return
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            log("${deviceName(device)} 尚未配对，发起配对请求")
            _state.value = TransportState.Connecting(deviceName(device))
            runCatching { device.createBond() }
                .onFailure { fail("createBond 失败: ${it.message}") }
            return
        }
        _state.value = TransportState.Connecting(deviceName(device))
        val ok = runCatching { hid.connect(device) }.getOrElse {
            fail("HID connect 异常: ${it.message}")
            return
        }
        log("HID connect(${device.address}) → $ok")
        if (!ok) fail("HID connect 返回 false")
        executor.schedule({
            val s = _state.value
            if (selection.isCurrent(ticket, device.address) && s is TransportState.Connecting && connectedDevice == null) {
                fail("连接超时：对方没有把手机当成键鼠。电脑要先在蓝牙里删除这台手机，再重新配对。")
            }
        }, 12, TimeUnit.SECONDS)
    }

    // ────────────────────────────── input ──────────────────────────────

    private fun execute(action: () -> Unit) {
        try {
            executor.execute(action)
        } catch (_: RejectedExecutionException) {
            log("HID 通道已停止，忽略待发送操作")
        }
    }

    fun beginContinuousScroll(): Long = synchronized(continuousLock) {
        continuousGeneration.incrementAndGet()
    }

    fun stopContinuousScroll(session: Long) = synchronized(continuousLock) {
        continuousGeneration.compareAndSet(session, session + 1)
        Unit
    }

    override fun send(event: InputEvent) {
        if (connectedDevice == null || executor.isShutdown) return
        val generation = inputGeneration
        execute {
            if (generation != inputGeneration || connectedDevice == null) return@execute
            when (event) {
                is InputEvent.PointerMove -> {
                    pointer.move(event.dx, event.dy)
                    movesReceived.incrementAndGet()
                    requestFlush()
                }
                is InputEvent.PointerButtons -> {
                    flushNow()
                    buttons.set(event.mask)
                    flushNow()
                }
                is InputEvent.Scroll -> {
                    pointer.scroll(event.vertical, event.horizontal)
                    requestFlush()
                }
                is InputEvent.WheelPulse -> synchronized(continuousLock) {
                    if (event.session != continuousGeneration.get() ||
                        System.nanoTime() - event.createdAtNanos > TimeUnit.MILLISECONDS.toNanos(50)) return@synchronized
                    val hid = hidDevice
                    if (hid == null && reportWriter == null) return@synchronized
                    val device = connectedDevice ?: return@synchronized
                    val profile = currentProfile
                    sendReport(hid, device, profile.mouseReportId,
                        HidEncoder.mouseReport(profile, buttons.get(), 0, 0, event.vertical.coerceIn(-3, 3), 0))
                }
                is InputEvent.KeyChord -> applyKey(event.usage, event.modifiers, event.down)
                is InputEvent.KeyTap -> queueKey(event.usage, event.modifiers, event.holdMs, 6)
                is InputEvent.Text -> queueText(event.text)
                is InputEvent.Consumer -> {
                    val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                    val pulse = consumerTimeline.reserve(now, 40, 6)
                    fun report(usage: Int) {
                        if (generation != inputGeneration) return
                        val hid = hidDevice
                        if (hid == null && reportWriter == null) return
                        val device = connectedDevice ?: return
                        val profile = currentProfile
                        sendReport(hid, device, profile.consumerReportId, HidEncoder.consumerReport(profile, usage))
                    }
                    executor.schedule({ report(event.usage) }, (pulse.downAtMs - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    executor.schedule({ report(0) }, pulse.upAtMs - now, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private fun requestFlush() {
        when (sendMode) {
            SendMode.DIRECT -> flushNow()
            SendMode.COALESCED -> scheduleFlush()
            SendMode.FIXED_CADENCE -> Unit
        }
    }

    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        executor.schedule({
            flushScheduled = false
            flushNow()
        }, COALESCE_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun startCadenceLoop() {
        if (connectedDevice == null || cadenceTask?.isDone == false) return
        val cadence = com.pocketdesk.app.input.ReportCadence(
            TimeUnit.MILLISECONDS.toNanos(CADENCE_MS),
        )
        val generation = ++cadenceGeneration
        val tick = object : Runnable {
            override fun run() {
                synchronized(this@BluetoothHidTransport) {
                    if (generation != cadenceGeneration || executor.isShutdown) return
                    if (sendMode == SendMode.FIXED_CADENCE && pointer.hasPending) flushNow()
                    cadenceTask = executor.schedule(
                        this, cadence.nextDelay(System.nanoTime()), TimeUnit.NANOSECONDS,
                    )
                }
            }
        }
        cadenceTask = executor.schedule(
            tick, cadence.reset(System.nanoTime()), TimeUnit.NANOSECONDS,
        )
    }

    @Synchronized
    private fun stopCadenceLoop() {
        cadenceGeneration++
        cadenceTask?.cancel(false)
        cadenceTask = null
    }

    private fun flushNow() {
        val profile = currentProfile
        val hid = hidDevice
        if (hid == null && reportWriter == null) return
        val device = connectedDevice ?: return
        val limit = if (profile.widePointer) 32767 else 127
        val delta = pointer.take(limit, profile.hasHorizontalScroll)
        if (pointer.hasPending) clippedReports.incrementAndGet()
        val report = HidEncoder.mouseReport(profile, buttons.get(), delta.x, delta.y, delta.wheel, delta.pan)
        val sent = sendReport(hid, device, profile.mouseReportId, report)
        if (!sent) pointer.restore(delta)
        if (sent && delta != com.pocketdesk.app.input.PointerDelta(0, 0, 0, 0)) recordGap()
        if (sent && pointer.hasPending && sendMode != SendMode.FIXED_CADENCE) scheduleFlush()
    }

    /** Inter-report gap tells apart our own pacing from a stalling link. */
    private fun recordGap() {
        val now = System.nanoTime()
        val last = lastMouseReportAt
        lastMouseReportAt = now
        if (last == 0L) return
        val gapMs = (now - last) / 1_000_000
        gapSum += gapMs
        gapCount++
        if (gapMs > gapMax) gapMax = gapMs
    }

    /**
     * Reports the numbers that decide whether the pointer feels smooth:
     * touch event rate in, report rate out, and how often a batch had to be clipped
     * (excess movement and scroll are retained for later reports).
     */
    private fun startStatsLogger() {
        if (statsTask?.isDone == false) return
        statsTask = executor.scheduleWithFixedDelay({
            val moves = movesReceived.getAndSet(0)
            val sentNow = reportsSent.get()
            val failedNow = reportsFailed.get()
            val sent = sentNow - lastSentSnapshot
            val failed = failedNow - lastFailedSnapshot
            lastSentSnapshot = sentNow
            lastFailedSnapshot = failedNow
            val clipped = clippedReports.getAndSet(0)
            val text = if (moves == 0 && sent == 0L) {
                ""
            } else {
                val gapAvg = if (gapCount > 0) gapSum / gapCount else 0
                val gapText = if (gapCount > 0) " · 间隔 均 ${gapAvg}ms/最 ${gapMax}ms" else ""
                "触摸 $moves/s · 报告 ${sent}/s · 失败 $failed · 裁切 $clipped$gapText"
            }
            _stats.value = text
            if (moves > 0 || sent > 0) {
                log("stats: $text · ${modeLabel()}")
            }
            gapSum = 0
            gapMax = 0
            gapCount = 0
        }, 1000, 1000, TimeUnit.MILLISECONDS)
    }

    private fun modeLabel(): String = when (sendMode) {
        SendMode.COALESCED -> "合并 ${COALESCE_MS}ms"
        SendMode.DIRECT -> "不合并"
        SendMode.FIXED_CADENCE -> "定点 ${CADENCE_MS}ms"
    }

    /**
     * Forget everything this side had latched.
     *
     * The host resets its own button and modifier state when a HID device goes away,
     * so anything still held here would come back as a phantom pressed button or a
     * stuck Shift on the next connection.
     */
    private fun clearInputState(releaseToHost: Boolean = false) {
        inputGeneration++
        synchronized(continuousLock) { continuousGeneration.incrementAndGet() }
        pointer.clear()
        keyTimeline.clear()
        consumerTimeline.clear()
        stopCadenceLoop()
        buttons.set(0)
        synchronized(kbLock) { keyboard.clear() }
        if (releaseToHost) {
            val hid = hidDevice
            if (hid == null && reportWriter == null) return
            val device = connectedDevice ?: return
            val profile = currentProfile
            sendReport(hid, device, profile.mouseReportId, HidEncoder.mouseReport(profile, 0, 0, 0, 0, 0))
            sendReport(hid, device, profile.keyboardReportId, HidEncoder.keyboardReport(profile, 0, IntArray(6)))
            sendReport(hid, device, profile.consumerReportId, HidEncoder.consumerReport(profile, 0))
        }
    }

    private fun applyKey(usage: Int, modifiers: Int, down: Boolean) {
        val profile = currentProfile
        val hid = hidDevice
        if (hid == null && reportWriter == null) return
        val device = connectedDevice ?: return
        synchronized(kbLock) {
            val stored = if (down) {
                keyboard.press(usage, modifiers)
            } else {
                keyboard.release(usage, modifiers)
                true
            }
            if (!stored) log("键盘 6 键已满，丢弃 usage=$usage")
            sendReport(hid, device, profile.keyboardReportId,
                HidEncoder.keyboardReport(profile, keyboard.modifiers, keyboard.keys()))
        }
    }

    private fun queueKey(usage: Int, modifiers: Int, holdMs: Long, gapMs: Long) {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        val pulse = keyTimeline.reserve(now, holdMs.coerceAtLeast(1), gapMs)
        val generation = inputGeneration
        executor.schedule({
            if (generation == inputGeneration) applyKey(usage, modifiers, true)
        }, (pulse.downAtMs - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
        executor.schedule({
            if (generation == inputGeneration) applyKey(usage, modifiers, false)
        }, pulse.upAtMs - now, TimeUnit.MILLISECONDS)
    }

    private fun queueText(text: String) {
        var skipped = 0
        for (ch in text) {
            val mapped = HidKeymap.forChar(ch)
            if (mapped == null) {
                skipped++
            } else {
                queueKey(mapped.first, mapped.second, TEXT_HOLD_MS, TEXT_STEP_MS - TEXT_HOLD_MS)
            }
        }
        log("排队输入 ${text.length - skipped} 个字符（跳过 $skipped 个非 ASCII）")
    }

    private fun sendReport(hid: BluetoothHidDevice?, device: BluetoothDevice, id: Int, data: ByteArray): Boolean {
        // Counted once. The exception branch used to increment as well, so a thrown
        // sendReport() read as two failures in the diagnostics counters.
        val ok = runCatching { reportWriter?.invoke(id, data) ?: (hid?.sendReport(device, id, data) == true) }.getOrElse {
            lastError = it.message
            false
        }
        if (ok) reportsSent.incrementAndGet() else reportsFailed.incrementAndGet()
        return ok
    }

    /**
     * Sniff/park mode on the idle link is what makes the *first* movement after a pause
     * feel late: the report waits for the next wake slot. A steady 60 ms empty report
     * keeps the link out of the slow schedule at a cost of ~17 tiny reports per second.
     */
    private fun startKeepAlive() {
        if (keepAliveTask?.isDone == false) return
        keepAliveTask = executor.scheduleWithFixedDelay({
            val profile = currentProfile
            val hid = hidDevice
            val device = connectedDevice
            if ((hid != null || reportWriter != null) && device != null) {
                sendReport(hid, device, profile.mouseReportId,
                    HidEncoder.mouseReport(profile, buttons.get(), 0, 0, 0, 0))
            }
        }, KEEP_ALIVE_MS, KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)
    }

    private fun stopKeepAlive() {
        keepAliveTask?.cancel(false)
        keepAliveTask = null
    }

    // ────────────────────────────── bonding ──────────────────────────────

    private fun registerBondReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(bondReceiver, filter)
        }
        receiverRegistered = true
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return
                    val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    log("配对状态变化 ${device.address} → $bond")
                    if (bond == BluetoothDevice.BOND_BONDED && selection.requestedAddress == device.address) {
                        execute { connectInternal(device) }
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (newState == BluetoothAdapter.STATE_ON) {
                        log("蓝牙重新开启，重新注册 HID")
                        start()
                    }
                }
            }
        }
    }

    private fun fail(message: String) {
        lastError = message
        _state.value = TransportState.Error(message)
        log("ERROR: $message")
    }
}
