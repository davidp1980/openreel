package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.model.RowStyle
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.network.dto.TmdbTvDto
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryDocumentariesTest {

    private fun repository(kids: Boolean, tmdb: TmdbApi): CatalogRepositoryImpl = CatalogRepositoryImpl(
        tmdb = tmdb,
        torrentio = mockk<TorrentioApi>(),
        extras = mockk<HomeExtrasSource>(),
        store = mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" },
        settings = mockk<SettingsRepository>(),
        profiles = mockk<ProfileRepository> {
            every { active } returns MutableStateFlow(Profile(id = if (kids) "kids" else "adult", name = "Test", avatar = 0, kids = kids))
        },
        texts = mockk<Texts>(relaxed = true),
        ithil = mockk<IthilLibraryRepository>(),
        ithilAccess = mockk<IthilAccessRepository>(),
        itemStore = FakeCatalogItemStore(now = System.currentTimeMillis()),
    )

    @Test
    fun `adult documentaries home builds curated documentary rows`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val movieQueries = mutableListOf<Map<String, String>>()
        val tvQueries = mutableListOf<Map<String, String>>()
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(movieQueries)) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 11, title = "Planet Doc", poster_path = "/m11.jpg")),
        )
        coEvery { tmdb.discoverTvQuery("tmdb-key", capture(tvQueries)) } returns TmdbPaginated(
            results = listOf(TmdbTvDto(id = 21, name = "Cosmos Series", poster_path = "/t21.jpg")),
        )

        val rows = repository(kids = false, tmdb = tmdb).getHome(HomeFilter.DOCUMENTARIES, maxItems = 20)

        assertEquals(
            listOf("d_top10", "d_movies", "d_series", "d_true_crime", "d_history", "d_music", "d_recent", "d_acclaimed"),
            rows.map { it.id },
        )
        assertEquals(RowStyle.TOP10, rows.first().style)
        assertTrue(movieQueries.all { "99" in (it["with_genres"] ?: "") })
        assertTrue(tvQueries.all { "99" in (it["with_genres"] ?: "") })
    }

    @Test
    fun `kids documentaries home builds safe documentary rows excluding mature genres`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val movieQueries = mutableListOf<Map<String, String>>()
        val tvQueries = mutableListOf<Map<String, String>>()
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(movieQueries)) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 31, title = "Ocean Kids", poster_path = "/m31.jpg")),
        )
        coEvery { tmdb.discoverTvQuery("tmdb-key", capture(tvQueries)) } returns TmdbPaginated(
            results = listOf(TmdbTvDto(id = 41, name = "Nature Kids", poster_path = "/t41.jpg")),
        )

        val rows = repository(kids = true, tmdb = tmdb).getHome(HomeFilter.DOCUMENTARIES, maxItems = 20)

        assertEquals(
            listOf("k_doc_movies", "k_doc_family", "k_doc_series", "k_doc_all_ages", "k_doc_top"),
            rows.map { it.id },
        )
        assertTrue(movieQueries.all {
            "99" in (it["with_genres"] ?: "") &&
                it["without_genres"] == "80,27,53,10752" &&
                it["certification_country"] == "US" &&
                it["certification.lte"] in setOf("G", "PG") &&
                it["include_adult"] == "false"
        })
        assertTrue(tvQueries.all {
            "99" in (it["with_genres"] ?: "") &&
                it["without_genres"] == "80,27,53,10768" &&
                it["include_adult"] == "false"
        })
    }
}
