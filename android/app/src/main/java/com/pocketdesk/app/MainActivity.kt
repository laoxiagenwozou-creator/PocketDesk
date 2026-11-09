package com.pocketdesk.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.pocketdesk.app.core.LogBus
import com.pocketdesk.app.service.HidService
import com.pocketdesk.app.ui.PocketDeskApp

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<HidService?>(null)
    private var bound = false
    private var started = false

    private fun message(chinese: String, english: String): String =
        if (service?.englishUi?.value == true) english else chinese

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasBluetoothPermission()) startAndBind()
            else Toast.makeText(this, message("需要允许附近设备权限才能连接键鼠", "Allow nearby devices permission to connect."), Toast.LENGTH_LONG).show()
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? HidService.LocalBinder
            service = local?.service()
            service?.setAimForeground(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            if (service == null) LogBus.append("绑定 HidService 失败：binder 类型不符")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PocketDeskApp(
                service = service,
                onOpenBluetoothSettings = ::openBluetoothSettings,
                onMakeDiscoverable = ::makeDiscoverable,
                onShareLog = ::shareLog,
            )
        }
        requestPermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        if (!bound && hasBluetoothPermission()) startAndBind()
    }

    override fun onResume() {
        super.onResume()
        service?.setAimForeground(true)
    }

    override fun onPause() {
        service?.setAimForeground(false)
        super.onPause()
    }

    override fun onStop() {
        service?.releasePointerInput()
        super.onStop()
        if (bound) {
            runCatching { unbindService(connection) }
            bound = false
        }
        // Keep the service reference: it is started as well as bound, so it outlives
        // the unbind, and clearing it here made the whole UI flash
        // "正在启动输入服务…" every time the user went home. onServiceDisconnected
        // is what clears it.
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startAndBind() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun startAndBind() {
        if (!hasBluetoothPermission()) return
        val intent = Intent(this, HidService::class.java)
        if (!started) {
            runCatching { ContextCompat.startForegroundService(this, intent) }
                .onSuccess { started = true }
                .onFailure {
                    LogBus.append("启动前台服务失败: ${it.message}")
                    Toast.makeText(this, message("启动输入服务失败：${it.message}", "Failed to start input service: ${it.message}"), Toast.LENGTH_LONG).show()
                }
        }
        if (!bound) {
            bound = runCatching { bindService(intent, connection, Context.BIND_AUTO_CREATE) }
                .getOrDefault(false)
        }
    }

    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, message("无法打开蓝牙设置", "Cannot open Bluetooth settings"), Toast.LENGTH_SHORT).show() }
    }

    private fun makeDiscoverable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionsIfNeeded()
            return
        }
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        // Windows search lists the Bluetooth name, not the app name. The phone was
        // broadcasting as "xiao", the same name as the PC, so the PC skipped it.
        runCatching { manager?.adapter?.name = "PocketDesk" }
            .onFailure { LogBus.append("改蓝牙名失败: ${it.message}") }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        runCatching { discoverableLauncher.launch(intent) }
            .onFailure {
                LogBus.append("可发现请求失败，改为打开蓝牙设置: ${it.message}")
                openBluetoothSettings()
            }
    }

    private fun shareLog(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, message("日志为空", "The log is empty"), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, message("PocketDesk 诊断日志", "PocketDesk diagnostic log"))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(intent, message("分享日志", "Share log"))) }
    }
}
