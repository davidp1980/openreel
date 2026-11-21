package com.shieldstream.tv.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.CatalogRow
import com.shieldstream.tv.model.FeaturedBadge
import com.shieldstream.tv.model.FeaturedItem
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.RowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDiskCacheTest {

    private lateinit var context: Context

    @Before
    fun clear() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "home_cache").deleteRecursively()
    }

    @Test
    fun `rows and featured round trip through disk cache`() {
        val cache = HomeDiskCache(context)
        val item = CatalogItem(
            id = "42",
            imdbId = "tt0042",
            type = MediaType.TV,
            title = "Show",
            overview = "Overview",
            posterUrl = "https://img/p.jpg",
            backdropUrl = "https://img/b.jpg",
            year = 2024,
            rating = 8.5f,
            genres = listOf("Drama", "Sci-Fi"),
            releaseDate = "2024-05-01",
        )
        val rows = listOf(CatalogRow("popular", "Popular", listOf(item), RowStyle.TOP10))
        val featured = listOf(FeaturedItem(item, FeaturedBadge.POPULAR, "https://img/l.png", "Drama", "45 min", "16+"))

        cache.writeRows("home_ALL_p2_ENGLISH", rows)
        cache.writeFeatured("featured_ALL_ENGLISH", featured)

        val readRows = cache.readRows("home_ALL_p2_ENGLISH")
        val readFeatured = cache.readFeatured("featured_ALL_ENGLISH")

        assertEquals(rows, readRows?.value)
        assertEquals(featured, readFeatured?.value)
        assertNull(cache.readRows("missing_key"))
    }
}
