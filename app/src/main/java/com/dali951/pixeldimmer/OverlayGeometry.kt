package com.dali951.pixeldimmer

import kotlin.math.roundToInt

data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class OverlayLayout(
    val dotRect: PixelRect?,
    val lineRect: PixelRect?
)

object OverlayGeometry {
    const val ROT_0 = 0
    const val ROT_90 = 1
    const val ROT_180 = 2
    const val ROT_270 = 3

    fun computeOverlayLayout(
        config: MaskConfig,
        currentPortraitW: Int,
        currentPortraitH: Int,
        rotation: Int
    ): OverlayLayout {
        if (currentPortraitW <= 0 || currentPortraitH <= 0) {
            return OverlayLayout(null, null)
        }

        val calW = config.calibrationWidth.coerceAtLeast(1)
        val calH = config.calibrationHeight.coerceAtLeast(1)
        val sameResolution = (calW == currentPortraitW && calH == currentPortraitH)

        val pxDotX = if (sameResolution) {
            config.dotX.coerceIn(0, currentPortraitW)
        } else {
            ((config.dotX.toDouble() * currentPortraitW) / calW).roundToInt().coerceIn(0, currentPortraitW)
        }

        val pxDotY = if (sameResolution) {
            config.dotY.coerceIn(0, currentPortraitH)
        } else {
            ((config.dotY.toDouble() * currentPortraitH) / calH).roundToInt().coerceIn(0, currentPortraitH)
        }

        val pxLineY = if (sameResolution) {
            config.lineY.coerceIn(0, currentPortraitH)
        } else {
            ((config.lineY.toDouble() * currentPortraitH) / calH).roundToInt().coerceIn(0, currentPortraitH)
        }

        val scaledDotSize = if (sameResolution) {
            config.dotSize
        } else {
            ((config.dotSize.toDouble() * currentPortraitW) / calW).roundToInt()
        }.coerceIn(MaskConfig.MIN_DOT_SIZE, MaskConfig.MAX_DOT_SIZE)

        val scaledLineHeight = if (sameResolution) {
            config.lineHeight
        } else {
            ((config.lineHeight.toDouble() * currentPortraitH) / calH).roundToInt()
        }.coerceIn(MaskConfig.MIN_LINE_HEIGHT, MaskConfig.MAX_LINE_HEIGHT)

        val dotRect = if (config.mode == MaskMode.DOT || config.mode == MaskMode.BOTH) {
            val (mappedX, mappedY) = mapPortraitPoint(
                px = pxDotX,
                py = pxDotY,
                portraitW = currentPortraitW,
                portraitH = currentPortraitH,
                rotation = rotation
            )
            PixelRect(
                x = mappedX - scaledDotSize / 2,
                y = mappedY - scaledDotSize / 2,
                width = scaledDotSize,
                height = scaledDotSize
            )
        } else {
            null
        }

        val lineRect = if (config.mode == MaskMode.LINE || config.mode == MaskMode.BOTH) {
            when (rotation) {
                ROT_90 -> PixelRect(
                    x = pxLineY - scaledLineHeight / 2,
                    y = 0,
                    width = scaledLineHeight,
                    height = currentPortraitW
                )
                ROT_180 -> PixelRect(
                    x = 0,
                    y = (currentPortraitH - pxLineY) - scaledLineHeight / 2,
                    width = currentPortraitW,
                    height = scaledLineHeight
                )
                ROT_270 -> PixelRect(
                    x = (currentPortraitH - pxLineY) - scaledLineHeight / 2,
                    y = 0,
                    width = scaledLineHeight,
                    height = currentPortraitW
                )
                else -> PixelRect(
                    x = 0,
                    y = pxLineY - scaledLineHeight / 2,
                    width = currentPortraitW,
                    height = scaledLineHeight
                )
            }
        } else {
            null
        }

        return OverlayLayout(dotRect = dotRect, lineRect = lineRect)
    }

    fun mapPortraitPoint(
        px: Int,
        py: Int,
        portraitW: Int,
        portraitH: Int,
        rotation: Int
    ): Pair<Int, Int> {
        return when (rotation) {
            ROT_90 -> py to (portraitW - px)
            ROT_180 -> (portraitW - px) to (portraitH - py)
            ROT_270 -> (portraitH - py) to px
            else -> px to py
        }
    }
}

object VersionComparator {
    fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        if (cleanCurrent == cleanRemote || cleanRemote.isEmpty()) return false

        val currParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }
        val remParts = cleanRemote.split(".", "-").mapNotNull { it.toIntOrNull() }
        if (currParts.isEmpty() || remParts.isEmpty()) return false

        val maxLen = maxOf(currParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
