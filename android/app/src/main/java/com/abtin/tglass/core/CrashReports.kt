package com.abtin.tglass.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.abtin.tglass.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last crash so the next launch can offer to copy it (there is no crash
 * service, and the owner tests on their own phone without adb).
 */
object CrashReports {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false
    @Volatile private var watching = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(
                    buildString {
                        appendLine("TGlass ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " on thread " + thread.name)
                        appendLine()
                        append(trace.take(12_000))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Debug builds only: logs the main thread's stack (tag TGlassWatchdog) whenever it is blocked for over 2 s,
     * so the CI emulator run shows where a freeze / ANR happens.
     */
    fun startWatchdog() {
        if (!BuildConfig.DEBUG || watching) return
        watching = true
        val main = android.os.Looper.getMainLooper()
        val handler = android.os.Handler(main)
        Thread({
            while (true) {
                val tick = java.util.concurrent.atomic.AtomicBoolean(false)
                handler.post { tick.set(true) }
                Thread.sleep(1000)
                if (!tick.get()) {
                    // Sample the blocked main thread every 100 ms; log the hottest app frames once it is free again.
                    val counts = HashMap<String, Int>()
                    var samples = 0
                    val started = android.os.SystemClock.uptimeMillis()
                    while (!tick.get()) {
                        samples++
                        main.thread.stackTrace
                            .filter { it.className.startsWith("com.abtin") }
                            .map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                            .distinct()
                            .forEach { counts[it] = (counts[it] ?: 0) + 1 }
                        Thread.sleep(100)
                        if (samples % 30 == 0) android.util.Log.w(
                            "TGlassWatchdog",
                            "still blocked, $samples samples:\n" + counts.entries.sortedByDescending { it.value }.take(25).joinToString("\n") { "  ${it.value}x ${it.key}" },
                        )
                    }
                    val top = counts.entries.sortedByDescending { it.value }.take(40)
                    android.util.Log.w(
                        "TGlassWatchdog",
                        "main thread blocked ${android.os.SystemClock.uptimeMillis() - started + 1000} ms, $samples samples:\n" +
                            top.joinToString("\n") { "  ${it.value}x ${it.key}" },
                    )
                }
            }
        }, "tglass-watchdog").apply { isDaemon = true }.start()
    }

    /** The saved report of the last crash, or null; reading it removes it. */
    fun take(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text?.takeIf { it.isNotBlank() }
    }

    fun copy(context: Context, report: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("TGlass crash report", report))
    }
}
