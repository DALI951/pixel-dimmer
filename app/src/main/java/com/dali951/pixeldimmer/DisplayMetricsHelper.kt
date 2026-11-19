package com.dali951.pixeldimmer

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager

object DisplayMetricsHelper {

    fun getPortraitSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            val w = bounds.width().coerceAtLeast(1)
            val h = bounds.height().coerceAtLeast(1)
            minOf(w, h) to maxOf(w, h)
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            val w = metrics.widthPixels.coerceAtLeast(1)
            val h = metrics.heightPixels.coerceAtLeast(1)
            minOf(w, h) to maxOf(w, h)
        }
    }

    fun getRotation(context: Context): Int {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            return display.rotation
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}
