package com.perdonus.ruclaw.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.perdonus.ruclaw.android.core.util.AppDiagnostics

class SessionKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        acquireLocks()
        AppDiagnostics.log("KeepAlive service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(extraTitle).orEmpty().ifBlank { "RuClaw подключен" }
        val message = intent?.getStringExtra(extraMessage).orEmpty()
            .ifBlank { "Держу launcher и websocket активными" }
        startForeground(notificationId, buildNotification(title, message))
        AppDiagnostics.log("KeepAlive service updated: $message")
        return START_STICKY
    }

    override fun onDestroy() {
        AppDiagnostics.log("KeepAlive service stopped")
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, message: String) =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            channelId,
            "RuClaw Keep Alive",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Удерживает подключение RuClaw активным в фоне"
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ruclaw-keepalive").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:ruclaw-keepalive")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        AppDiagnostics.log("KeepAlive locks acquired")
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        wifiLock = null
        wakeLock = null
        AppDiagnostics.log("KeepAlive locks released")
    }

    companion object {
        private const val channelId = "ruclaw_keep_alive"
        private const val notificationId = 42021
        private const val extraTitle = "title"
        private const val extraMessage = "message"

        fun sync(context: Context, active: Boolean, title: String, message: String) {
            val intent = Intent(context, SessionKeepAliveService::class.java).apply {
                putExtra(extraTitle, title)
                putExtra(extraMessage, message)
            }
            if (active) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
