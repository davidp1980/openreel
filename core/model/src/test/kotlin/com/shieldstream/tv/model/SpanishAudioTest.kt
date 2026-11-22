package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishAudioTest {

    @Test
    fun `dual castellano plus VO scores highest`() {
        assertEquals(4, source(title = "Movie.Dual.Castellano.Ingles.1080p").spanishAudioScore)
        assertEquals(4, source(title = "Movie.1080p", languages = listOf("ES", "EN")).spanishAudioScore)
        assertEquals(4, source(title = "Movie.Castellano.ENG.1080p").spanishAudioScore)
    }

    @Test
    fun `castellano only scores 3`() {
        assertEquals(3, source(title = "Movie.Castellano.1080p").spanishAudioScore)
        assertEquals(3, source(title = "Movie.ESP.1080p").spanishAudioScore)
        assertEquals(3, source(title = "Movie.1080p", languages = listOf("ES")).spanishAudioScore)
    }

    @Test
    fun `generic spanish scores 2 and wide multi with ES flag scores 1`() {
        assertEquals(2, source(title = "Movie.Spanish.1080p").spanishAudioScore)
        assertEquals(1, source(title = "Movie.1080p", languages = listOf("ES", "EN", "FR", "DE")).spanishAudioScore)
    }

    @Test
    fun `VO, English, and bare Dual without Spanish score 0 and do not advertise Spanish`() {
        val vo = source(title = "Movie.1080p", languages = listOf("EN"))
        val bareDual = source(title = "Movie.Dual.1080p", languages = listOf("EN"))

        assertEquals(0, vo.spanishAudioScore)
        assertEquals(0, bareDual.spanishAudioScore)
        assertFalse(vo.advertisesSpanishAudio)
        assertFalse(bareDual.advertisesSpanishAudio)
    }

    @Test
    fun `latino releases score minus 1 even when addon adds ES flag`() {
        assertEquals(-1, source(title = "Movie.Español.Latino.1080p", languages = listOf("ES")).spanishAudioScore)
        assertEquals(-1, source(title = "Movie.Dual.Latino.Ingles.1080p", languages = listOf("ES", "EN")).spanishAudioScore)
        assertEquals(-1, source(title = "Movie.es-la.1080p", languages = listOf("ES")).spanishAudioScore)
        assertFalse(source(title = "Movie.Dual.Latino.Ingles.1080p", languages = listOf("ES", "EN")).advertisesSpanishAudio)
    }

    @Test
    fun `explicit castellano plus latino stays prioritized`() {
        val both = source(title = "Movie.Castellano.Latino.1080p", languages = listOf("ES", "LAT"))
        assertTrue(both.spanishAudioScore >= 3)
        assertTrue(both.advertisesSpanishAudio)
    }

    private fun source(
        title: String,
        languages: List<String> = emptyList(),
    ) = StreamSource(
        id = title,
        title = title,
        infoHash = "0".repeat(40),
        fileIdx = null,
        magnet = "magnet:?xt=urn:btih:${"0".repeat(40)}",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 1_000_000_000,
        seeders = 10,
        isCached = true,
        languages = languages,
    )
}
