package com.dali951.pixeldimmer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

object OverlayController {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var stateListener: (() -> Unit)? = null

    fun setStateListener(listener: (() -> Unit)?) {
        stateListener = listener
    }

    fun notifyStateChanged() {
        val listener = stateListener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.invoke()
        } else {
            mainHandler.post { stateListener?.invoke() }
        }
    }

    fun sync(context: Context) {
        val appContext = context.applicationContext
        val config = MaskPreferences.load(appContext)
        val a11y = PixelMaskAccessibilityService.instance

        if (a11y != null) {
            a11y.refreshOverlay(config)
            stopStandardService(appContext)
        } else if (config.enabled && Settings.canDrawOverlays(appContext)) {
            val runningService = PixelMaskOverlayService.instance
            if (runningService != null) {
                runningService.applyConfig(config)
            } else {
                val intent = Intent(appContext, PixelMaskOverlayService::class.java)
                try {
                    ContextCompat.startForegroundService(appContext, intent)
                } catch (_: Exception) {
                }
            }
        } else {
            stopStandardService(appContext)
        }

        requestTileSync(appContext)
        notifyStateChanged()
    }

    fun isOverlayActivelyDrawing(context: Context): Boolean {
        val config = MaskPreferences.load(context)
        if (!config.enabled) return false
        return PixelMaskAccessibilityService.isRunning ||
            (PixelMaskOverlayService.isRunning && Settings.canDrawOverlays(context))
    }

    private fun stopStandardService(context: Context) {
        try {
            context.stopService(Intent(context, PixelMaskOverlayService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun requestTileSync(context: Context) {
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, PixelMaskTileService::class.java)
            )
        } catch (_: Exception) {
        }
    }
}
