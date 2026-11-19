package com.dali951.pixeldimmer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelDimmerLogicTest {

    @Test
    fun dotModeLeavesLineNullAndAllowsNegativeEdgeOrigin() {
        val cfg = MaskConfig(
            enabled = true,
            mode = MaskMode.DOT,
            dotX = 2,
            dotY = 900,
            dotSize = 32,
            calibrationWidth = 1440,
            calibrationHeight = 3088
        )

        val layout = OverlayGeometry.computeOverlayLayout(
            config = cfg,
            currentPortraitW = 1440,
            currentPortraitH = 3088,
            rotation = OverlayGeometry.ROT_0
        )

        assertNotNull(layout.dotRect)
        assertNull(layout.lineRect)
        assertEquals(PixelRect(x = -14, y = 884, width = 32, height = 32), layout.dotRect)
    }

    @Test
    fun lineModeLeavesDotNullAndSpansFullWidthInPortrait() {
        val cfg = MaskConfig(
            enabled = true,
            mode = MaskMode.LINE,
            lineY = 1000,
            lineHeight = 4,
            calibrationWidth = 1440,
            calibrationHeight = 3088
        )

        val layout = OverlayGeometry.computeOverlayLayout(
            config = cfg,
            currentPortraitW = 1440,
            currentPortraitH = 3088,
            rotation = OverlayGeometry.ROT_0
        )

        assertNull(layout.dotRect)
        assertEquals(PixelRect(x = 0, y = 998, width = 1440, height = 4), layout.lineRect)
    }

    @Test
    fun bothModeMapsAcrossAllFourRotations() {
        val w = 1440
        val h = 3088
        val cfg = MaskConfig(
            enabled = true,
            mode = MaskMode.BOTH,
            dotX = 20,
            dotY = 800,
            dotSize = 40,
            lineY = 800,
            lineHeight = 6,
            calibrationWidth = w,
            calibrationHeight = h
        )

        val rot0 = OverlayGeometry.computeOverlayLayout(cfg, w, h, OverlayGeometry.ROT_0)
        assertEquals(PixelRect(0, 780, 40, 40), rot0.dotRect)
        assertEquals(PixelRect(0, 797, w, 6), rot0.lineRect)

        val rot90 = OverlayGeometry.computeOverlayLayout(cfg, w, h, OverlayGeometry.ROT_90)
        // ROT_90: (px, py) -> (py, W - px) = (800, 1420)
        assertEquals(PixelRect(780, 1400, 40, 40), rot90.dotRect)
        assertEquals(PixelRect(797, 0, 6, w), rot90.lineRect)

        val rot180 = OverlayGeometry.computeOverlayLayout(cfg, w, h, OverlayGeometry.ROT_180)
        // ROT_180: (W - px, H - py) = (1420, 2288)
        assertEquals(PixelRect(1400, 2268, 40, 40), rot180.dotRect)
        assertEquals(PixelRect(0, 2285, w, 6), rot180.lineRect)

        val rot270 = OverlayGeometry.computeOverlayLayout(cfg, w, h, OverlayGeometry.ROT_270)
        // ROT_270: (H - py, px) = (2288, 20)
        assertEquals(PixelRect(2268, 0, 40, 40), rot270.dotRect)
        assertEquals(PixelRect(2285, 0, 6, w), rot270.lineRect)
    }

    @Test
    fun resolutionScalingPreservesProportionalLocation() {
        val cfg = MaskConfig(
            enabled = true,
            mode = MaskMode.BOTH,
            dotX = 20,
            dotY = 1000,
            dotSize = 40,
            lineY = 1000,
            lineHeight = 4,
            calibrationWidth = 1440,
            calibrationHeight = 3088
        )

        val scaled = OverlayGeometry.computeOverlayLayout(
            config = cfg,
            currentPortraitW = 1080,
            currentPortraitH = 2316,
            rotation = OverlayGeometry.ROT_0
        )

        // 20 * 1080 / 1440 = 15, dotSize = 30 -> x = 15 - 15 = 0
        // 1000 * 2316 / 3088 = 750 -> y = 750 - 15 = 735
        assertEquals(PixelRect(0, 735, 30, 30), scaled.dotRect)
        // lineHeight = 4 * 2316 / 3088 = 3 -> y = 750 - 1 = 749
        assertEquals(PixelRect(0, 749, 1080, 3), scaled.lineRect)
    }

    @Test
    fun versionComparatorHandlesMultiDigitAndEqualVersions() {
        assertTrue(VersionComparator.isRemoteVersionNewer("1.0.1", "v1.0.2"))
        assertTrue(VersionComparator.isRemoteVersionNewer("1.0.2", "v1.0.10"))
        assertTrue(VersionComparator.isRemoteVersionNewer("1.0.9", "1.1.0"))
        assertFalse(VersionComparator.isRemoteVersionNewer("1.0.1", "v1.0.1"))
        assertFalse(VersionComparator.isRemoteVersionNewer("1.0.10", "v1.0.2"))
        assertFalse(VersionComparator.isRemoteVersionNewer("1.0.1", ""))
        assertFalse(VersionComparator.isRemoteVersionNewer("1.0.1", "invalid-tag"))
    }
}
