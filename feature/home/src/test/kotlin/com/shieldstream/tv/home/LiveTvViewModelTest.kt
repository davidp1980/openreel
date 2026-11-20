package com.shieldstream.tv.home

import com.shieldstream.tv.domain.LiveTvRepository
import com.shieldstream.tv.domain.RtveLivePlaybackResolver
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val texts = object : Texts {
        override fun get(id: Int, vararg formatArgs: Any): String = when (id) {
            StringsR.string.live_tv_section_thematic -> "24/7 channels"
            StringsR.string.live_tv_error -> "Error loading live channels"
            StringsR.string.live_tv_blocked_geo -> "Geo-blocked"
            StringsR.string.live_tv_blocked_upcoming -> "Starts at ${formatArgs[0]}"
            else -> "text_$id"
        }

        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = "qty_$id"
    }

    @Test
    fun `does not load until activated and merges thematic channels`() = runTest {
        val main = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(LiveChannel(id = "1", assetId = "10", title = "La 1")),
            ),
        )
        val thematic = listOf(LiveChannel(id = "2", assetId = "20", title = "Somos Cine"))
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns main
            coEvery { loadThematicChannels(any()) } returns thematic
            coEvery { loadOpenSections(any()) } returns emptyList()
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, texts)

        assertEquals(LiveTvUiState.Loading, viewModel.uiState.value)
        coVerify(exactly = 0) { repository.loadMainSections(any()) }

        viewModel.activate()
        viewModel.activate()

        val content = viewModel.uiState.value as LiveTvUiState.Content
        assertEquals(2, content.sections.size)
        assertEquals("Live now", content.sections[0].title)
        assertEquals("24/7 channels", content.sections[1].title)
        assertEquals("Somos Cine", content.sections[1].channels.single().title)
        coVerify(exactly = 1) { repository.loadMainSections(false) }
    }

    @Test
    fun `thematic failure leaves main sections intact`() = runTest {
        val main = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(LiveChannel(id = "1", assetId = "10", title = "La 1")),
            ),
        )
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns main
            coEvery { loadThematicChannels(any()) } throws IOException("timeout")
            coEvery { loadOpenSections(any()) } returns emptyList()
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, texts)

        viewModel.activate()

        val content = viewModel.uiState.value as LiveTvUiState.Content
        assertEquals(1, content.sections.size)
        assertEquals("La 1", content.sections[0].channels.single().title)
    }

    @Test
    fun `main failure shows error and blocked channel emits notice`() = runTest {
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } throws IOException("offline")
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, texts)

        viewModel.activate()

        assertEquals(LiveTvUiState.Error("Error loading live channels"), viewModel.uiState.value)

        val blocked = viewModel.resolvePlayback(
            LiveChannel(id = "1", assetId = "10", title = "La 1", allowedInCountry = false),
        )
        assertNull(blocked)
        assertEquals("Geo-blocked", viewModel.notice.value)

        val ready = viewModel.resolvePlayback(LiveChannel(id = "2", assetId = "20", title = "La 2"))
        assertNotNull(ready)
        assertTrue(ready!!.sources.isNotEmpty())
    }

    @Test
    fun `buildPlaybackLineup flattens playable channels, skips blocked ones, and keeps selected index`() = runTest {
        val sections = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(
                    LiveChannel(id = "1", assetId = "10", title = "Telediario", category = "La 1"),
                    LiveChannel(id = "2", assetId = "20", title = "Geo", allowedInCountry = false),
                    LiveChannel(id = "3", assetId = "30", title = "Saber y ganar", category = "La 2"),
                ),
            ),
            LiveTvSection(
                id = "thematic",
                title = "24/7",
                channels = listOf(
                    LiveChannel(id = "4", assetId = "40", title = "Somos Cine", category = "Somos Cine"),
                    LiveChannel(id = "3", assetId = "30", title = "Saber y ganar duplicate", category = "La 2"),
                ),
            ),
            LiveTvSection(
                id = "tdt_spain_generalistas",
                title = "Generalistas",
                channels = listOf(
                    LiveChannel(
                        id = "tdt_a3",
                        title = "Antena 3",
                        category = "Generalistas",
                        directUrls = listOf("https://live.example.com/a3.m3u8"),
                        provider = "TDT",
                    ),
                ),
            ),
        )
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns listOf(sections[0])
            coEvery { loadThematicChannels(any()) } returns sections[1].channels
            coEvery { loadOpenSections(any()) } returns listOf(sections[2])
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, texts)

        viewModel.activate()

        val selected = sections[0].channels[2]
        val lineup = viewModel.buildPlaybackLineup(selected)
        assertNotNull(lineup)
        assertEquals(1, lineup!!.index)
        assertEquals(listOf("1_10", "3_30", "4_40", "tdt_a3_"), lineup.channels.map { it.key })
        assertEquals("La 2 · Saber y ganar", lineup.channels[1].displayTitle)
        assertEquals("Somos Cine", lineup.channels[2].displayTitle)
        assertEquals("Antena 3", lineup.channels[3].displayTitle)

        val filteredLineup = viewModel.buildPlaybackLineup(sections[2].channels[0], listOf(sections[2]))
        assertNotNull(filteredLineup)
        assertEquals(1, filteredLineup!!.channels.size)
        assertEquals("Antena 3", filteredLineup.channels.single().displayTitle)

        val blocked = viewModel.buildPlaybackLineup(sections[0].channels[1])
        assertNull(blocked)
        assertEquals("Geo-blocked", viewModel.notice.value)
    }

    @Test
    fun `silent refresh preserves both tdt and fast sections when open load fails`() = runTest {
        val main = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(LiveChannel(id = "1", assetId = "10", title = "La 1")),
            ),
        )
        val open = listOf(
            LiveTvSection(
                id = "tdt_nacionales",
                title = "TDT Nacionales",
                channels = listOf(
                    LiveChannel(
                        id = "tdt_trece",
                        title = "TRECE",
                        directUrls = listOf("https://live.example.com/trece.m3u8"),
                        provider = "TDT",
                    ),
                ),
            ),
            LiveTvSection(
                id = "fast_cine",
                title = "Cine",
                channels = listOf(
                    LiveChannel(
                        id = "fast_cine_samsung_top",
                        title = "Top Cine",
                        directUrls = listOf("https://jmp2.uk/stvp-top"),
                        provider = "Samsung TV Plus",
                    ),
                ),
            ),
        )
        var failOpen = false
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns main
            coEvery { loadThematicChannels(any()) } returns emptyList()
            coEvery { loadOpenSections(any()) } answers {
                if (failOpen) throw IOException("offline") else open
            }
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, texts)

        viewModel.activate()
        failOpen = true
        viewModel.refreshSilent()

        val content = viewModel.uiState.value as LiveTvUiState.Content
        assertEquals(listOf("main", "tdt_nacionales", "fast_cine"), content.sections.map { it.id })
    }
}
