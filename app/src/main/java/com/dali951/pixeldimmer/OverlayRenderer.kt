package com.dali951.pixeldimmer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayRenderer(
    private val context: Context,
    private val windowType: Int
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var dotView: BlackDotView? = null
    private var lineView: View? = null
    private var latestConfig: MaskConfig? = null
    private var listenerRegistered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                mainHandler.post {
                    latestConfig?.let { render(it) }
                }
            }
        }
    }

    fun registerDisplayListener() {
        if (!listenerRegistered) {
            displayManager?.registerDisplayListener(displayListener, mainHandler)
            listenerRegistered = true
        }
    }

    fun unregisterDisplayListener() {
        if (listenerRegistered) {
            displayManager?.unregisterDisplayListener(displayListener)
            listenerRegistered = false
        }
    }

    fun render(config: MaskConfig) {
        latestConfig = config
        if (!config.enabled) {
            clear()
            return
        }

        val (portraitW, portraitH) = DisplayMetricsHelper.getPortraitSize(context)
        val rotation = DisplayMetricsHelper.getRotation(context)
        val layout = OverlayGeometry.computeOverlayLayout(
            config = config,
            currentPortraitW = portraitW,
            currentPortraitH = portraitH,
            rotation = rotation
        )

        syncDotView(layout.dotRect)
        syncLineView(layout.lineRect)
    }

    fun clear() {
        latestConfig = null
        removeDotView()
        removeLineView()
    }

    private fun syncDotView(rect: PixelRect?) {
        if (rect == null) {
            removeDotView()
            return
        }
        val params = buildLayoutParams(rect)
        val existing = dotView
        if (existing == null) {
            val view = BlackDotView(context)
            try {
                windowManager.addView(view, params)
                dotView = view
            } catch (_: Exception) {
                dotView = null
            }
        } else {
            try {
                windowManager.updateViewLayout(existing, params)
                existing.invalidate()
            } catch (_: Exception) {
                removeDotView()
            }
        }
    }

    private fun syncLineView(rect: PixelRect?) {
        if (rect == null) {
            removeLineView()
            return
        }
        val params = buildLayoutParams(rect)
        val existing = lineView
        if (existing == null) {
            val view = View(context).apply {
                setBackgroundColor(Color.BLACK)
            }
            try {
                windowManager.addView(view, params)
                lineView = view
            } catch (_: Exception) {
                lineView = null
            }
        } else {
            try {
                windowManager.updateViewLayout(existing, params)
            } catch (_: Exception) {
                removeLineView()
            }
        }
    }

    private fun removeDotView() {
        dotView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
            dotView = null
        }
    }

    private fun removeLineView() {
        lineView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
            lineView = null
        }
    }

    private fun buildLayoutParams(rect: PixelRect): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        return WindowManager.LayoutParams(
            rect.width.coerceAtLeast(1),
            rect.height.coerceAtLeast(1),
            rect.x,
            rect.y,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private class BlackDotView(context: Context) : View(context) {
        private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawOval(0f, 0f, width.toFloat(), height.toFloat(), blackPaint)
        }
    }
}
