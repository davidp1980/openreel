package com.shieldstream.tv.domain

import com.shieldstream.tv.model.LiveBlockReason
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LivePlaybackDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtveLivePlaybackResolverTest {

    private var now = 1_000_000L
    private val resolver = RtveLivePlaybackResolver { now }

    @Test
    fun `non-drm channel resolves to primary and tv fallback hls live queue`() {
        val channel = LiveChannel(
            id = "1",
            assetId = "1688877",
            title = "Telediario",
            category = "La 1",
            hasDrm = false,
        )

        val decision = resolver.resolve(channel) as LivePlaybackDecision.Ready

        assertEquals("La 1 · Telediario", decision.title)
        assertEquals(2, decision.sources.size)
        assertEquals("https://ztnr.rtve.es/ztnr/1688877.m3u8", decision.sources[0].directUrl)
        assertEquals("application/x-mpegURL", decision.sources[0].mimeType)
        assertNull(decision.sources[0].drmTokenUrl)
        assertTrue(decision.sources[0].isLive)
        assertEquals("https://ztnr.rtve.es/ztnr/tv/1688877.m3u8", decision.sources[1].directUrl)
    }

    @Test
    fun `drm channel resolves to dash widevine primary and non-drm tv fallback`() {
        val channel = LiveChannel(
            id = "2",
            assetId = "999",
            title = "Partido",
            hasDrm = true,
        )

        val decision = resolver.resolve(channel) as LivePlaybackDecision.Ready

        assertEquals("Partido", decision.title)
        assertEquals(2, decision.sources.size)
        assertEquals("https://ztnr.rtve.es/ztnr/999.mpd", decision.sources[0].directUrl)
        assertEquals("application/dash+xml", decision.sources[0].mimeType)
        assertEquals("https://api.rtve.es/api/token/999", decision.sources[0].drmTokenUrl)
        assertEquals("https://ztnr.rtve.es/ztnr/tv/999.mpd", decision.sources[1].directUrl)
        assertNull(decision.sources[1].drmTokenUrl)
    }

    @Test
    fun `blocks geo-restricted, upcoming, paid and sourceless channels`() {
        val base = LiveChannel(id = "1", assetId = "10", title = "Channel")

        assertEquals(
            LivePlaybackDecision.Blocked(LiveBlockReason.GEO_RESTRICTED),
            resolver.resolve(base.copy(allowedInCountry = false)),
        )
        assertEquals(
            LivePlaybackDecision.Blocked(LiveBlockReason.NOT_STARTED_YET, startsAtMillis = 2_000_000L),
            resolver.resolve(base.copy(isOnAir = false, startsAtMillis = 2_000_000L)),
        )
        assertEquals(
            LivePlaybackDecision.Blocked(LiveBlockReason.SUBSCRIPTION_REQUIRED),
            resolver.resolve(base.copy(paid = true)),
        )
        assertEquals(
            LivePlaybackDecision.Blocked(LiveBlockReason.NO_SOURCE),
            resolver.resolve(base.copy(assetId = null)),
        )
    }
}
