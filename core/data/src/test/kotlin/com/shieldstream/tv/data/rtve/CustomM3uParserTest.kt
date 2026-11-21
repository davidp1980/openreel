package com.shieldstream.tv.data.rtve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomM3uParserTest {

    private val parser = CustomM3uParser()

    @Test
    fun `parses M3U entries grouped by group-title preserving order and merging duplicates`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news1" tvg-logo="http://cdn.example.com/news1.png" group-title="Noticias",Canal Noticias
            https://stream.example.com/news1-primary.m3u8
            #EXTINF:-1 tvg-logo="https://cdn.example.com/news1-hd.png" group-title="Noticias",Canal Noticias
            https://stream.example.com/news1-backup.m3u8
            #EXTINF:-1 group-title="Deportes",Deportes HD
            http://stream.example.com/sports.m3u8
            #EXTINF:-1,Sin Grupo
            https://stream.example.com/ungrouped.m3u8
        """.trimIndent()

        val sections = parser.parseSections(m3u)

        assertEquals(listOf("Noticias", "Deportes", "M3U"), sections.map { it.title })
        assertTrue(sections.all { it.id.startsWith(CustomM3uParser.SECTION_PREFIX) })

        val news = sections[0].channels.single()
        assertEquals("Canal Noticias", news.title)
        assertEquals("https://cdn.example.com/news1.png", news.logoUrl)
        assertEquals(
            listOf(
                "https://stream.example.com/news1-primary.m3u8",
                "https://stream.example.com/news1-backup.m3u8",
            ),
            news.directUrls,
        )
        assertEquals("M3U", news.provider)

        assertEquals("Deportes HD", sections[1].channels.single().title)
        assertEquals("Sin Grupo", sections[2].channels.single().title)
    }

    @Test
    fun `ignores invalid URLs, blank titles, and non-http schemes`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Test",Bad Scheme
            rtmp://stream.example.com/live
            #EXTINF:-1 group-title="Test",
            https://stream.example.com/notitle.m3u8
            #EXTINF:-1 tvg-name="Fallback Name" group-title="Test",
            https://stream.example.com/fallback.m3u8
        """.trimIndent()

        val sections = parser.parseSections(m3u)

        assertEquals(1, sections.size)
        assertEquals("Fallback Name", sections.single().channels.single().title)
    }

    @Test
    fun `returns empty list for non-m3u or empty input`() {
        assertTrue(parser.parseSections("").isEmpty())
        assertTrue(parser.parseSections("#EXTM3U\n# just a comment").isEmpty())
    }
}
