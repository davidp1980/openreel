package com.shieldstream.tv.data.rtve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdtLiveParserTest {

    private val parser = TdtLiveParser()

    @Test
    fun `restores all playable national, regional and international channels organized by bucket`() {
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
                          "name": "TRECE",
                          "logo": "http://img.example.com/trece.png",
                          "options": [
                            {"format": "stream", "url": "https://www.trecetv.es/directo"},
                            {"format": "m3u8", "url": "https://live.example.com/trece/master.m3u8"},
                            {"format": "mpd", "url": "https://live.example.com/trece/manifest.mpd"}
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
                            {"format": "m3u8", "url": "https://rtvehlslive.rtve.es/clan.m3u8?cb=[CACHEBUSTER]&did=[DEVICE_ID]"},
                            {"format": "youtube", "url": "https://www.youtube.com/embed/live"}
                          ]
                        }
                      ]
                    },
                    {
                      "name": "C. de Madrid",
                      "channels": [
                        {
                          "name": "Canal 33 Madrid",
                          "logo": "https://img.example.com/c33.png",
                          "options": [
                            {"format": "m3u8", "url": "https://live.example.com/c33.m3u8"}
                          ]
                        }
                      ]
                    },
                    {
                      "name": "Andalucía",
                      "channels": [
                        {
                          "name": "Canal Sur",
                          "logo": "https://img.example.com/canalsur.png",
                          "options": [
                            {"format": "m3u8", "url": "https://live.example.com/canalsur.m3u8"}
                          ]
                        }
                      ]
                    }
                  ]
                },
                {
                  "name": "International",
                  "ambits": [
                    {
                      "name": "Int. Europa",
                      "channels": [
                        {
                          "name": "Obscure Local TV",
                          "logo": "https://img.example.com/obscure.png",
                          "options": [
                            {"format": "m3u8", "url": "https://live.example.com/obscure.m3u8"}
                          ]
                        },
                        {
                          "name": "CNN Internacional",
                          "logo": "https://img.example.com/cnn.png",
                          "options": [
                            {"format": "m3u8", "url": "https://live.example.com/cnn.m3u8"}
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

        assertEquals(
            listOf("TDT Nacionales", "TDT Autonómicas", "TDT Internacionales"),
            sections.map { it.title },
        )

        val national = sections[0].channels
        assertEquals(listOf("Clan", "TRECE"), national.map { it.title })
        assertTrue(national[0].directUrls.single().none { it == '[' || it == ']' })
        assertEquals("https://rtvehlslive.rtve.es/clan.m3u8?cb=0&did=", national[0].directUrls.single())

        val trece = national[1]
        assertEquals("https://img.example.com/trece.png", trece.logoUrl)
        assertEquals("Generalistas", trece.category)
        assertEquals("TDT", trece.provider)
        assertEquals(
            listOf(
                "https://live.example.com/trece/master.m3u8",
                "https://live.example.com/trece/manifest.mpd",
            ),
            trece.directUrls,
        )

        val regional = sections[1].channels
        assertEquals(listOf("Canal 33 Madrid", "Canal Sur"), regional.map { it.title })
        assertEquals(listOf("C. de Madrid", "Andalucía"), regional.map { it.category })

        val international = sections[2].channels
        assertEquals(listOf("CNN Internacional", "Obscure Local TV"), international.map { it.title })
    }
}
