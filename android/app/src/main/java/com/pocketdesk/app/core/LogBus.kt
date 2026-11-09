package com.pocketdesk.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Small in-memory + on-disk diagnostics log.
 *
 * Deliberately never records typed text: only event kinds and counters, so a log
 * can be handed over for debugging without leaking what the user typed.
 */
object LogBus {

    private const val CAPACITY = 400

    /** Rotate at half a megabyte; a day of use writes a few thousand lines. */
    private const val MAX_BYTES = 512 * 1024

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null
    // Drop new disk copies under sustained pressure; memory and logcat stay immediate.
    private val writer = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(CAPACITY),
        { task -> Thread(task, "PocketDesk-log-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy()
    )

    @Synchronized
    fun init(logDir: File) {
        if (file != null) return
        runCatching {
            logDir.mkdirs()
            file = File(logDir, "pocketdesk.log")
        }
    }

    @Synchronized
    fun append(message: String) {
        // The monitor protects the formatter, memory, and disk submission order.
        val line = "${stamp.format(Date())}  $message"
        _lines.value = (_lines.value + line).takeLast(CAPACITY)
        file?.let { target ->
            writer.execute {
                runCatching {
                    rotateIfNeeded(target)
                    target.appendText(line + "\n")
                }
            }
        }
        // Mirror to logcat so a host with adb can read the same diagnostics:
        //   adb logcat -s PocketDesk
        android.util.Log.i("PocketDesk", message)
    }

    /** Keep the live file small: a few thousand lines a day would otherwise pile up. */
    private fun rotateIfNeeded(target: File) {
        if (target.length() < MAX_BYTES) return
        val previous = File(target.parentFile, target.name + ".1")
        runCatching { previous.delete() }
        runCatching { target.renameTo(previous) }
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        // Reserve space for clear; an in-flight write finishes before it runs.
        writer.queue.clear()
        file?.let { target ->
            writer.execute { runCatching { target.writeText("") } }
        }
    }

    @Synchronized
    fun exportText(): String = _lines.value.joinToString("\n")

    @Synchronized
    fun filePath(): String? = file?.absolutePath
}
