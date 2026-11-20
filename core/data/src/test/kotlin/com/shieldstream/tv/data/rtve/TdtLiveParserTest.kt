package com.shieldstream.tv.data.rtve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdtLiveParserTest {

    private val parser = TdtLiveParser()

    @Test
    fun `parses free-to-air channels, filters non-direct options, sanitizes urls, and skips native rtve duplicates`() {
        val raw = """
            {
              "countries": [
                {
                  "name": "Spain",
                  "ambits": [
                    {
                      "name": "Generalistas",
                      "channels": [
                        {
                          "name": "La 1",
                          "logo": "https://img.example.com/la1.png",
                          "options": [
                            {"format": "m3u8", "url": "https://rtvehlslive.rtve.es/la1.m3u8"}
                          ]
                        },
                        {
                          "name": "Somos Cine (RTVE)",
                          "logo": "https://img.example.com/cine.png",
                          "options": [
                            {"format": "m3u8", "url": "https://rtvehlslive.rtve.es/cine.m3u8"}
                          ]
                        },
                        {
                          "name": "Antena 3",
                          "logo": "http://img.example.com/a3.png",
                          "options": [
                            {"format": "stream", "url": "https://www.atresplayer.com/directos/antena3/"},
                            {"format": "m3u8", "url": "https://live.example.com/a3/master.m3u8"},
                            {"format": "mpd", "url": "https://live.example.com/a3/manifest.mpd"}
                          ]
                        }
                      ]
                    },
                    {
                      "name": "Infantiles",
                      "channels": [
                        {
                          "name": "Clan",
                          "logo": "https://img.example.com/clan.png",
                          "options": [
                            {"format": "m3u8", "url": "https://rtvehlslive.rtve.es/clan.m3u8"}
                          ]
                        },
                        {
                          "name": "Runtime Kids",
                          "logo": "https://img.example.com/runtime.png",
                          "options": [
                            {"format": "m3u8", "url": "https://stream.runtime.tv/kids.m3u8?cb=[CACHEBUSTER]&did=[DEVICE_ID]"},
                            {"format": "youtube", "url": "https://www.youtube.com/embed/live"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val sections = parser.parseSections(raw)

        assertEquals(2, sections.size)
        assertEquals("Generalistas", sections[0].title)
        assertEquals(1, sections[0].channels.size)

        val antena3 = sections[0].channels.single()
        assertEquals("Antena 3", antena3.title)
        assertEquals("https://img.example.com/a3.png", antena3.logoUrl)
        assertEquals("TDT", antena3.provider)
        assertEquals(
            listOf(
                "https://live.example.com/a3/master.m3u8",
                "https://live.example.com/a3/manifest.mpd",
            ),
            antena3.directUrls,
        )

        assertEquals("Infantiles", sections[1].title)
        assertEquals(2, sections[1].channels.size)
        assertEquals("Clan", sections[1].channels[0].title)
        val runtime = sections[1].channels[1]
        assertEquals("Runtime Kids", runtime.title)
        assertTrue(runtime.directUrls.single().none { it == '[' || it == ']' })
        assertEquals("https://stream.runtime.tv/kids.m3u8?cb=0&did=", runtime.directUrls.single())
    }
}
