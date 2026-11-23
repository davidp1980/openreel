package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFitTest {
    @Test
    fun fitLeavesVideoUntouched() {
        val t = VideoFit.FIT.computeTransform(1920, 1080, 1f, detectedBarScale = 1.33f)
        assertFalse(t.useFill)
        assertFalse(t.useZoom)
        assertEquals(1f, t.scaleX, 0.001f)
        assertEquals(1f, t.scaleY, 0.001f)
    }

    @Test
    fun zoomAndStretchUseNativeResizeModesForNon16x9Video() {
        val zoom = VideoFit.ZOOM.computeTransform(1920, 800, 1f, detectedBarScale = null)
        assertTrue(zoom.useZoom)
        assertFalse(zoom.useFill)
        assertEquals(1f, zoom.scaleX, 0.001f)
        assertEquals(1f, zoom.scaleY, 0.001f)

        val stretch = VideoFit.STRETCH.computeTransform(1920, 800, 1f, detectedBarScale = null)
        assertTrue(stretch.useFill)
        assertFalse(stretch.useZoom)
        assertEquals(1f, stretch.scaleX, 0.001f)
        assertEquals(1f, stretch.scaleY, 0.001f)
    }

    @Test
    fun zoomAndStretchUseDetectedScaleFor16x9WithHardcodedBars() {
        val zoom = VideoFit.ZOOM.computeTransform(1920, 1080, 1f, detectedBarScale = 1.31f)
        assertFalse(zoom.useZoom)
        assertFalse(zoom.useFill)
        assertEquals(1.31f, zoom.scaleX, 0.001f)
        assertEquals(1.31f, zoom.scaleY, 0.001f)

        val stretch = VideoFit.STRETCH.computeTransform(1920, 1080, 1f, detectedBarScale = 1.31f)
        assertTrue(stretch.useFill)
        assertFalse(stretch.useZoom)
        assertEquals(1f, stretch.scaleX, 0.001f)
        assertEquals(1.31f, stretch.scaleY, 0.001f)
    }

    @Test
    fun zoomAndStretchFallbackToDefaultLetterboxScaleWhenDetectionMissingOrInvalid() {
        val fallbackZoom = VideoFit.ZOOM.computeTransform(3840, 2160, 1f, detectedBarScale = null)
        assertEquals(VideoFit.DEFAULT_LETTERBOX_SCALE, fallbackZoom.scaleX, 0.001f)
        assertEquals(VideoFit.DEFAULT_LETTERBOX_SCALE, fallbackZoom.scaleY, 0.001f)

        val outOfRangeStretch = VideoFit.STRETCH.computeTransform(1920, 1080, 1f, detectedBarScale = 1.0f)
        assertTrue(outOfRangeStretch.useFill)
        assertEquals(1f, outOfRangeStretch.scaleX, 0.001f)
        assertEquals(VideoFit.DEFAULT_LETTERBOX_SCALE, outOfRangeStretch.scaleY, 0.001f)
    }

    @Test
    fun unknownVideoSizeFallsBackToNativeModes() {
        val zoom = VideoFit.ZOOM.computeTransform(0, 0, 1f, detectedBarScale = null)
        assertTrue(zoom.useZoom)
        assertEquals(1f, zoom.scaleX, 0.001f)

        val stretch = VideoFit.STRETCH.computeTransform(0, 0, 1f, detectedBarScale = null)
        assertTrue(stretch.useFill)
        assertEquals(1f, stretch.scaleY, 0.001f)
    }
}
