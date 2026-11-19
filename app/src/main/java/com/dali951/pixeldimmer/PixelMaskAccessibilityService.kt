package com.dali951.pixeldimmer

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class PixelMaskAccessibilityService : AccessibilityService() {

    private var renderer: OverlayRenderer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        renderer = OverlayRenderer(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).apply {
            registerDisplayListener()
        }
        OverlayController.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun refreshOverlay(config: MaskConfig) {
        renderer?.render(config)
    }

    override fun onDestroy() {
        renderer?.unregisterDisplayListener()
        renderer?.clear()
        renderer = null
        instance = null
        OverlayController.sync(this)
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PixelMaskAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
