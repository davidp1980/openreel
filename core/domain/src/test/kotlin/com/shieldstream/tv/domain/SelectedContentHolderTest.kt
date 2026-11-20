package com.shieldstream.tv.domain

import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.LiveChannelEntry
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.PlaybackMedia
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedContentHolderTest {

    @Test
    fun `selecting an item replaces details and clears stale playback state`() {
        val holder = SelectedContentHolder()
        val next = NextEpisode("show", 2, 1, "Next")
        holder.prefetchNext(next)
        holder.playQueue(emptyList(), "Old", progressKey = "old")
        val item = item()
        val target = EpisodeTarget(2, 3)

        holder.selectItem(item, target, playImmediately = true)

        val details = holder.details.value
        assertEquals(item, details?.item)
        assertEquals(target, details?.target)
        assertEquals(true, details?.playImmediately)
        assertTrue(details?.requestedAtNs?.let { it != 0L } == true)
        assertNull(holder.playback.value)
        assertNull(holder.prefetch.value)
    }

    @Test
    fun `playback fields are published as one request`() {
        val holder = SelectedContentHolder()
        val media = PlaybackMedia(MediaType.TV, "tt1", "Show", "Show", 2026, season = 2, episode = 3)
        val next = NextEpisode("show", 2, 4, "Next")

        holder.playQueue(
            sources = emptyList(),
            label = "Show S02E03",
            progressKey = "tv:show:2:3",
            fromStart = true,
            media = media,
            next = next,
        )

        val playback = holder.playback.value
        assertEquals("Show S02E03", playback?.label)
        assertEquals("tv:show:2:3", playback?.progressKey)
        assertEquals(true, playback?.fromStart)
        assertEquals(false, playback?.isLive)
        assertEquals(media, playback?.media)
        assertEquals(next, playback?.next)
        assertTrue(playback?.requestedAtNs?.let { it != 0L } == true)
        assertEquals(emptyList<Nothing>(), playback?.sources)
    }

    @Test
    fun `live playback flag propagates when explicitly enabled`() {
        val holder = SelectedContentHolder()

        holder.playQueue(
            sources = emptyList(),
            label = "La 1",
            isLive = true,
        )

        assertEquals(true, holder.playback.value?.isLive)
        assertNull(holder.playback.value?.progressKey)
    }

    @Test
    fun `next episode request is consumed once`() {
        val holder = SelectedContentHolder()
        val next = NextEpisode("show", 2, 4, "Next")
        holder.requestNext(next)

        assertEquals(next, holder.consumeNextRequest())
        assertNull(holder.consumeNextRequest())
    }

    @Test
    fun `live lineup and last live channel key are preserved and consumed once`() {
        val holder = SelectedContentHolder()
        val entry = LiveChannelEntry(
            key = "1_10",
            title = "Telediario",
            channelName = "La 1",
            sources = listOf(
                StreamSource(
                    id = "s1",
                    title = "La 1",
                    infoHash = "",
                    fileIdx = null,
                    magnet = "",
                    quality = Quality.FHD_1080,
                    hdrType = HdrType.SDR,
                    sizeBytes = 0L,
                    seeders = null,
                    isCached = true,
                    directUrl = "https://ztnr.rtve.es/ztnr/10.m3u8",
                    isLive = true,
                ),
            ),
        )

        holder.playQueue(
            sources = entry.sources,
            label = entry.displayTitle,
            isLive = true,
            liveChannels = listOf(entry),
            liveChannelIndex = 0,
        )
        assertEquals(listOf(entry), holder.playback.value?.liveChannels)
        assertEquals(0, holder.playback.value?.liveChannelIndex)

        holder.setLastLiveChannelKey("2_20")
        assertEquals("2_20", holder.consumeLastLiveChannelKey())
        assertNull(holder.consumeLastLiveChannelKey())

        holder.setLastLiveChannelKey("3_30")
        holder.selectItem(item())
        assertNull(holder.consumeLastLiveChannelKey())
    }

    private fun item() = CatalogItem(
        id = "show",
        imdbId = "tt1",
        type = MediaType.TV,
        title = "Show",
        overview = "",
        posterUrl = null,
        backdropUrl = null,
        year = 2026,
        rating = null,
    )
}
