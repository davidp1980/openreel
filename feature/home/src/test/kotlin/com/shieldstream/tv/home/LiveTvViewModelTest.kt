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
}
