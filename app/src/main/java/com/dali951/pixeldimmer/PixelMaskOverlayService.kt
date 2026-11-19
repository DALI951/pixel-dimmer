package com.dali951.pixeldimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class PixelMaskOverlayService : Service() {

    private var renderer: OverlayRenderer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        renderer = OverlayRenderer(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).apply {
            registerDisplayListener()
        }
        OverlayController.notifyStateChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = MaskPreferences.load(this)
        if (!config.enabled || !Settings.canDrawOverlays(this) || PixelMaskAccessibilityService.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        renderer?.render(config)
        OverlayController.notifyStateChanged()
        return START_STICKY
    }

    fun applyConfig(config: MaskConfig) {
        if (!config.enabled || !Settings.canDrawOverlays(this) || PixelMaskAccessibilityService.isRunning) {
            stopSelf()
            return
        }
        renderer?.render(config)
    }

    override fun onDestroy() {
        renderer?.unregisterDisplayListener()
        renderer?.clear()
        renderer = null
        instance = null
        OverlayController.notifyStateChanged()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pixel Dimmer Overlay",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                description = "Keeps the pure-black pixel mask active across apps"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_mask)
            .setContentTitle("Pixel Dimmer active")
            .setContentText("Tap to adjust dot or line mask")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pixel_dimmer_overlay"
        private const val NOTIF_ID = 1001

        @Volatile
        var instance: PixelMaskOverlayService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
