package com.dali951.pixeldimmer

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PixelMaskTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val config = MaskPreferences.load(this)
        val hasPermission = PixelMaskAccessibilityService.isRunning || Settings.canDrawOverlays(this)

        if (!hasPermission) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val updated = config.copy(enabled = !config.enabled)
        MaskPreferences.save(this, updated)
        OverlayController.sync(this)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val config = MaskPreferences.load(this)
        val hasPermission = PixelMaskAccessibilityService.isRunning || Settings.canDrawOverlays(this)
        tile.state = when {
            !hasPermission -> Tile.STATE_INACTIVE
            config.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "Pixel Dimmer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (config.enabled && hasPermission) config.mode.label else "Off"
        }
        tile.updateTile()
    }
}
