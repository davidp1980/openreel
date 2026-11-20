package com.shieldstream.tv.data.rtve

import org.junit.Assert.assertEquals
import org.junit.Test

class FastM3uParserTest {

    private val parser = FastM3uParser()

    @Test
    fun `parses and organizes Samsung TV Plus, Pluto TV and Plex into unified thematic sections`() {
        val samsungM3u = """
            #EXTM3U
            #EXTINF:-1 channel-id="ES1" tvg-name="24H" tvg-logo="https://img.samsung.test/24h.png" group-title="Noticias",24H
            https://jmp2.uk/stvp-24H
            #EXTINF:-1 channel-id="ES2" tvg-name="Top Cine" tvg-logo="http://img.samsung.test/cine.png" group-title="Cine",Top Cine
            https://jmp2.uk/stvp-ES2
            #EXTINF:-1 channel-id="ES3" tvg-name="Euronews" tvg-logo="https://img.samsung.test/euro.png" group-title="Noticias",Euronews
            https://jmp2.uk/stvp-ES3
            #EXTINF:-1 channel-id="ES4" tvg-name="Planeta Doc" tvg-logo="https://img.samsung.test/doc.png" group-title="Documentales",Planeta Doc
            https://jmp2.uk/stvp-ES4
        """.trimIndent()

        val plutoM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Euronews" tvg-logo="https://img.pluto.test/euro.png" group-title="Noticias",Euronews
            https://jmp2.uk/plu-euro.m3u8
            #EXTINF:-1 tvg-name="Pluto Anime" tvg-logo="https://img.pluto.test/anime.png" group-title="Anime",Pluto Anime
            https://jmp2.uk/plu-anime.m3u8
            #EXTINF:-1 tvg-name="Invalid" group-title="Cine",Invalid
            not-a-valid-url
        """.trimIndent()

        val plexM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="FailArmy" tvg-logo="https://img.plex.test/fail.png" group-title="Spain",FailArmy
            https://epg.provider.plex.tv/library/parts/123/?X-Plex-Token=abc
        """.trimIndent()

        val sections = parser.parseAndOrganize(
            listOf(
                FastPlaylistSource(provider = "Samsung TV Plus", raw = samsungM3u),
                FastPlaylistSource(provider = "Pluto TV", raw = plutoM3u),
                FastPlaylistSource(
                    provider = "Plex TV",
                    raw = plexM3u,
                    dedicatedSectionId = "fast_plex",
                    dedicatedSectionTitle = "Plex TV",
                ),
            ),
        )

        assertEquals(
            listOf("Cine", "Documentales y Crimen", "Noticias", "Infantil y Anime", "Plex TV"),
            sections.map { it.title },
        )

        val movies = sections[0].channels
        assertEquals(listOf("Top Cine"), movies.map { it.title })
        assertEquals("https://img.samsung.test/cine.png", movies.single().logoUrl)
        assertEquals("Samsung TV Plus", movies.single().category)

        val news = sections[2].channels
        assertEquals(listOf("Euronews"), news.map { it.title })
        assertEquals(
            listOf("https://jmp2.uk/stvp-ES3", "https://jmp2.uk/plu-euro.m3u8"),
            news.single().directUrls,
        )

        val kids = sections[3].channels
        assertEquals(listOf("Pluto Anime"), kids.map { it.title })
        assertEquals("Pluto TV", kids.single().provider)

        val plex = sections[4].channels
        assertEquals(listOf("FailArmy"), plex.map { it.title })
        assertEquals("fast_plex", sections[4].id)
    }
}
