package com.shieldstream.tv.data.rtve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RtveLiveParserTest {

    private val parser = RtveLiveParser()

    @Test
    fun `parses live rows from index_apps json and keeps order`() {
        val raw = """
            {
              "rows": [
                {"title": "Series", "orden": 1, "moduleType": "Collection", "tipo": "ColeccionPoster", "urlContent": "https://api.rtve.es/api/collection/1.json"},
                {"title": "Eventos", "orden": 5, "moduleType": "livesCollection", "tipo": "directosTV", "urlContent": "https://api.rtve.es/api/lives/agr-directos/2.json"},
                {"title": "Portada > Ahora en directo", "orden": 2, "moduleType": "other", "tipo": "directosTV16", "urlContent": "http://api.rtve.es/api/lives/pagina-portada.json"},
                {"title": "Sin fuente", "orden": 3, "moduleType": "livesCollection", "tipo": "directosTV", "urlContent": "https://evil.example/lives.json"}
              ]
            }
        """.trimIndent()

        val rows = parser.parseLiveRowSources(raw)

        assertEquals(2, rows.size)
        assertEquals("Ahora en directo", rows[0].title)
        assertEquals("https://api.rtve.es/api/lives/pagina-portada.json", rows[0].contentUrl)
        assertEquals("Eventos", rows[1].title)
        assertEquals("https://api.rtve.es/api/lives/agr-directos/2.json", rows[1].contentUrl)
    }

    @Test
    fun `parses broadcast, peticion, fast fallback, madrid timestamps and excludes radio`() {
        val raw = """
            {
              "page": {
                "items": [
                  {
                    "id": "80745",
                    "tipo": "peticion",
                    "titulo": "Etapa 21",
                    "antetitulo": "LA VUELTA 2026",
                    "live": false,
                    "inicio": "13-09-2026 16:10:00",
                    "duracion": 235,
                    "porcentaje": 0,
                    "idAsset": "6712432",
                    "logo": "http://img.rtve.es/css/logo.png",
                    "thumb": "https://img.rtve.es/v/123/",
                    "hasDRM": true,
                    "allowedInCountry": true
                  },
                  {
                    "id": "21007",
                    "idAsset": "1688877",
                    "tipo": "broadcast",
                    "contentType": "directo",
                    "descripcion": "Canal RTVE La Promesa: disfruta de capítulos"
                  },
                  {
                    "id": "900",
                    "idAsset": "901",
                    "tipo": "broadcast",
                    "titulo": "RNE",
                    "audio": true
                  }
                ]
              }
            }
        """.trimIndent()

        val channels = parser.parseLiveChannels(raw)

        assertEquals(2, channels.size)
        val first = channels[0]
        assertEquals("80745", first.id)
        assertEquals("6712432", first.assetId)
        assertEquals("Etapa 21", first.title)
        assertEquals("La Vuelta 2026", first.category)
        assertFalse(first.isOnAir)
        assertEquals(235, first.durationMinutes)
        assertTrue(first.hasDrm)
        assertEquals(true, first.allowedInCountry)
        assertEquals("https://img.rtve.es/v/123/?w=400", first.imageUrl)
        assertEquals("https://img.rtve.es/i/?w=400&i=https%3A%2F%2Fimg.rtve.es%2Fcss%2Flogo.png", first.logoUrl)
        val expectedMillis = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Madrid") }
            .parse("13-09-2026 16:10:00")!!.time
        assertEquals(expectedMillis, first.startsAtMillis)

        val second = channels[1]
        assertEquals("Canal RTVE La Promesa", second.title)
        assertTrue(second.isOnAir)
        assertNull(second.logoUrl)
    }

    @Test
    fun `parses thematic 24-7 channels from estructuraAndroidTV2 json`() {
        val raw = """
            {
              "television": {
                "menuBloques": [
                  {
                    "menuItems": [
                      {"title": "Inicio", "tipo": "portada", "urlContent": "https://www.rtve.es/play/index_apps.json"},
                      {
                        "title": "CANALES TEMÁTICOS",
                        "tipo": "submenu",
                        "menuItems": [
                          {"title": "RTVE COCINA", "tipo": "portadaCanalTematico", "imgBackground": "https://img.rtve.es/imagenes/cocina.jpg", "urlContent": "https://api.rtve.es/api/lives/broadcasts/1.json"},
                          {"title": "Solo Play+", "tipo": "portadaCanalTematico", "subscriptor": true, "urlContent": "https://api.rtve.es/api/lives/broadcasts/2.json"}
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val thematic = parser.parseThematicChannelSources(raw)

        assertEquals(1, thematic.size)
        assertEquals("RTVE COCINA", thematic[0].title)
        assertEquals("https://img.rtve.es/imagenes/cocina.jpg", thematic[0].imageUrl)
        assertEquals("https://api.rtve.es/api/lives/broadcasts/1.json", thematic[0].contentUrl)
    }
}
