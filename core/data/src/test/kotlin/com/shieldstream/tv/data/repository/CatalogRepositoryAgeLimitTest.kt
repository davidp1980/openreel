package com.shieldstream.tv.data.repository

import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.IthilAccessRepository
import com.shieldstream.tv.domain.IthilLibraryRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.Profile
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.api.TorrentioApi
import com.shieldstream.tv.network.dto.TmdbContentRatingDto
import com.shieldstream.tv.network.dto.TmdbContentRatingsDto
import com.shieldstream.tv.network.dto.TmdbMovieDetailDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.network.dto.TmdbReleaseCountryDto
import com.shieldstream.tv.network.dto.TmdbReleaseDateDto
import com.shieldstream.tv.network.dto.TmdbReleaseDatesDto
import com.shieldstream.tv.network.dto.TmdbSearchResponse
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTvDetailDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryAgeLimitTest {

    private fun repository(maxAge: Int?, tmdb: TmdbApi, itemStore: CatalogItemStore = FakeCatalogItemStore(now = System.currentTimeMillis())) =
        CatalogRepositoryImpl(
            tmdb = tmdb,
            torrentio = mockk<TorrentioApi>(),
            extras = mockk<HomeExtrasSource>(),
            store = mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" },
            settings = mockk<SettingsRepository>(),
            profiles = mockk<ProfileRepository> {
                every { active } returns MutableStateFlow(Profile(id = "kids", name = "Kids", avatar = 0, kids = true, maxAge = maxAge))
            },
            texts = mockk<Texts>(relaxed = true),
            ithil = mockk<IthilLibraryRepository>(),
            ithilAccess = mockk<IthilAccessRepository>(),
            itemStore = itemStore,
        )

    private fun mockTmdbWithRatings(
        movieQueries: MutableList<Map<String, String>> = mutableListOf(),
    ): TmdbApi {
        val tmdb = mockk<TmdbApi>()
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(movieQueries)) } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 1, title = "Safe G", poster_path = "/1.jpg"),
                TmdbMovieDto(id = 2, title = "Safe PG", poster_path = "/2.jpg"),
                TmdbMovieDto(id = 3, title = "Blocked 13", poster_path = "/3.jpg"),
                TmdbMovieDto(id = 4, title = "Unrated Movie", poster_path = "/4.jpg"),
            ),
        )
        coEvery { tmdb.discoverTvQuery("tmdb-key", any()) } returns TmdbPaginated(
            results = listOf(
                TmdbTvDto(id = 10, name = "Safe TV-Y", poster_path = "/10.jpg"),
                TmdbTvDto(id = 11, name = "Blocked TV-14", poster_path = "/11.jpg"),
                TmdbTvDto(id = 12, name = "Unrated Show", poster_path = "/12.jpg"),
            ),
        )
        coEvery { tmdb.movieReleaseDates(1, "tmdb-key") } returns TmdbReleaseDatesDto(
            listOf(TmdbReleaseCountryDto("US", listOf(TmdbReleaseDateDto("G")))),
        )
        coEvery { tmdb.movieReleaseDates(2, "tmdb-key") } returns TmdbReleaseDatesDto(
            listOf(TmdbReleaseCountryDto("US", listOf(TmdbReleaseDateDto("PG")))),
        )
        coEvery { tmdb.movieReleaseDates(3, "tmdb-key") } returns TmdbReleaseDatesDto(
            listOf(TmdbReleaseCountryDto("US", listOf(TmdbReleaseDateDto("PG-13")))),
        )
        coEvery { tmdb.movieReleaseDates(4, "tmdb-key") } returns TmdbReleaseDatesDto()

        coEvery { tmdb.tvContentRatings(10, "tmdb-key") } returns TmdbContentRatingsDto(
            listOf(TmdbContentRatingDto("US", "TV-Y")),
        )
        coEvery { tmdb.tvContentRatings(11, "tmdb-key") } returns TmdbContentRatingsDto(
            listOf(TmdbContentRatingDto("US", "TV-14")),
        )
        coEvery { tmdb.tvContentRatings(12, "tmdb-key") } returns TmdbContentRatingsDto()
        return tmdb
    }

    @Test
    fun `home trending search recommendations and item lookups hide age-prohibited and unrated covers`() = runTest {
        val movieQueries = mutableListOf<Map<String, String>>()
        val tmdb = mockTmdbWithRatings(movieQueries)
        coEvery { tmdb.search("tmdb-key", "test") } returns TmdbSearchResponse(
            results = listOf(
                TmdbSearchResultDto(id = 1, media_type = "movie", title = "Safe G", poster_path = "/1.jpg", genre_ids = listOf(16)),
                TmdbSearchResultDto(id = 3, media_type = "movie", title = "Blocked 13", poster_path = "/3.jpg", genre_ids = listOf(16)),
                TmdbSearchResultDto(id = 4, media_type = "movie", title = "Unrated", poster_path = "/4.jpg", genre_ids = listOf(16)),
                TmdbSearchResultDto(id = 10, media_type = "tv", name = "Safe TV-Y", poster_path = "/10.jpg", genre_ids = listOf(10762)),
                TmdbSearchResultDto(id = 11, media_type = "tv", name = "Blocked TV-14", poster_path = "/11.jpg", genre_ids = listOf(10762)),
            ),
        )
        coEvery { tmdb.movieRecommendations(1, "tmdb-key") } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 2, title = "Safe PG", poster_path = "/2.jpg", genre_ids = listOf(16)),
                TmdbMovieDto(id = 3, title = "Blocked 13", poster_path = "/3.jpg", genre_ids = listOf(16)),
                TmdbMovieDto(id = 4, title = "Unrated Movie", poster_path = "/4.jpg", genre_ids = listOf(16)),
            ),
        )
        coEvery { tmdb.movieSimilar(1, "tmdb-key") } returns TmdbPaginated()
        coEvery { tmdb.movieDetails(1, "tmdb-key", any(), any()) } returns TmdbMovieDetailDto(
            id = 1,
            title = "Safe G",
            poster_path = "/1.jpg",
        )
        coEvery { tmdb.tvDetails(10, "tmdb-key", any(), any()) } returns TmdbTvDetailDto(
            id = 10,
            name = "Safe TV-Y",
            poster_path = "/10.jpg",
        )

        val itemStore = FakeCatalogItemStore(now = System.currentTimeMillis()).apply {
            put(CatalogItem("3", null, MediaType.MOVIE, "Cached Blocked", "", "/3.jpg", null, null, null))
        }
        val repo = repository(maxAge = 7, tmdb = tmdb, itemStore = itemStore)

        val homeRows = repo.getHome(HomeFilter.ALL, maxItems = 20)
        assertTrue(homeRows.isNotEmpty())
        homeRows.forEach { row ->
            val ids = row.items.map { it.id }.toSet()
            assertTrue("Row ${row.id} leaked forbidden id: $ids", ids.all { it in setOf("1", "2", "10") })
        }

        assertEquals(listOf("1", "2"), repo.trending().map { it.id })
        assertEquals(listOf("1", "10"), repo.search("test").map { it.id })

        val origin = CatalogItem("1", null, MediaType.MOVIE, "Safe G", "", "/1.jpg", null, null, null)
        assertEquals(listOf("2"), repo.getRecommendations(origin).map { it.id })

        assertNull(repo.getItem(MediaType.MOVIE, "3"))
        assertNull(repo.getItem(MediaType.MOVIE, "4"))
        assertEquals("Safe G", repo.getItem(MediaType.MOVIE, "1")?.title)
        assertNull(repo.getShowAiring("11"))
        assertEquals("Safe TV-Y", repo.getShowAiring("10")?.item?.title)
    }

    @Test
    fun `kids profile with age limit 0 uses G certification on discover and hides PG movies`() = runTest {
        val movieQueries = mutableListOf<Map<String, String>>()
        val tmdb = mockTmdbWithRatings(movieQueries)
        val repo = repository(maxAge = 0, tmdb = tmdb)

        val rows = repo.getHome(HomeFilter.MOVIES, maxItems = 20)

        assertTrue(rows.isNotEmpty())
        assertTrue(movieQueries.all { it["certification.lte"] == "G" })
        rows.forEach { row ->
            assertEquals(listOf("1"), row.items.map { it.id })
        }
    }
}
