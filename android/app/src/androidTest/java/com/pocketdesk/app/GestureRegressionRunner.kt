package com.pocketdesk.app

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.State
import com.pocketdesk.app.core.AppTheme
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.transport.hid.BluetoothHidTransport
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class GestureRegressionRunner : Instrumentation() {
    private var arguments = Bundle()
    private val result = Bundle()
    private val notes = mutableListOf<String>()
    private val screenshots = mutableListOf<String>()
    private var failures = 0
    private var blocked = 0
    private lateinit var evidence: File
    private lateinit var decor: View
    private var downTime = 0L
    private var lastEventTime = 0L
    private var active = emptyList<Finger>()

    private data class Finger(val id: Int, val x: Float, val y: Float)
    private class Blocked(message: String) : RuntimeException(message)

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        var activity: Activity? = null
        try {
            val root = targetContext.getExternalFilesDir(null)
                ?: throw Blocked("Target external files directory unavailable")
            evidence = File(root, "gesture-regression/${System.currentTimeMillis()}")
            check(evidence.mkdirs()) { "Cannot create $evidence" }
            result.putString("evidence_dir", evidence.absolutePath)
            ensureUnlocked()
            val launched = startActivitySync(
                Intent(targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            activity = launched
            decor = onMain { launched.window.decorView }
            val service = awaitService(launched)
            waitForIdleSync()
            SystemClock.sleep(600)
            ensureForeground()
            result.putString("theme", onMain { service.theme().id })
            result.putString("send_mode", service.transport.sendMode.name)
            result.putString("report_evidence", "Successful HID report counts above the keepalive upper bound; does not identify wheel payloads or prove host scrolling")
            if (arguments.getString("profile") == "true") {
                testCase("keyboard_profile") { profileKeyboard() }
            }
            if (service.transport.state.value is TransportState.Connected) {
                runGestures(service)
            } else {
                block("gestures", "Requires an already connected HID host: ${service.transport.state.value}")
            }
            if (onMain { service.theme() } == AppTheme.HAPTIC) {
                testCase("keyboard_clip") { checkKeyboard() }
            } else {
                block("keyboard_clip", "Select the haptic skin before running; runner does not change preferences")
            }
        } catch (e: Blocked) {
            block("setup", e.message ?: "Blocked")
        } catch (t: Throwable) {
            fail("setup", t)
        } finally {
            if (::decor.isInitialized) runCatching { cancelTouch() }
            if (activity != null) runCatching { runOnMainSync { activity.finish() } }
            result.putInt("failures", failures)
            result.putInt("blocked", blocked)
            result.putString("screenshots", screenshots.joinToString("\n"))
            result.putString("summary", notes.joinToString("\n"))
            result.putString("status", when {
                failures > 0 -> "FAILED"
                blocked > 0 -> "BLOCKED"
                else -> "PASSED_WITH_REPORT_COUNT_PROXY"
            })
            result.putString("stream", "\n${result.getString("status")} failures=$failures blocked=$blocked\n${notes.joinToString("\n")}\n${screenshots.joinToString("\n")}\n")
            if (::evidence.isInitialized) {
                runCatching { File(evidence, "result.txt").writeText(result.getString("stream").orEmpty()) }
            }
            finish(if (failures == 0 && blocked == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun ensureUnlocked() {
        val power = targetContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive && arguments.getString("wake") == "true") {
            uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_WAKEUP), true)
            uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_WAKEUP), true)
            SystemClock.sleep(500)
        }
        val keyguard = targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!power.isInteractive || keyguard.isKeyguardLocked || keyguard.isDeviceLocked) {
            throw Blocked("Device screen is off or keyguard is locked; unlock manually and rerun. No unlock attempted")
        }
    }

    private fun ensureForeground() {
        ensureUnlocked()
        if (!onMain { decor.isShown && decor.hasWindowFocus() && decor.width > 0 && decor.height > 0 }) {
            throw Blocked("MainActivity has no visible focused window; check permissions or system dialogs")
        }
    }

    private fun awaitService(activity: Activity): HidService {
        val field = MainActivity::class.java.getDeclaredField("service\$delegate").apply { isAccessible = true }
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val service = onMain { (field.get(activity) as State<*>).value as? HidService }
            if (service != null) return service
            ensureUnlocked()
            SystemClock.sleep(50)
        }
        throw Blocked("HidService did not bind within 15 seconds; pregrant app permissions and dismiss system dialogs")
    }

    private fun runGestures(service: HidService) {
        val rounds = (arguments.getString("rounds")?.toIntOrNull() ?: 6).coerceAtLeast(6)
        result.putInt("rounds_requested", rounds)
        var completed = 0
        repeat(rounds) { round ->
            testCase("round_${round + 1}") {
                ensureForeground()
                check(service.transport.state.value is TransportState.Connected) { "HID host disconnected" }
                onMain { service.cancelFling() }
                SystemClock.sleep(80)
                val sentBefore = service.transport.reportsSent.get()
                val failedBefore = service.transport.reportsFailed.get()
                val started = SystemClock.uptimeMillis()
                val variant = round % 3
                val simultaneous = round % 2 == 0
                val width = onMain { decor.width.toFloat() }
                val height = onMain { decor.height.toFloat() }
                var first = Finger(7, width * 0.36f, height * 0.19f)
                var second = Finger(19, width * 0.64f, height * 0.19f)
                begin(first)
                emit(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
                repeat(20) { frame ->
                    SystemClock.sleep(16)
                    val step = if (frame < 10) frame + 1 else 19 - frame
                    val y = height * (0.19f + step * 0.024f)
                    first = first.copy(y = y)
                    second = second.copy(y = y)
                    emit(MotionEvent.ACTION_MOVE, listOf(first, second))
                    if (variant == 2 && frame == 9) {
                        emit(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
                        SystemClock.sleep(16)
                        emit(MotionEvent.ACTION_MOVE, listOf(first))
                        second = second.copy(id = 31)
                        emit(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
                    }
                }
                val firstUpIndex = if (variant == 1) 0 else 1
                emit(MotionEvent.ACTION_POINTER_UP or (firstUpIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
                if (!simultaneous) SystemClock.sleep(24)
                emit(MotionEvent.ACTION_UP, listOf(if (firstUpIndex == 0) second else first), sameTime = simultaneous)
                onMain { service.cancelFling() }
                SystemClock.sleep(40)
                val elapsed = SystemClock.uptimeMillis() - started
                val sent = service.transport.reportsSent.get() - sentBefore
                val failed = service.transport.reportsFailed.get() - failedBefore
                val keepaliveMax = ceil(elapsed.toDouble() / BluetoothHidTransport.KEEP_ALIVE_MS).toLong() + 1
                val excess = sent - keepaliveMax
                val description = "round=${round + 1} frames=20 transition=${if (variant == 2) "2->1->2" else "1->2"} firstUpIndex=$firstUpIndex simultaneousEventTime=$simultaneous sent=$sent failed=$failed elapsedMs=$elapsed keepaliveUpper=$keepaliveMax excess=$excess"
                result.putString("round_${round + 1}", description)
                notes += description
                check(failed == 0L) { "HID report failures: $failed" }
                check(excess >= 12) { "Only $excess reports beyond keepalive; need >=12 (report-count proxy, wheel contents unobserved)" }
                check(service.transport.state.value is TransportState.Connected) { "HID host disconnected" }
                completed++
            }
            cancelTouch()
        }
        result.putInt("rounds_passed", completed)
    }

    private fun profileKeyboard() {
        ensureForeground()
        var keyBounds: Rect? = null
        repeat(20) {
            if (keyBounds == null) {
                keyBounds = findKeyBounds(uiAutomation.rootInActiveWindow)
                if (keyBounds == null) SystemClock.sleep(100)
            }
        }
        val key = keyBounds ?: throw Blocked("Cannot locate g key for profile")
        val location = onMain { IntArray(2).also { decor.getLocationOnScreen(it) } }
        fun exercise() {
            begin(Finger(7, key.exactCenterX() - location[0], key.exactCenterY() - location[1]))
            SystemClock.sleep(130)
            cancelTouch()
            SystemClock.sleep(130)
        }
        repeat(3) { exercise() }
        shellOutput("dumpsys gfxinfo com.pocketdesk.app reset")
        repeat(24) { exercise() }
        val output = shellOutput("dumpsys gfxinfo com.pocketdesk.app")
        File(evidence, "gfxinfo.txt").writeText(output)
        notes += output.lineSequence().filter {
            it.startsWith("Total frames") || it.startsWith("Janky frames:") ||
                it.contains("percentile:") || it.startsWith("Number Frame deadline missed:")
        }.joinToString("; ")
    }

    private fun shellOutput(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun checkKeyboard() {
        ensureForeground()
        val location = onMain { IntArray(2).also { decor.getLocationOnScreen(it) } }
        val width = onMain { decor.width }
        val height = onMain { decor.height }
        val override = arguments.getString("gBounds")?.split(',')?.map { it.trim().toInt() }
        val discovered = findKeyBounds(uiAutomation.rootInActiveWindow)
        val key = when {
            override != null -> {
                require(override.size == 4) { "gBounds must be screen pixels: left,top,right,bottom" }
                Rect(override[0], override[1], override[2], override[3])
            }
            discovered != null -> discovered
            else -> Rect(
                location[0] + (width * 0.453f).roundToInt(),
                location[1] + (height * 0.802f).roundToInt(),
                location[0] + (width * 0.547f).roundToInt(),
                location[1] + (height * 0.857f).roundToInt(),
            )
        }
        result.putString("g_bounds_source", when {
            override != null -> "explicit screen bounds"
            discovered != null -> "platform accessibility clickable g node"
            else -> "estimated from 1140x2616 layout; manual screenshot review required"
        })
        result.putString("g_bounds", key.toShortString())
        require(key.width() > 0 && key.height() > 0 && Rect(location[0], location[1], location[0] + width, location[1] + height).contains(key)) { "Invalid g key bounds: $key" }
        val before = screenshot("g-before.png")
        var held: Bitmap? = null
        try {
            begin(Finger(7, key.exactCenterX() - location[0], key.exactCenterY() - location[1]))
            SystemClock.sleep(240)
            ensureForeground()
            held = screenshot("g-held.png")
        } finally {
            cancelTouch()
        }
        SystemClock.sleep(240)
        val after = screenshot("g-cancelled.png")
        try {
            val pressed = checkNotNull(held)
            require(before.width == pressed.width && before.height == pressed.height && before.width == after.width && before.height == after.height) { "Screenshot dimensions changed" }
            require(Rect(0, 0, before.width, before.height).contains(key)) { "Screenshot and key coordinates differ" }
            val density = targetContext.resources.displayMetrics.density
            val inner = Rect(key).apply { inset((4 * density).roundToInt(), (4 * density).roundToInt()) }
            val near = Rect(key).apply { inset(-(24 * density).roundToInt(), -(24 * density).roundToInt()) }
            val roi = Rect(key).apply { inset(-(40 * density).roundToInt(), -(40 * density).roundToInt()) }
            roi.intersect(0, 0, before.width, before.height)
            val inside = changedFraction(before, pressed, inner) { _, _ -> true }
            val nearOutside = changedFraction(before, pressed, roi) { x, y -> near.contains(x, y) && !key.contains(x, y) }
            val farOutside = changedFraction(before, pressed, roi) { x, y -> !near.contains(x, y) }
            val restored = changedFraction(before, after, roi) { _, _ -> true }
            result.putString("keyboard_pixels", "inside=$inside nearOutside=$nearOutside farOutside=$farOutside restored=$restored; nearOutside includes normal raised-shadow fade; farOutside is only a spill heuristic")
            notes += "g held then ACTION_CANCEL only: inside=$inside nearOutside=$nearOutside farOutside=$farOutside restored=$restored"
            check(inside >= 0.02) { "No visible pressed key state; check g bounds and haptic theme" }
            check(farOutside <= 0.02) { "Unexpected changes far outside key: $farOutside" }
            check(restored <= 0.02) { "Key did not return to its baseline after CANCEL: $restored" }
        } finally {
            before.recycle()
            held?.recycle()
            after.recycle()
        }
    }

    private fun findKeyBounds(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        if (node.text?.toString() == "g") {
            var candidate: AccessibilityNodeInfo? = node
            while (candidate != null) {
                if (candidate.isClickable) {
                    val bounds = Rect()
                    candidate.getBoundsInScreen(bounds)
                    return bounds
                }
                candidate = candidate.parent
            }
        }
        for (index in 0 until node.childCount) {
            findKeyBounds(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun changedFraction(a: Bitmap, b: Bitmap, area: Rect, include: (Int, Int) -> Boolean): Double {
        var total = 0
        var changed = 0
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) {
                if (!include(x, y)) continue
                total++
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                val difference = max(abs(Color.red(p) - Color.red(q)), max(abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q))))
                if (difference > 12) changed++
            }
        }
        check(total > 0) { "Empty pixel comparison region" }
        return changed.toDouble() / total
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = uiAutomation.takeScreenshot() ?: throw Blocked("UiAutomation screenshot unavailable")
        val file = File(evidence, name)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshots += file.absolutePath
        return bitmap
    }

    private fun begin(finger: Finger) {
        downTime = SystemClock.uptimeMillis()
        lastEventTime = downTime
        emit(MotionEvent.ACTION_DOWN, listOf(finger))
    }

    private fun emit(action: Int, fingers: List<Finger>, sameTime: Boolean = false) {
        val time = if (sameTime) lastEventTime else max(SystemClock.uptimeMillis(), lastEventTime + 1)
        lastEventTime = time
        val properties = fingers.map { finger ->
            MotionEvent.PointerProperties().apply { id = finger.id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coords = fingers.map { finger ->
            MotionEvent.PointerCoords().apply { x = finger.x; y = finger.y; pressure = 1f; size = 0.08f }
        }.toTypedArray()
        val event = MotionEvent.obtain(downTime, time, action, fingers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            val accepted = onMain { decor.dispatchTouchEvent(event) }
            check(accepted || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                "decorView rejected action=$action"
            }
            active = when (action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> emptyList()
                MotionEvent.ACTION_POINTER_UP -> fingers.filterIndexed { index, _ -> index != event.actionIndex }
                else -> fingers
            }
        } finally {
            event.recycle()
        }
    }

    private fun cancelTouch() {
        if (active.isNotEmpty()) emit(MotionEvent.ACTION_CANCEL, active)
    }

    private fun testCase(name: String, test: () -> Unit) {
        try {
            test()
            result.putString("${name}_status", "PASS")
        } catch (e: Blocked) {
            block(name, e.message ?: "Blocked")
        } catch (t: Throwable) {
            fail(name, t)
        } finally {
            runCatching { cancelTouch() }
        }
    }

    private fun block(name: String, message: String) {
        blocked++
        result.putString("${name}_status", "BLOCKED: $message")
        notes += "$name BLOCKED: $message"
    }

    private fun fail(name: String, error: Throwable) {
        failures++
        result.putString("${name}_status", "FAIL: $error")
        notes += "$name FAIL: $error"
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        runOnMainSync { outcome = runCatching(block) }
        return checkNotNull(outcome).getOrThrow()
    }
}
