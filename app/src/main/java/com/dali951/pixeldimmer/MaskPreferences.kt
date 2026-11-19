package com.dali951.pixeldimmer

import android.content.Context

object MaskPreferences {
    private const val PREFS_NAME = "pixel_dimmer_prefs"

    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"
    private const val KEY_DOT_X = "dot_x"
    private const val KEY_DOT_Y = "dot_y"
    private const val KEY_DOT_SIZE = "dot_size"
    private const val KEY_LINE_Y = "line_y"
    private const val KEY_LINE_HEIGHT = "line_height"
    private const val KEY_CAL_WIDTH = "cal_width"
    private const val KEY_CAL_HEIGHT = "cal_height"
    private const val KEY_FINE_MODE = "fine_mode"

    fun load(context: Context): MaskConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (portraitW, portraitH) = DisplayMetricsHelper.getPortraitSize(context)

        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val defaultY = (portraitH * 0.35f).toInt().coerceIn(0, portraitH)
            val initial = MaskConfig(
                enabled = false,
                mode = MaskMode.DOT,
                dotX = 18.coerceIn(0, portraitW),
                dotY = defaultY,
                dotSize = 36,
                lineY = defaultY,
                lineHeight = 3,
                calibrationWidth = portraitW,
                calibrationHeight = portraitH,
                fineMode = true
            )
            save(context, initial)
            return initial
        }

        val calW = prefs.getInt(KEY_CAL_WIDTH, portraitW).coerceAtLeast(1)
        val calH = prefs.getInt(KEY_CAL_HEIGHT, portraitH).coerceAtLeast(1)

        return MaskConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            mode = MaskMode.fromString(prefs.getString(KEY_MODE, MaskMode.DOT.name)),
            dotX = prefs.getInt(KEY_DOT_X, 18).coerceIn(0, calW),
            dotY = prefs.getInt(KEY_DOT_Y, (calH * 0.35f).toInt()).coerceIn(0, calH),
            dotSize = prefs.getInt(KEY_DOT_SIZE, 36).coerceIn(MaskConfig.MIN_DOT_SIZE, MaskConfig.MAX_DOT_SIZE),
            lineY = prefs.getInt(KEY_LINE_Y, (calH * 0.35f).toInt()).coerceIn(0, calH),
            lineHeight = prefs.getInt(KEY_LINE_HEIGHT, 3).coerceIn(MaskConfig.MIN_LINE_HEIGHT, MaskConfig.MAX_LINE_HEIGHT),
            calibrationWidth = calW,
            calibrationHeight = calH,
            fineMode = prefs.getBoolean(KEY_FINE_MODE, true)
        )
    }

    fun save(context: Context, config: MaskConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_MODE, config.mode.name)
            .putInt(KEY_DOT_X, config.dotX)
            .putInt(KEY_DOT_Y, config.dotY)
            .putInt(KEY_DOT_SIZE, config.dotSize.coerceIn(MaskConfig.MIN_DOT_SIZE, MaskConfig.MAX_DOT_SIZE))
            .putInt(KEY_LINE_Y, config.lineY)
            .putInt(KEY_LINE_HEIGHT, config.lineHeight.coerceIn(MaskConfig.MIN_LINE_HEIGHT, MaskConfig.MAX_LINE_HEIGHT))
            .putInt(KEY_CAL_WIDTH, config.calibrationWidth.coerceAtLeast(1))
            .putInt(KEY_CAL_HEIGHT, config.calibrationHeight.coerceAtLeast(1))
            .putBoolean(KEY_FINE_MODE, config.fineMode)
            .apply()
    }
}
