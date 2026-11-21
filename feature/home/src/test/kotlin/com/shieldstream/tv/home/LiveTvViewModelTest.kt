package com.shieldstream.tv.home

import com.shieldstream.tv.domain.LiveTvRepository
import com.shieldstream.tv.domain.RtveLivePlaybackResolver
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
            StringsR.string.live_tv_m3u_error_empty -> "Empty URL"
            StringsR.string.live_tv_m3u_error_invalid_url -> "Invalid URL"
            StringsR.string.live_tv_m3u_error_load -> "Load failed"
            StringsR.string.live_tv_m3u_imported -> "${formatArgs[0]} channels imported"
            StringsR.string.live_tv_m3u_removed -> "Playlist removed"
            else -> "text_$id"
        }

        override fun quantity(id: Int, count: Int, vararg formatArgs: Any): String = "qty_$id"
    }

    private fun fakeSettings(initialUrl: String? = null): SettingsRepository {
        val urlFlow = MutableStateFlow(initialUrl)
        return mockk(relaxed = true) {
            every { customM3uUrl } returns urlFlow
            coEvery { setCustomM3uUrl(any()) } answers {
                urlFlow.value = firstArg<String?>()?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
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
            coEvery { loadCustomM3uSections(any()) } returns emptyList()
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, fakeSettings(), texts)

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
            coEvery { loadCustomM3uSections(any()) } returns emptyList()
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, fakeSettings(), texts)

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
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, fakeSettings(), texts)

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
                id = "m3u_news",
                title = "News",
                channels = listOf(
                    LiveChannel(
                        id = "m3u_news_ch1",
                        title = "Custom News",
                        category = "News",
                        directUrls = listOf("https://live.example.com/news.m3u8"),
                        provider = "M3U",
                    ),
                ),
            ),
        )
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns listOf(sections[0])
            coEvery { loadThematicChannels(any()) } returns sections[1].channels
            coEvery { loadCustomM3uSections(any()) } returns listOf(sections[2])
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, fakeSettings(), texts)

        viewModel.activate()

        val selected = sections[0].channels[2]
        val lineup = viewModel.buildPlaybackLineup(selected)
        assertNotNull(lineup)
        assertEquals(1, lineup!!.index)
        assertEquals(listOf("1_10", "3_30", "4_40", "m3u_news_ch1_"), lineup.channels.map { it.key })
        assertEquals("La 2 · Saber y ganar", lineup.channels[1].displayTitle)
        assertEquals("Somos Cine", lineup.channels[2].displayTitle)
        assertEquals("Custom News", lineup.channels[3].displayTitle)

        val filteredLineup = viewModel.buildPlaybackLineup(sections[2].channels[0], listOf(sections[2]))
        assertNotNull(filteredLineup)
        assertEquals(1, filteredLineup!!.channels.size)
        assertEquals("Custom News", filteredLineup.channels.single().displayTitle)

        val blocked = viewModel.buildPlaybackLineup(sections[0].channels[1])
        assertNull(blocked)
        assertEquals("Geo-blocked", viewModel.notice.value)
    }

    @Test
    fun `silent refresh preserves custom M3U sections when M3U load fails`() = runTest {
        val main = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(LiveChannel(id = "1", assetId = "10", title = "La 1")),
            ),
        )
        val m3u = listOf(
            LiveTvSection(
                id = "m3u_custom",
                title = "My Channels",
                channels = listOf(
                    LiveChannel(
                        id = "m3u_custom_1",
                        title = "Channel 1",
                        directUrls = listOf("https://live.example.com/ch1.m3u8"),
                        provider = "M3U",
                    ),
                ),
            ),
        )
        var failM3u = false
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns main
            coEvery { loadThematicChannels(any()) } returns emptyList()
            coEvery { loadCustomM3uSections(any()) } answers {
                if (failM3u) throw IOException("offline") else m3u
            }
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, fakeSettings("https://example.com/list.m3u"), texts)

        viewModel.activate()
        failM3u = true
        viewModel.refreshSilent()

        val content = viewModel.uiState.value as LiveTvUiState.Content
        assertEquals(listOf("main", "m3u_custom"), content.sections.map { it.id })
    }

    @Test
    fun `importing and removing a custom M3U URL updates settings and live sections`() = runTest {
        val main = listOf(
            LiveTvSection(
                id = "main",
                title = "Live now",
                channels = listOf(LiveChannel(id = "1", assetId = "10", title = "La 1")),
            ),
        )
        val imported = listOf(
            LiveTvSection(
                id = "m3u_sports",
                title = "Sports",
                channels = listOf(
                    LiveChannel(
                        id = "m3u_sports_1",
                        title = "Sports 1",
                        directUrls = listOf("https://live.example.com/s1.m3u8"),
                        provider = "M3U",
                    ),
                ),
            ),
        )
        val settings = fakeSettings()
        val repository = mockk<LiveTvRepository> {
            coEvery { loadMainSections(any()) } returns main
            coEvery { loadThematicChannels(any()) } returns emptyList()
            coEvery { loadCustomM3uSections(any()) } returns emptyList()
            coEvery { validateAndLoadCustomM3u("https://example.com/my.m3u") } returns imported
        }
        val viewModel = LiveTvViewModel(repository, RtveLivePlaybackResolver { 1_000L }, settings, texts)
        viewModel.activate()

        viewModel.openM3uEditor()
        viewModel.saveM3uUrl("not-a-url")
        assertEquals("Invalid URL", viewModel.m3uEditor.value?.error)

        viewModel.saveM3uUrl("https://example.com/my.m3u")
        assertNull(viewModel.m3uEditor.value)
        assertEquals("https://example.com/my.m3u", viewModel.customM3uUrl.value)
        assertEquals("1 channels imported", viewModel.notice.value)
        assertEquals(listOf("main", "m3u_sports"), (viewModel.uiState.value as LiveTvUiState.Content).sections.map { it.id })

        viewModel.openM3uEditor()
        viewModel.removeM3uUrl()
        assertNull(viewModel.m3uEditor.value)
        assertNull(viewModel.customM3uUrl.value)
        assertEquals("Playlist removed", viewModel.notice.value)
        assertEquals(listOf("main"), (viewModel.uiState.value as LiveTvUiState.Content).sections.map { it.id })
    }
}
