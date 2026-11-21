package com.shieldstream.tv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScrollTest {

    @Test
    fun `initialHomeZone lands on hero when featured content exists`() {
        assertEquals(
            HomeFocusZone.HERO,
            initialHomeZone(hasFeatured = true),
        )
        assertEquals(
            HomeFocusZone.ROWS,
            initialHomeZone(hasFeatured = false),
        )
    }

    @Test
    fun `homeScrollVisuals crossfades billboard and header and delays row clipping`() {
        val distance = 1_000f

        val atTop = homeScrollVisuals(scrollPx = 0f, transitionDistancePx = distance)
        assertEquals(1f, atTop.billboardAlpha, 0.0001f)
        assertEquals(0f, atTop.heroInfoAlpha, 0.0001f)
        assertFalse(atTop.clipRowsAtHeader)

        val billboardHidden = homeScrollVisuals(scrollPx = 720f, transitionDistancePx = distance)
        assertEquals(0f, billboardHidden.billboardAlpha, 0.0001f)
        assertEquals((0.72f - 0.55f) / 0.35f, billboardHidden.heroInfoAlpha, 0.0001f)
        assertFalse(billboardHidden.clipRowsAtHeader)

        val clipEnabled = homeScrollVisuals(scrollPx = 750f, transitionDistancePx = distance)
        assertEquals(0f, clipEnabled.billboardAlpha, 0.0001f)
        assertTrue(clipEnabled.heroInfoAlpha > 0f)
        assertTrue(clipEnabled.clipRowsAtHeader)

        val headerReady = homeScrollVisuals(scrollPx = 900f, transitionDistancePx = distance)
        assertEquals(0f, headerReady.billboardAlpha, 0.0001f)
        assertEquals(1f, headerReady.heroInfoAlpha, 0.0001f)
        assertTrue(headerReady.clipRowsAtHeader)

        val atBottom = homeScrollVisuals(scrollPx = 1_000f, transitionDistancePx = distance)
        assertEquals(0f, atBottom.billboardAlpha, 0.0001f)
        assertEquals(1f, atBottom.heroInfoAlpha, 0.0001f)
        assertTrue(atBottom.clipRowsAtHeader)

        val nonPositiveDistance = homeScrollVisuals(scrollPx = 0f, transitionDistancePx = 0f)
        assertEquals(0f, nonPositiveDistance.billboardAlpha, 0.0001f)
        assertEquals(1f, nonPositiveDistance.heroInfoAlpha, 0.0001f)
        assertTrue(nonPositiveDistance.clipRowsAtHeader)
    }

    @Test
    fun `homeBringIntoViewDistance aligns hero and rows cleanly`() {
        val containerSize = 1080f
        val rowCardPivotPx = 364f

        // HERO aligns its top to 0.
        assertEquals(
            0f,
            homeBringIntoViewDistance(HomeFocusZone.HERO, offset = 0f, size = containerSize, containerSize = containerSize, rowCardPivotPx = rowCardPivotPx),
            0.0001f,
        )
        assertEquals(
            -824f,
            homeBringIntoViewDistance(HomeFocusZone.HERO, offset = -824f, size = containerSize, containerSize = containerSize, rowCardPivotPx = rowCardPivotPx),
            0.0001f,
        )

        // ROWS aligns cards to rowCardPivotPx, and clamps tall children to containerSize - childSize.
        assertEquals(
            600f - rowCardPivotPx,
            homeBringIntoViewDistance(HomeFocusZone.ROWS, offset = 600f, size = 400f, containerSize = containerSize, rowCardPivotPx = rowCardPivotPx),
            0.0001f,
        )
        val tallChildSize = 800f
        assertEquals(
            300f - (containerSize - tallChildSize),
            homeBringIntoViewDistance(HomeFocusZone.ROWS, offset = 300f, size = tallChildSize, containerSize = containerSize, rowCardPivotPx = rowCardPivotPx),
            0.0001f,
        )
    }
}
