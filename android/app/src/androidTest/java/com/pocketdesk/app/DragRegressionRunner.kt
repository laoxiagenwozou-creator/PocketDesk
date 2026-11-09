package com.pocketdesk.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import androidx.activity.ComponentActivity
import java.io.File

class DragRegressionRunner : Instrumentation() {
    private lateinit var activity: Activity
    private lateinit var decor: View
    private var downTime = 0L
    private var time = 0L
    private var active = emptyList<Finger>()
    private val notes = mutableListOf<String>()
    private var failures = 0
    private data class Finger(val id: Int, val x: Float, val y: Float)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(targetContext.packageName == "com.pocketdesk.app.dragtest") { "Only runs against isolated app" }
            activity = startActivitySync(Intent().setClassName(targetContext.packageName, "com.pocketdesk.app.DragRegressionActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            decor = onMain { activity.window.decorView }
            waitForIdleSync()
            SystemClock.sleep(400)
            check(onMain { decor.hasWindowFocus() }) { "Unlock test phone first" }
            val b = onMain { activity.javaClass.getMethod("panelBounds").invoke(activity) as FloatArray }
            check(b.size == 5)
            val density = b[4]
            val left = Finger(7, b[0] + 56 * density, b[3] - 53 * density)
            val right = Finger(19, b[2] - 56 * density, left.y)
            val pad = Finger(31, b[0] + (b[2] - b[0]) * 0.45f, b[1] + (b[3] - b[1]) * 0.32f)
            case("hold_left_then_repeated_pad_strokes") {
                begin(left)
                repeat(3) { index ->
                    var moving = pad.copy(id = 31 + index)
                    emit(5 or (1 shl 8), listOf(left, moving))
                    repeat(10) { moving = moving.copy(x = moving.x + 8 * density); emit(2, listOf(left, moving)) }
                    emit(6 or (1 shl 8), listOf(left, moving))
                    val trace = trace()
                    check(trace.count { it.startsWith("M:1:") } >= (index + 1) * 7) { "Stroke ${index + 1} has no held-left motion: $trace" }
                    check(trace.none { it == "B:0" || it.startsWith("S:") || it.startsWith("C:") }) { "Drag was released or became scroll/click: $trace" }
                }
                emit(1, listOf(left))
                check(trace().last() == "B:0") { "Left was not released" }
            }
            case("move_pad_then_hold_left") {
                begin(pad)
                emit(5 or (1 shl 8), listOf(pad, left))
                var moving = pad
                repeat(10) { moving = moving.copy(x = moving.x + 8 * density); emit(2, listOf(moving, left)) }
                check(trace().count { it.startsWith("M:1:") } >= 7) { "No drag when pad touched first: ${trace()}" }
                emit(6 or (1 shl 8), listOf(moving, left))
                check(trace().last() == "B:0") { "Left release waited for unrelated pad finger" }
                emit(1, listOf(moving))
            }
            case("slide_held_left_button") {
                begin(left)
                var moving = left
                repeat(10) { moving = moving.copy(x = moving.x + 8 * density, y = moving.y - 4 * density); emit(2, listOf(moving)) }
                check(trace().count { it.startsWith("M:1:") } >= 7) { "Dragging from left button did not move: ${trace()}" }
                emit(1, listOf(moving))
                check(trace().last() == "B:0")
            }
            case("left_and_right_release_independently") {
                begin(left)
                emit(5 or (1 shl 8), listOf(left, right))
                check(trace().last() == "B:3") { "Right overwrote left: ${trace()}" }
                emit(6 or (1 shl 8), listOf(left, right))
                check(trace().last() == "B:1") { "Right release cleared left: ${trace()}" }
                emit(1, listOf(left))
                check(trace().last() == "B:0")
            }
            case("cancel_releases_button") {
                begin(left)
                cancel()
                check(trace().last() == "B:0") { "Cancel left stuck down: ${trace()}" }
            }
            case("drag_lock_survives_button_release") {
                val pill = Finger(41, (b[0] + b[2]) / 2, b[3] - 28 * density)
                begin(pill)
                emit(1, listOf(pill))
                SystemClock.sleep(80)
                check(trace().last() == "B:1") { "Drag lock did not engage: ${trace()}" }
                begin(left)
                emit(1, listOf(left))
                check(trace().none { it == "B:0" }) { "Button release cancelled drag lock: ${trace()}" }
                begin(pad)
                var moving = pad
                repeat(10) { moving = moving.copy(x = moving.x + 8 * density); emit(2, listOf(moving)) }
                emit(1, listOf(moving))
                check(trace().count { it.startsWith("M:1:") } >= 7)
                begin(pill)
                emit(1, listOf(pill))
                SystemClock.sleep(80)
                check(trace().last() == "B:0") { "Drag lock did not release: ${trace()}" }
            }
            case("tap_then_drag_releases_on_lift") {
                begin(pad)
                emit(1, listOf(pad))
                check(trace().last() == "C:1")
                begin(pad)
                var moving = pad
                repeat(10) { moving = moving.copy(x = moving.x + 8 * density); emit(2, listOf(moving)) }
                emit(1, listOf(moving))
                check(trace().count { it.startsWith("M:1:") } >= 7) { "Tap drag failed: ${trace()}" }
                check(trace().last() == "B:0")
            }
            case("pad_tap_while_left_held_does_not_click_or_release") {
                begin(left)
                emit(5 or (1 shl 8), listOf(left, pad))
                emit(6 or (1 shl 8), listOf(left, pad))
                check(trace() == listOf("B:1")) { "Pad tap changed held button: ${trace()}" }
                emit(1, listOf(left))
                check(trace().last() == "B:0")
            }
            case("two_quick_taps_send_exactly_two_clicks") {
                begin(pad)
                emit(1, listOf(pad))
                begin(pad)
                emit(1, listOf(pad))
                check(trace() == listOf("C:1", "C:1")) { "Double tap sent an extra click: ${trace()}" }
            }
            case("ordinary_pointer_and_scroll") {
                begin(pad)
                var moving = pad
                repeat(10) { moving = moving.copy(x = moving.x + 5 * density); emit(2, listOf(moving)) }
                emit(1, listOf(moving))
                check(trace().count { it.startsWith("M:0:") } >= 7)
                clear()
                moving = pad
                var second = pad.copy(id = 19, x = pad.x + 80 * density)
                begin(moving)
                emit(5 or (1 shl 8), listOf(moving, second))
                repeat(12) {
                    moving = moving.copy(y = moving.y + 10 * density)
                    second = second.copy(y = second.y + 10 * density)
                    emit(2, listOf(moving, second))
                }
                emit(6 or (1 shl 8), listOf(moving, second))
                emit(1, listOf(moving))
                check(trace().any { it.startsWith("S:") }) { "Two-finger scroll regressed: ${trace()}" }
                check(trace().none { it.startsWith("M:") || it.startsWith("C:") })
            }
            case("left_edge_accumulates_small_moves") {
                var moving = Finger(61, b[0] + 12 * density, b[1] + 110 * density)
                begin(moving)
                repeat(20) { moving = moving.copy(y = moving.y + 4 * density); emit(2, listOf(moving)) }
                emit(1, listOf(moving))
                check(trace().any { it.startsWith("S:") }) { "Left edge did not scroll: ${trace()}" }
                check(trace().none { it.startsWith("M:") || it.startsWith("C:") }) { "Edge gesture leaked into touchpad: ${trace()}" }
            }
            case("right_ring_enables_edge_and_repeated_strokes") {
                val ring = Finger(62, b[2] - 18 * density, b[1] + 18 * density)
                begin(ring); emit(1, listOf(ring)); SystemClock.sleep(150)
                clear()
                repeat(2) { stroke ->
                    var moving = Finger(63 + stroke, b[2] - 12 * density, b[1] + 110 * density)
                    begin(moving)
                    repeat(20) { moving = moving.copy(y = moving.y + 4 * density); emit(2, listOf(moving)) }
                    emit(1, listOf(moving))
                    check(trace().count { it.startsWith("S:") } >= stroke + 1) { "Right edge stroke ${stroke + 1} did not scroll: ${trace()}" }
                }
                check(trace().none { it.startsWith("M:") || it.startsWith("C:") }) { "Edge gesture leaked into touchpad: ${trace()}" }
            }
            onMain { activity.javaClass.getMethod("showKeyboard").invoke(activity) }
            waitForIdleSync()
            uiAutomation.waitForIdle(100, 3000)
            case("keyboard_punctuation_keeps_modifiers") {
                tapLabel("⇧")
                tapLabel("，")
                check(trace().last() == "K:54:2") { "Shift lost on comma: ${trace()}" }
                tapLabel("⌘")
                tapLabel("。")
                check(trace().last() == "K:55:1") { "Ctrl lost on period: ${trace()}" }
                tapLabel("a")
                check(trace().last() == "K:4:0")
            }
            case("media_123_returns_to_symbols_and_back_returns_letters") {
                tapLabel("123")
                tapLabel("媒体")
                tapLabel("123")
                keyFinger("-")
                onMain { (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed() }
                waitForIdleSync()
                keyFinger("q")
            }
            case("held_backspace_repeats_and_stops_on_release") {
                val key = keyFinger("⌫")
                begin(key)
                SystemClock.sleep(600)
                emit(1, listOf(key))
                val count = trace().count { it == "K:42:0" }
                check(count >= 3) { "Backspace did not repeat: ${trace()}" }
                SystemClock.sleep(200)
                check(trace().count { it == "K:42:0" } == count) { "Backspace continued after release" }
            }
            case("extension_entry_collapse_and_system_back") {
                tapLabel("扩展")
                keyFinger("Esc")
                saveShot("haptic-common")
                tapLabel("收起")
                keyFinger("q")
                tapLabel("扩展")
                keyFinger("Esc")
                SystemClock.sleep(120)
                onMain { (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed() }
                waitForIdleSync()
                keyFinger("q")
            }
            tapLabel("扩展")
            case("extended_common_keys_send_correct_hid_usages") {
                listOf("Esc" to 41, "Tab" to 43, "Home" to 74, "End" to 77,
                    "PgUp" to 75, "PgDn" to 78, "↑" to 82, "Del" to 76,
                    "←" to 80, "↓" to 81, "→" to 79, "Enter" to 40).forEach { (label, usage) ->
                    tapLabel(label)
                    check(trace().last() == "K:$usage:0") { "$label mapped incorrectly: ${trace()}" }
                }
            }
            case("held_ctrl_arrow_keeps_modifier_then_consumes_it") {
                tapLabel("Ctrl")
                val key = keyFinger("→")
                begin(key)
                SystemClock.sleep(650)
                emit(1, listOf(key))
                val keys = trace().filter { it.startsWith("K:") }
                check(keys.size >= 4 && keys.all { it == "K:79:1" }) { "Held modifier changed: $keys" }
                tapLabel("→")
                check(trace().last() == "K:79:0")
            }
            case("held_arrow_cancel_and_hidden_panel_stop_repeating") {
                val key = keyFinger("↓")
                begin(key)
                SystemClock.sleep(500)
                cancel()
                val count = trace().size
                SystemClock.sleep(180)
                check(trace().size == count)
                begin(key)
                onMain { activity.javaClass.getMethod("showKeyboard").invoke(activity) }
                waitForIdleSync()
                val hiddenCount = trace().count { it.startsWith("K:") }
                SystemClock.sleep(450)
                check(trace().count { it.startsWith("K:") } == hiddenCount) { "Hidden arrow still repeating: ${trace()}" }
            }
            tapLabel("扩展")
            case("background_stops_held_arrow_without_recomposition") {
                val key = keyFinger("↓")
                begin(key)
                SystemClock.sleep(450)
                try {
                    onMain { (activity as ComponentActivity).lifecycle.let { (it as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.CREATED } }
                    val count = trace().count { it.startsWith("K:") }
                    SystemClock.sleep(180)
                    check(trace().count { it.startsWith("K:") } == count) { "Paused key kept repeating" }
                } finally {
                    onMain { (activity as ComponentActivity).lifecycle.let { (it as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.RESUMED } }
                    waitForIdleSync()
                    SystemClock.sleep(120)
                }
                val count = trace().count { it.startsWith("K:") }
                SystemClock.sleep(150)
                check(trace().count { it.startsWith("K:") } == count) { "Resume revived old key hold" }
            }
            case("function_keys_and_real_gui_modifier") {
                tapLabel("F1–F12")
                saveShot("haptic-functions")
                (1..12).forEach { n ->
                    tapLabel("F$n")
                    check(trace().last() == "K:${57+n}:0")
                }
                tapLabel("Win")
                tapLabel("F1")
                check(trace().last() == "K:58:8")
            }
            tapLabel("持续滚动")
            case("lever_up_down_center_lift_and_repeated_strokes") {
                val center = keyFinger("持续滚动拨杆")
                val up = center.copy(y = center.y - 60 * density)
                val down = center.copy(y = center.y + 60 * density)
                repeat(2) {
                    begin(up)
                    check(lastRate() > 0f)
                    emit(2, listOf(center))
                    check(kotlin.math.abs(lastRate()) <= 0.12f)
                    emit(2, listOf(down))
                    check(lastRate() < 0f)
                    saveShot("haptic-scrolling")
                    emit(1, listOf(down))
                    check(lastRate() == 0f)
                }
                saveShot("haptic-scroll-stopped")
            }
            case("lever_does_not_transfer_to_second_finger") {
                val center = keyFinger("持续滚动拨杆")
                val first = center.copy(y = center.y - 60 * density)
                val second = center.copy(id = 19, y = center.y + 60 * density)
                begin(first)
                emit(5 or (1 shl 8), listOf(first, second))
                check(lastRate() > 0f)
                emit(6, listOf(first, second))
                check(lastRate() == 0f)
                emit(2, listOf(second.copy(y = second.y + 10)))
                check(lastRate() == 0f)
                emit(1, listOf(second))
            }
            case("lever_cancel_exit_and_dispose_stop") {
                val center = keyFinger("持续滚动拨杆")
                val up = center.copy(y = center.y - 60 * density)
                begin(up); cancel()
                check(lastRate() == 0f)
                begin(up)
                emit(2, listOf(up.copy(x = -20f)))
                check(lastRate() == 0f)
                cancel()
                begin(up)
                onMain { activity.javaClass.getMethod("showKeyboard").invoke(activity) }
                waitForIdleSync()
                keyFinger("q")
                check(lastRate() == 0f) { "Disposed lever did not stop: ${trace()}" }
            }
            tapLabel("扩展")
            tapLabel("持续滚动")
            case("background_stops_lever_and_requires_fresh_touch") {
                val up = keyFinger("持续滚动拨杆").let { it.copy(y = it.y - 60 * density) }
                begin(up)
                try {
                    onMain { (activity as ComponentActivity).lifecycle.let { (it as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.CREATED } }
                    check(lastRate() == 0f) { "Pause did not synchronously stop lever: ${trace()}" }
                } finally {
                    onMain { (activity as ComponentActivity).lifecycle.let { (it as androidx.lifecycle.LifecycleRegistry).currentState = androidx.lifecycle.Lifecycle.State.RESUMED } }
                    waitForIdleSync()
                    SystemClock.sleep(120)
                }
                check(lastRate() == 0f) { "Resume revived old gesture: ${trace()}" }
            }
            case("covered_input_stops_lever_and_rejects_moves") {
                val up = keyFinger("持续滚动拨杆").let { it.copy(y = it.y - 60 * density) }
                begin(up)
                check(lastRate() > 0f)
                try {
                    onMain { activity.javaClass.getMethod("allowInput", Boolean::class.javaPrimitiveType).invoke(activity, false) }
                    waitForIdleSync()
                    SystemClock.sleep(120)
                    check(lastRate() == 0f)
                    emit(2, listOf(up.copy(y = up.y - 10)))
                    check(lastRate() == 0f)
                } finally {
                    cancel()
                    onMain { activity.javaClass.getMethod("allowInput", Boolean::class.javaPrimitiveType).invoke(activity, true) }
                    waitForIdleSync()
                }
            }
            case("offline_lever_is_disabled") {
                configure("haptic", 1f, false, false)
                val up = keyFinger("持续滚动拨杆").let { it.copy(y = it.y - 60 * density) }
                begin(up); emit(1, listOf(up))
                check(trace().filter { it.startsWith("RATE:") }.all { it == "RATE:0.0" })
            }
            configure("night", 1f, false, true)
            case("night_skin_and_large_font_compact_layout") {
                saveShot("night-scroll")
                tapLabel("常用键")
                saveShot("night-common")
                configure("haptic", 1.4f, true, true)
                saveShot("compact-large-font")
                tapLabel("Esc")
                check(trace().last() == "K:41:0")
                var moving = keyFinger("PgDn")
                begin(moving)
                repeat(9) {
                    moving = moving.copy(y = moving.y - 9 * density)
                    emit(2, listOf(moving))
                }
                emit(1, listOf(moving))
                waitForIdleSync()
                SystemClock.sleep(120)
                keyFinger("Ctrl")
                saveShot("compact-large-font-scrolled")
                tapLabel("Ctrl")
                tapLabel("收起")
                keyFinger("q")
                configure("haptic", 1f, false, true)
            }
            val transportNotes = TransportRegression.run(targetContext, this)
            notes += transportNotes
            failures += transportNotes.count { it.contains(" FAIL ") }
        } catch (t: Throwable) {
            failures++
            notes += "setup FAIL $t"
        } finally {
            if (::decor.isInitialized) runCatching { cancel() }
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
            result.putInt("failures", failures)
            result.putString("stream", notes.joinToString("\n", postfix = "\nfailures=$failures\n"))
            targetContext.getExternalFilesDir(null)?.let { File(it, "drag-result.txt").writeText(result.getString("stream").orEmpty()) }
            finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun lastRate(): Float = trace().last { it.startsWith("RATE:") }.substringAfter(":").toFloat()
    private fun configure(theme: String, scale: Float, compact: Boolean, online: Boolean) {
        onMain {
            activity.javaClass.getMethod("configure", String::class.java, Float::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).invoke(activity, theme, scale, compact, online)
        }
        waitForIdleSync()
        uiAutomation.waitForIdle(100, 3000)
    }
    private fun saveShot(name: String) {
        SystemClock.sleep(150)
        uiAutomation.takeScreenshot()?.let { bitmap ->
            targetContext.getExternalFilesDir(null)?.let { dir ->
                File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String): Rect? {
        if (node == null || !node.refresh()) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return Rect().also { node.getBoundsInScreen(it) }
        repeat(node.childCount) { findText(node.getChild(it), text)?.let { bounds -> return bounds } }
        return null
    }
    private fun keyFinger(label: String): Finger {
        val deadline = SystemClock.uptimeMillis() + 3000
        var bounds: Rect? = null
        while (bounds == null && SystemClock.uptimeMillis() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            root?.refresh()
            bounds = findText(root, label)
            if (bounds == null) SystemClock.sleep(50)
        }
        val key = checkNotNull(bounds) { "Cannot find key $label" }
        val offset = onMain { IntArray(2).also { decor.getLocationOnScreen(it) } }
        return Finger(7, key.exactCenterX() - offset[0], key.exactCenterY() - offset[1])
    }
    private fun tapLabel(label: String) {
        val finger = keyFinger(label)
        begin(finger)
        emit(1, listOf(finger))
        waitForIdleSync()
        SystemClock.sleep(120)
        uiAutomation.waitForIdle(100, 3000)
    }

    private fun case(name: String, block: () -> Unit) {
        clear()
        try { block(); notes += "$name PASS ${trace().size} events" }
        catch (t: Throwable) {
            failures++
            notes += "$name FAIL $t"
            uiAutomation.takeScreenshot()?.let { bitmap ->
                targetContext.getExternalFilesDir(null)?.let { dir ->
                    File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                bitmap.recycle()
            }
        }
        finally { cancel(); SystemClock.sleep(350) }
    }
    private fun clear() = onMain { activity.javaClass.getMethod("clearTrace").invoke(activity); Unit }
    private fun trace(): List<String> = onMain { (activity.javaClass.getMethod("readTrace").invoke(activity) as String).lines().filter { it.isNotBlank() } }
    private fun begin(f: Finger) { check(onMain { decor.hasWindowFocus() }) { "Test activity lost focus; refusing input" }; downTime = SystemClock.uptimeMillis(); time = downTime; emit(0, listOf(f)) }
    private fun cancel() { if (active.isNotEmpty()) emit(3, active) }
    private fun emit(action: Int, fingers: List<Finger>) {
        SystemClock.sleep(16)
        time = maxOf(SystemClock.uptimeMillis(), time + 1)
        val props = fingers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = 1 } }.toTypedArray()
        val coords = fingers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 0.08f } }.toTypedArray()
        val e = MotionEvent.obtain(downTime, time, action, fingers.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            onMain { decor.dispatchTouchEvent(e) }
            active = when (e.actionMasked) { 1, 3 -> emptyList(); 6 -> fingers.filterIndexed { i, _ -> i != e.actionIndex }; else -> fingers }
        } finally { e.recycle() }
    }
    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }
}
