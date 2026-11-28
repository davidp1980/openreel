package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.network.api.TmdbApi
import com.shieldstream.tv.network.dto.TmdbMovieDetailDto
import com.shieldstream.tv.network.dto.TmdbMovieDto
import com.shieldstream.tv.network.dto.TmdbPaginated
import com.shieldstream.tv.network.dto.TmdbSearchResultDto
import com.shieldstream.tv.network.dto.TmdbTvDetailDto
import com.shieldstream.tv.network.dto.TmdbTvDto
import com.shieldstream.tv.strings.Texts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeExtrasSourceTest {

    @Test
    fun `classic category uses pre-2000 highly rated movie filters for every row`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore>()
        val queries = mutableListOf<Map<String, String>>()
        every { store.tmdbKey } returns "tmdb-key"
        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(queries)) } returns TmdbPaginated(
            results = listOf(TmdbMovieDto(id = 1, poster_path = "/poster.jpg")),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = HomeExtrasSource(tmdb, context, store, mockk<Texts>(relaxed = true))

        val rows = source.categoryRows(Category.CLASSICS, HomeFilter.MOVIES)

        assertEquals(3, rows.size)
        assertEquals(listOf("1", "2", "3"), queries.map { it.getValue("page") }.sorted())
        queries.forEach { query ->
            assertEquals("false", query["include_adult"])
            assertEquals("popularity.desc", query["sort_by"])
            assertEquals("1999-12-31", query["primary_release_date.lte"])
            assertEquals("8.0", query["vote_average.gte"])
            assertEquals("500", query["vote_count.gte"])
        }
        coVerify(exactly = 0) { tmdb.discoverTvQuery(any(), any()) }
    }

    @Test
    fun `movies featured billboard uses movie sources and avoids duplicates from home`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" }
        coEvery { tmdb.trendingAll("tmdb-key", 1) } returns TmdbPaginated(
            results = listOf(
                TmdbSearchResultDto(
                    id = 100,
                    media_type = "movie",
                    title = "Home Movie",
                    poster_path = "/p100.jpg",
                    backdrop_path = "/b100.jpg",
                    vote_average = 8.2,
                    vote_count = 1000,
                ),
                TmdbSearchResultDto(
                    id = 200,
                    media_type = "tv",
                    name = "Home Show",
                    poster_path = "/p200.jpg",
                    backdrop_path = "/b200.jpg",
                    vote_average = 8.4,
                    vote_count = 900,
                ),
            ),
        )
        coEvery { tmdb.trendingAllWeek("tmdb-key", 1) } returns TmdbPaginated()
        coEvery { tmdb.nowPlayingMovies("tmdb-key", any()) } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 100, title = "Home Movie", poster_path = "/p100.jpg", backdrop_path = "/b100.jpg"),
                TmdbMovieDto(id = 101, title = "Cinema Movie", poster_path = "/p101.jpg", backdrop_path = "/b101.jpg", release_date = "2026-11-15"),
            ),
        )
        coEvery { tmdb.trendingMovies("tmdb-key", 1) } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 102, title = "Trending Movie", poster_path = "/p102.jpg", backdrop_path = "/b102.jpg"),
            ),
        )
        coEvery { tmdb.topRatedMovies("tmdb-key", 1) } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 103, title = "Acclaimed Movie", poster_path = "/p103.jpg", backdrop_path = "/b103.jpg", vote_average = 8.8, vote_count = 2000),
            ),
        )
        coEvery { tmdb.movieDetails(any(), any(), any(), any()) } answers {
            val id = firstArg<Int>()
            TmdbMovieDetailDto(id = id, title = "Movie $id", poster_path = "/p$id.jpg", backdrop_path = "/b$id.jpg")
        }
        coEvery { tmdb.tvDetails(any(), any(), any(), any()) } answers {
            val id = firstArg<Int>()
            TmdbTvDetailDto(id = id, name = "Show $id", poster_path = "/p$id.jpg", backdrop_path = "/b$id.jpg")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = HomeExtrasSource(tmdb, context, store, mockk<Texts>(relaxed = true))

        val homeFeatured = source.featured(HomeFilter.ALL)
        val moviesFeatured = source.featured(HomeFilter.MOVIES)

        val homeMovieIds = homeFeatured.filter { it.item.type == MediaType.MOVIE }.map { it.item.id }.toSet()
        val moviesIds = moviesFeatured.map { it.item.id }.toSet()
        assertEquals(setOf("100"), homeMovieIds)
        assertEquals(setOf("101", "102", "103"), moviesIds)
    }

    @Test
    fun `documentaries featured billboard queries genre 99 for adults and enforces kids safety filters for kids`() = runTest {
        val tmdb = mockk<TmdbApi>()
        val store = mockk<TokenStore> { every { tmdbKey } returns "tmdb-key" }
        val movieQueries = mutableListOf<Map<String, String>>()
        val tvQueries = mutableListOf<Map<String, String>>()

        coEvery { tmdb.discoverMovieQuery("tmdb-key", capture(movieQueries)) } returns TmdbPaginated(
            results = listOf(
                TmdbMovieDto(id = 301, title = "Doc Movie 1", poster_path = "/p301.jpg", backdrop_path = "/b301.jpg"),
                TmdbMovieDto(id = 302, title = "Doc Movie 2", poster_path = "/p302.jpg", backdrop_path = "/b302.jpg"),
            ),
        )
        coEvery { tmdb.discoverTvQuery("tmdb-key", capture(tvQueries)) } returns TmdbPaginated(
            results = listOf(
                TmdbTvDto(id = 401, name = "Doc Series 1", poster_path = "/p401.jpg", backdrop_path = "/b401.jpg"),
                TmdbTvDto(id = 402, name = "Doc Series 2", poster_path = "/p402.jpg", backdrop_path = "/b402.jpg"),
            ),
        )
        coEvery { tmdb.movieDetails(any(), any(), any(), any()) } answers {
            val id = firstArg<Int>()
            TmdbMovieDetailDto(id = id, title = "Movie $id", poster_path = "/p$id.jpg", backdrop_path = "/b$id.jpg")
        }
        coEvery { tmdb.tvDetails(any(), any(), any(), any()) } answers {
            val id = firstArg<Int>()
            TmdbTvDetailDto(id = id, name = "Show $id", poster_path = "/p$id.jpg", backdrop_path = "/b$id.jpg")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = HomeExtrasSource(tmdb, context, store, mockk<Texts>(relaxed = true))

        val adultFeatured = source.featured(HomeFilter.DOCUMENTARIES)
        assertEquals(setOf("301", "401", "302", "402"), adultFeatured.map { it.item.id }.toSet())
        val adultMovieQueries = movieQueries.toList()
        val adultTvQueries = tvQueries.toList()
        assertEquals(2, adultMovieQueries.size)
        assertEquals(2, adultTvQueries.size)
        adultMovieQueries.forEach { assertEquals("99", it["with_genres"]) }
        adultTvQueries.forEach { assertEquals("99", it["with_genres"]) }

        movieQueries.clear()
        tvQueries.clear()

        val kidsFeatured = source.kidsFeatured(HomeFilter.DOCUMENTARIES)
        assertEquals(setOf("301", "401", "302", "402"), kidsFeatured.map { it.item.id }.toSet())
        assertEquals(2, movieQueries.size)
        assertEquals(1, tvQueries.size)
        movieQueries.forEach { query ->
            assertEquals("99", query["with_genres"])
            assertEquals("80,27,53,10752", query["without_genres"])
            assertEquals("PG", query["certification.lte"])
            assertEquals("US", query["certification_country"])
            assertEquals("false", query["include_adult"])
        }
        tvQueries.forEach { query ->
            assertEquals("99,10751", query["with_genres"])
            assertEquals("80,27,53,10768", query["without_genres"])
            assertEquals("false", query["include_adult"])
        }
    }
}
