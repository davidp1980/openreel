package com.shieldstream.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RtveImageSizingTest {

    @Test
    fun `rewrites video variants to canonical card and large widths`() {
        assertEquals("https://img.rtve.es/v/123/?w=400", RtveImageSizing.sized("https://img.rtve.es/v/123/"))
        assertEquals("https://img.rtve.es/v/123/?w=1600", RtveImageSizing.sized("https://img.rtve.es/v/123", 1200))
        assertEquals("https://img.rtve.es/v/123/horizontal2?w=400", RtveImageSizing.sized("https://img.rtve.es/v/123/horizontal2"))
        assertEquals("https://img.rtve.es/v/123/vertical/?h=400", RtveImageSizing.sized("https://img.rtve.es/v/123/vertical"))
    }

    @Test
    fun `rewrites program paths and logos`() {
        assertEquals(
            "https://img.rtve.es/p/456/?w=320&imgProgApi=logo",
            RtveImageSizing.sized("https://img.rtve.es/p/456/?imgProgApi=logo"),
        )
        assertEquals(
            "https://img.rtve.es/p/456/imgportada/?w=400",
            RtveImageSizing.sized("https://img.rtve.es/p/456/"),
        )
        assertEquals(
            "https://img.rtve.es/p/456/imgbackground/?h=400",
            RtveImageSizing.sized("https://img.rtve.es/p/456?imgProgApi=imgBackground"),
        )
        assertEquals(
            "https://img.rtve.es/p/bluey_1028792.png",
            RtveImageSizing.sized("https://img.rtve.es/p/bluey_1028792.png"),
        )
    }

    @Test
    fun `wraps imagenes and css through resizer and preserves non-rtve urls`() {
        assertEquals(
            "https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fcss%2Flogo.png",
            RtveImageSizing.sized("https://img.rtve.es/css/logo.png"),
        )
        assertEquals(
            "https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fimagenes%2Fbanner.jpg",
            RtveImageSizing.sized("https://img.rtve.es/imagenes/banner.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            RtveImageSizing.sized("https://image.tmdb.org/t/p/w500/abc.jpg"),
        )
    }
}
