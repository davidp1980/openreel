package com.shieldstream.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.shieldstream.tv.domain.CatalogCacheRefresher
import com.shieldstream.tv.domain.LiveDrmLicenseProvider
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.ResolveState
import com.shieldstream.tv.domain.ResolveStreamUseCase
import com.shieldstream.tv.domain.SelectedContentHolder
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.SkipSegmentsRepository
import com.shieldstream.tv.domain.SubtitleRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.media.Media3Factory
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.LiveChannelEntry
import com.shieldstream.tv.model.PlaybackPolicy
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.SubtitlePreference
import com.shieldstream.tv.strings.Texts
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerViewModelLiveTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `live drm playback fetches license, joins live edge without zero start, skips probe and spanish gate`() = runBlocking {
        val players = FakeLivePlayers(englishAudioTracks())
        val fixture = liveFixture(
            players = players,
            sources = listOf(drmSource(), fallbackDashSource()),
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license?tok=1" },
            widevineSupported = true,
            spanishOnlySources = true,
            matchContentFrameRate = true,
        )

        fixture.viewModel.start()
        yield()
        players.listener(0).onPlaybackStateChanged(Player.STATE_READY)

        assertEquals(1, players.singleArgItems.size)
        assertEquals(0, players.startPositionItems.size)
        val item = players.singleArgItems[0]
        assertEquals("https://ztnr.rtve.es/ztnr/999.mpd", item.localConfiguration?.uri.toString())
        assertEquals("application/dash+xml", item.localConfiguration?.mimeType)
        assertEquals(
            "https://drm.axinom.test/license?tok=1",
            item.localConfiguration?.drmConfiguration?.licenseUri.toString(),
        )
        assertNull(fixture.viewModel.earlyFrameRate)
        verify(exactly = 0) { fixture.dataSourceFactory.createDataSource() }

        players.listener(0).onIsPlayingChanged(true)
        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()

        verify(exactly = 0) { fixture.watchProgress.save(any(), any(), any(), any()) }
        verify(exactly = 0) { fixture.trakt.scrobble(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.skipSegments.episode(any(), any(), any()) }
    }

    @Test
    fun `live drm falls back to tv dash when widevine is unsupported`() = runBlocking {
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = listOf(drmSource(), fallbackDashSource()),
            licenseProvider = LiveDrmLicenseProvider { error("should not fetch license") },
            widevineSupported = false,
        )

        fixture.viewModel.start()
        yield()

        assertEquals(1, players.singleArgItems.size)
        val item = players.singleArgItems[0]
        assertEquals("https://ztnr.rtve.es/ztnr/tv/999.mpd", item.localConfiguration?.uri.toString())
        assertNull(item.localConfiguration?.drmConfiguration)
        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `live drm error falls back to non-drm tv dash even after playback`() = runBlocking {
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = listOf(drmSource(), fallbackDashSource()),
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license" },
            widevineSupported = true,
        )

        fixture.viewModel.start()
        yield()
        players.listener(0).onIsPlayingChanged(true)
        players.listener(0).onPlayerError(
            PlaybackException("drm failed", null, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED),
        )
        yield()

        assertEquals(2, players.singleArgItems.size)
        assertEquals(
            "https://ztnr.rtve.es/ztnr/tv/999.mpd",
            players.singleArgItems[1].localConfiguration?.uri.toString(),
        )
        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `behind live window error seeks to default position and prepares`() = runBlocking {
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = listOf(hlsSource()),
            licenseProvider = LiveDrmLicenseProvider { null },
        )

        fixture.viewModel.start()
        yield()
        val player = players.player(0)
        players.listener(0).onPlayerError(
            PlaybackException("behind window", null, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW),
        )

        verify(exactly = 1) { player.seekToDefaultPosition() }
        verify(atLeast = 2) { player.prepare() }
        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `zapLiveChannel wraps around, switches between drm and hls, releases old player and saves last key`() = runBlocking {
        val channels = sampleLineup()
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = channels[0].sources,
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license" },
            widevineSupported = true,
            liveChannels = channels,
            liveChannelIndex = 0,
        ).apply { viewModel.zapDebounceMs = 0L }

        fixture.viewModel.start()
        yield()
        assertEquals(0, fixture.viewModel.liveChannelIndex.value)
        assertEquals("La 1 · Telediario", fixture.viewModel.title.value)
        assertEquals("https://ztnr.rtve.es/ztnr/999.mpd", players.singleArgItems[0].localConfiguration?.uri.toString())

        fixture.viewModel.zapLiveChannel(+1)
        yield()
        assertEquals(1, fixture.viewModel.liveChannelIndex.value)
        assertEquals("24h", fixture.viewModel.title.value)
        assertEquals(2, players.singleArgItems.size)
        assertEquals("https://ztnr.rtve.es/ztnr/123.m3u8", players.singleArgItems[1].localConfiguration?.uri.toString())
        assertNull(players.singleArgItems[1].localConfiguration?.drmConfiguration)
        verify(exactly = 1) { players.player(0).release() }

        // Wrap backwards from index 1 -> 0 -> 2
        fixture.viewModel.zapLiveChannel(-1)
        yield()
        fixture.viewModel.zapLiveChannel(-1)
        yield()
        assertEquals(2, fixture.viewModel.liveChannelIndex.value)
        assertEquals("Teledeporte", fixture.viewModel.title.value)
        assertEquals("3_777", fixture.holder.consumeLastLiveChannelKey())

        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `rapid zapping within debounce window updates label immediately and tunes final channel only once`() = runTest {
        val channels = sampleLineup()
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = channels[0].sources,
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license" },
            widevineSupported = true,
            liveChannels = channels,
            liveChannelIndex = 0,
        )

        fixture.viewModel.start()
        runCurrent()
        assertEquals(1, players.singleArgItems.size)

        fixture.viewModel.zapLiveChannel(+1)
        fixture.viewModel.zapLiveChannel(+1)
        assertEquals(2, fixture.viewModel.liveChannelIndex.value)
        assertEquals("Teledeporte", fixture.viewModel.title.value)
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, players.singleArgItems.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, players.singleArgItems.size)
        assertEquals("https://ztnr.rtve.es/ztnr/777.m3u8", players.singleArgItems[1].localConfiguration?.uri.toString())

        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `rapid zap back to active channel within debounce window does not recreate player`() = runTest {
        val channels = sampleLineup()
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = channels[0].sources,
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license" },
            widevineSupported = true,
            liveChannels = channels,
            liveChannelIndex = 0,
        )

        fixture.viewModel.start()
        runCurrent()
        assertEquals(1, players.singleArgItems.size)

        fixture.viewModel.zapLiveChannel(+1)
        fixture.viewModel.zapLiveChannel(-1)
        advanceTimeBy(400)
        runCurrent()

        assertEquals(0, fixture.viewModel.liveChannelIndex.value)
        assertEquals(1, players.singleArgItems.size)
        verify(exactly = 0) { players.player(0).release() }

        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    @Test
    fun `selectLiveChannel with immediate switches without waiting for debounce`() = runTest {
        val channels = sampleLineup()
        val players = FakeLivePlayers(Tracks.EMPTY)
        val fixture = liveFixture(
            players = players,
            sources = channels[0].sources,
            licenseProvider = LiveDrmLicenseProvider { "https://drm.axinom.test/license" },
            widevineSupported = true,
            liveChannels = channels,
            liveChannelIndex = 0,
        )

        fixture.viewModel.start()
        runCurrent()
        fixture.viewModel.selectLiveChannel(2, immediate = true)
        runCurrent()

        assertEquals(2, fixture.viewModel.liveChannelIndex.value)
        assertEquals("Teledeporte", fixture.viewModel.title.value)
        assertEquals(2, players.singleArgItems.size)
        assertEquals("https://ztnr.rtve.es/ztnr/777.m3u8", players.singleArgItems[1].localConfiguration?.uri.toString())

        fixture.viewModel.onStop()
        fixture.viewModel.viewModelScope.cancel()
    }

    private fun sampleLineup(): List<LiveChannelEntry> = listOf(
        LiveChannelEntry(
            key = "1_999",
            title = "Telediario",
            channelName = "La 1",
            sources = listOf(drmSource(), fallbackDashSource()),
        ),
        LiveChannelEntry(
            key = "2_123",
            title = "24h",
            sources = listOf(hlsSource()),
        ),
        LiveChannelEntry(
            key = "3_777",
            title = "Teledeporte",
            sources = listOf(
                hlsSource().copy(id = "rtve_777_hls", title = "Teledeporte", directUrl = "https://ztnr.rtve.es/ztnr/777.m3u8"),
            ),
        ),
    )

    private fun liveFixture(
        players: FakeLivePlayers,
        sources: List<StreamSource>,
        licenseProvider: LiveDrmLicenseProvider,
        widevineSupported: Boolean = true,
        spanishOnlySources: Boolean = false,
        matchContentFrameRate: Boolean = false,
        liveChannels: List<LiveChannelEntry> = emptyList(),
        liveChannelIndex: Int = 0,
    ): LiveFixture {
        val policy = PlaybackPolicy.automatic(512L * 1024 * 1024)
        val dataSourceFactory = mockk<DataSource.Factory>(relaxed = true)
        val media3Factory = mockk<Media3Factory>()
        every { media3Factory.createPlayer(any()) } answers { players.create() }
        every { media3Factory.dataSourceFactory } returns dataSourceFactory
        val playbackPolicies = mockk<PlaybackPolicyProvider> {
            every { automatic() } returns policy
            every { resolve(any(), any(), any()) } returns policy
        }
        val resolve = mockk<ResolveStreamUseCase>()
        every { resolve.invoke(any<StreamSource>()) } answers {
            val source = firstArg<StreamSource>()
            flowOf(ResolveState.Ready(source.directUrl!!))
        }
        val settings = mockk<SettingsRepository>(relaxed = true) {
            every { deviceOptimization } returns flowOf(DeviceOptimization.CUSTOM)
            every { maxBufferSeconds } returns flowOf(40)
            every { seekThumbnails } returns flowOf(true)
            every { autoNextEpisode } returns flowOf(true)
            every { dialogueBoost } returns flowOf(false)
        }
        val profile = Profile(
            id = "test",
            name = "Test",
            avatar = 0,
            spanishFirst = spanishOnlySources,
            spanishOnlySources = spanishOnlySources,
            subtitlePreference = SubtitlePreference.OFF,
            matchContentFrameRate = matchContentFrameRate,
        )
        val profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(profile)
        }
        val holder = SelectedContentHolder().apply {
            playQueue(
                sources = sources,
                label = liveChannels.getOrNull(liveChannelIndex)?.displayTitle ?: "La 1",
                isLive = true,
                liveChannels = liveChannels,
                liveChannelIndex = liveChannelIndex,
            )
        }
        val watchProgress = mockk<WatchProgressRepository>(relaxed = true)
        val trakt = mockk<TraktRepository>(relaxed = true)
        val skipSegments = mockk<SkipSegmentsRepository>(relaxed = true)
        val vm = PlayerViewModel(
            media3Factory = media3Factory,
            playbackPolicies = playbackPolicies,
            resolveStreamUseCase = resolve,
            settings = settings,
            profiles = profiles,
            holder = holder,
            watchProgress = watchProgress,
            subtitles = mockk<SubtitleRepository>(relaxed = true),
            trakt = trakt,
            skipSegmentsRepository = skipSegments,
            liveDrmProvider = licenseProvider,
            texts = mockk<Texts>(relaxed = true),
            catalogCache = mockk<CatalogCacheRefresher>(relaxed = true),
        ).apply {
            isWidevineSupported = { widevineSupported }
        }
        return LiveFixture(vm, holder, dataSourceFactory, watchProgress, trakt, skipSegments)
    }

    private fun drmSource() = StreamSource(
        id = "rtve_999_dash",
        title = "La 1",
        infoHash = "",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 0L,
        seeders = null,
        isCached = true,
        directUrl = "https://ztnr.rtve.es/ztnr/999.mpd",
        provider = "RTVE",
        mimeType = "application/dash+xml",
        drmTokenUrl = "https://api.rtve.es/api/token/999",
        isLive = true,
    )

    private fun fallbackDashSource() = StreamSource(
        id = "rtve_999_dash_fallback",
        title = "La 1",
        infoHash = "",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 0L,
        seeders = null,
        isCached = true,
        directUrl = "https://ztnr.rtve.es/ztnr/tv/999.mpd",
        provider = "RTVE",
        mimeType = "application/dash+xml",
        drmTokenUrl = null,
        isLive = true,
    )

    private fun hlsSource() = StreamSource(
        id = "rtve_123_hls",
        title = "24h",
        infoHash = "",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 0L,
        seeders = null,
        isCached = true,
        directUrl = "https://ztnr.rtve.es/ztnr/123.m3u8",
        provider = "RTVE",
        mimeType = "application/x-mpegURL",
        isLive = true,
    )

    private fun englishAudioTracks(): Tracks {
        val format = Format.Builder()
            .setId("audio-en")
            .setLanguage("en")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .build()
        val group = Tracks.Group(
            TrackGroup("audio", format),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(false),
        )
        return Tracks(listOf(group))
    }

    private data class LiveFixture(
        val viewModel: PlayerViewModel,
        val holder: SelectedContentHolder,
        val dataSourceFactory: DataSource.Factory,
        val watchProgress: WatchProgressRepository,
        val trakt: TraktRepository,
        val skipSegments: SkipSegmentsRepository,
    )

    private class FakeLivePlayers(private val tracks: Tracks) {
        val singleArgItems = mutableListOf<MediaItem>()
        val startPositionItems = mutableListOf<Pair<MediaItem, Long>>()
        private val listeners = mutableListOf<Player.Listener?>()
        private val instances = mutableListOf<ExoPlayer>()

        fun create(): ExoPlayer {
            val player = mockk<ExoPlayer>(relaxed = true)
            instances += player
            var selection = TrackSelectionParameters.DEFAULT
            listeners += null
            val index = instances.lastIndex
            val listener = slot<Player.Listener>()
            val singleItem = slot<MediaItem>()
            val posItem = slot<MediaItem>()
            val posMs = slot<Long>()
            every { player.trackSelectionParameters } answers { selection }
            every { player.trackSelectionParameters = any() } answers { selection = firstArg() }
            every { player.currentTracks } returns tracks
            every { player.playbackState } returns Player.STATE_READY
            every { player.duration } returns C.TIME_UNSET
            every { player.currentPosition } returns 0L
            every { player.addListener(capture(listener)) } answers { listeners[index] = listener.captured }
            every { player.setMediaItem(capture(singleItem)) } answers { singleArgItems += singleItem.captured }
            every { player.setMediaItem(capture(posItem), capture(posMs)) } answers {
                startPositionItems += posItem.captured to posMs.captured
            }
            return player
        }

        fun listener(index: Int): Player.Listener = listeners[index]!!
        fun player(index: Int): ExoPlayer = instances[index]
    }
}
