package com.pocketdesk.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.app.Instrumentation
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.input.MouseButtonSource
import com.pocketdesk.app.core.InputEvent
import com.pocketdesk.app.core.TransportState
import com.pocketdesk.app.input.HostSelection
import com.pocketdesk.app.transport.hid.BluetoothHidTransport
import com.pocketdesk.app.transport.hid.HidKeymap
import com.pocketdesk.app.transport.hid.HidProfiles
import java.util.Collections
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object TransportRegression {
    fun run(context: Context, instrumentation: Instrumentation): List<String> {
        check(context.packageName == "com.pocketdesk.app.dragtest")
        val notes = mutableListOf<String>()
        val reports = Collections.synchronizedList(mutableListOf<Pair<Int, ByteArray>>())
        val transport = BluetoothHidTransport(context, reportWriter = { id, bytes ->
            reports += id to bytes.copyOf()
            true
        }, log = {})
        val executor = field(transport, "executor") as ScheduledThreadPoolExecutor
        val callback = field(transport, "hidCallback") as BluetoothHidDevice.Callback
        val selection = field(transport, "selection") as HostSelection
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val first = adapter.getRemoteDevice("02:00:00:00:00:11")
        val second = adapter.getRemoteDevice("02:00:00:00:00:12")
        fun onExecutor(action: () -> Unit) = executor.submit(action).get(5, TimeUnit.SECONDS)
        fun snapshot() = synchronized(reports) { reports.toList() }
        fun clear() = reports.clear()
        fun checkCase(name: String, test: () -> Unit) {
            try { test(); notes += "$name PASS" }
            catch (t: Throwable) { notes += "$name FAIL $t" }
        }
        try {
            transport.sendMode = BluetoothHidTransport.SendMode.DIRECT
            onExecutor { callback.onConnectionStateChanged(first, BluetoothProfile.STATE_CONNECTED) }
            val service = HidService()
            HidService::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(service, transport)
            checkCase("service_release_resets_drag_lock_and_all_button_sources") {
                instrumentation.runOnMainSync {
                    service.setDragLocked(true)
                    service.setHeldButtons(MouseButtonSource.RIGHT_PAD, 2)
                    check(service.dragLocked.value && service.hasHeldButtons())
                    service.releasePointerInput()
                    check(!service.dragLocked.value && !service.hasHeldButtons())
                }
            }
            checkCase("old_click_release_cannot_release_new_drag") {
                instrumentation.runOnMainSync {
                    service.click(1)
                    service.setHeldButtons(MouseButtonSource.LEFT_PAD, 1)
                }
                Thread.sleep(100)
                instrumentation.runOnMainSync {
                    check(service.hasHeldButtons())
                    service.releasePointerInput()
                }
            }
            checkCase("serialized_text_and_backspaces") {
                clear()
                transport.send(InputEvent.Text("aaaa"))
                transport.send(InputEvent.Text("aaaa"))
                repeat(3) { transport.send(InputEvent.KeyTap(HidKeymap.BACKSPACE)) }
                Thread.sleep(650)
                val keys = snapshot().filter { it.first == HidProfiles.A_LINKPAD.keyboardReportId }
                    .map { it.second[2].toInt() and 255 }.filter { it != 0 }
                check(keys == List(8) { 4 } + List(3) { HidKeymap.BACKSPACE }) { "Unexpected keys: $keys" }
            }
            checkCase("rapid_button_down_up_preserves_both_edges") {
                clear()
                transport.send(InputEvent.PointerButtons(1))
                transport.send(InputEvent.PointerButtons(0))
                onExecutor { }
                val masks = snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.map { it.second[0].toInt() }
                check(masks.contains(1) && masks.last() == 0) { "Button edges lost: $masks" }
            }
            checkCase("wheel_and_pan_overflow_is_not_discarded") {
                clear()
                transport.send(InputEvent.Scroll(500, -350))
                Thread.sleep(160)
                val mouse = snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.map { it.second }
                check(mouse.sumOf { it[3].toInt() } == 500)
                check(mouse.sumOf { it[4].toInt() } == -350)
            }
            checkCase("stopped_wheel_session_drops_already_queued_pulses") {
                clear()
                val gate = java.util.concurrent.CountDownLatch(1)
                val entered = java.util.concurrent.CountDownLatch(1)
                executor.execute { entered.countDown(); gate.await(3, TimeUnit.SECONDS) }
                check(entered.await(1, TimeUnit.SECONDS))
                val session = transport.beginContinuousScroll()
                try {
                    repeat(10) { transport.send(InputEvent.WheelPulse(1, session)) }
                    transport.stopContinuousScroll(session)
                } finally { gate.countDown() }
                onExecutor { }
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.all { it.second[3] == 0.toByte() })
            }
            checkCase("expired_wheel_pulse_and_old_stop_do_not_affect_new_session") {
                clear()
                val old = transport.beginContinuousScroll()
                val current = transport.beginContinuousScroll()
                transport.stopContinuousScroll(old)
                transport.send(InputEvent.WheelPulse(2, current, System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(100)))
                transport.send(InputEvent.WheelPulse(-1, current))
                onExecutor { }
                transport.stopContinuousScroll(current)
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.sumOf { it.second[3].toInt() } == -1)
            }
            checkCase("service_continuous_scroll_release_stops_reports") {
                clear()
                instrumentation.runOnMainSync { service.setContinuousScroll(1f) }
                Thread.sleep(180)
                check(snapshot().any { it.first == HidProfiles.A_LINKPAD.mouseReportId && it.second[3] > 0 })
                instrumentation.runOnMainSync { service.releasePointerInput() }
                onExecutor { clear() }
                Thread.sleep(120)
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.all { it.second[3] == 0.toByte() })
            }
            checkCase("service_center_and_mouse_hold_stop_continuous_scroll") {
                instrumentation.runOnMainSync { service.setContinuousScroll(-1f) }
                Thread.sleep(100)
                instrumentation.runOnMainSync { service.setContinuousScroll(0.05f) }
                onExecutor { clear() }
                Thread.sleep(100)
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.all { it.second[3] == 0.toByte() })
                instrumentation.runOnMainSync {
                    service.setContinuousScroll(-1f)
                    service.setHeldButtons(MouseButtonSource.LEFT_PAD, 1)
                    service.setContinuousScroll(-1f)
                }
                onExecutor { clear() }
                Thread.sleep(100)
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.all { it.second[3] == 0.toByte() })
                instrumentation.runOnMainSync { service.releasePointerInput() }
            }
            checkCase("manual_pointer_movement_stops_continuous_scroll") {
                instrumentation.runOnMainSync { service.setContinuousScroll(1f) }
                Thread.sleep(80)
                instrumentation.runOnMainSync { service.move(1, 0) }
                onExecutor { clear() }
                Thread.sleep(100)
                check(snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.all { it.second[3] == 0.toByte() })
            }
            checkCase("disconnect_drops_old_text_before_new_peer") {
                transport.send(InputEvent.Text("a".repeat(100)))
                Thread.sleep(35)
                onExecutor {
                    callback.onConnectionStateChanged(first, BluetoothProfile.STATE_DISCONNECTED)
                    selection.request(second.address)
                    callback.onConnectionStateChanged(second, BluetoothProfile.STATE_CONNECTED)
                    clear()
                }
                transport.send(InputEvent.KeyTap(5))
                Thread.sleep(250)
                val keys = snapshot().filter { it.first == HidProfiles.A_LINKPAD.keyboardReportId }
                    .map { it.second[2].toInt() and 255 }.filter { it != 0 }
                check(keys == listOf(5)) { "Old host text leaked to new host: $keys" }
            }
            checkCase("late_old_disconnect_cannot_clear_new_connection") {
                onExecutor { callback.onConnectionStateChanged(first, BluetoothProfile.STATE_DISCONNECTED) }
                check((transport.state.value as? TransportState.Connected)?.address == second.address)
            }
            checkCase("explicit_disconnect_rejects_late_connected_callback") {
                transport.disconnect()
                onExecutor { callback.onConnectionStateChanged(second, BluetoothProfile.STATE_CONNECTED) }
                check(transport.state.value !is TransportState.Connected)
            }
            checkCase("offline_send_and_drag_lock_do_not_claim_success") {
                instrumentation.runOnMainSync {
                    service.setDragLocked(true)
                    check(!service.dragLocked.value)
                    check(!service.typeText("not sent"))
                }
            }
            checkCase("offline_pointer_data_is_not_replayed_on_reconnect") {
                clear()
                transport.send(InputEvent.PointerMove(80, 90))
                transport.send(InputEvent.Scroll(10, 11))
                onExecutor {
                    selection.request(second.address)
                    callback.onConnectionStateChanged(second, BluetoothProfile.STATE_CONNECTED)
                }
                transport.send(InputEvent.PointerButtons(0))
                onExecutor { }
                val mouse = snapshot().filter { it.first == HidProfiles.A_LINKPAD.mouseReportId }.map { it.second }
                check(mouse.all { bytes -> bytes.drop(1).all { it == 0.toByte() } })
            }
        } finally {
            transport.stop()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
        check(reports.isNotEmpty()) { "No report callbacks exercised" }
        return notes
    }

    private fun field(instance: Any, name: String): Any =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)!!
}
