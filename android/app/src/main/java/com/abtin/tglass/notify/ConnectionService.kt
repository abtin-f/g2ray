package com.abtin.tglass.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abtin.tglass.R
import com.abtin.tglass.data.td.Td

/**
 * Keeps the process (and so TDLib's connection) alive while the app is in the background, so new
 * messages arrive as notifications — the approach FOSS Telegram clients use instead of Google push.
 */
class ConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        com.abtin.tglass.core.CrashReports.install(this)
        Notifier.ensureChannels(this)
        val notification = NotificationCompat.Builder(this, Notifier.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("TGlass is connected")
            .setContentText("New messages will arrive as notifications")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(Notifier.openChatIntent(this, 0L))
            .build()
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            Build.VERSION.SDK_INT >= 29 -> startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(ID, notification)
        }
        Td.get(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ID = 7_777

        /** True while the background connection is up (shown in Settings → Notifications). */
        @Volatile var running = false
            private set

        private fun enabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("tglass", Context.MODE_PRIVATE)
            return prefs.getBoolean("loggedIn", false) && !prefs.getBoolean("demoMode", false) && prefs.getBoolean("bgConnection", true)
        }

        /** Starts or stops the service according to the current settings. */
        fun sync(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            if (enabled(context)) runCatching { ContextCompat.startForegroundService(context, intent) }
            else context.stopService(intent)
        }
    }
}

/** Restores the background connection after a reboot or app update. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ConnectionService.sync(context)
    }
}
