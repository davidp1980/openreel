package com.shieldstream.tv.domain

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Rewrites `img.rtve.es` image URLs to the canonical pre-resized dimensions used by RTVE Play's
 * web/TV clients so responses hit RTVE's image cache instead of triggering slow on-the-fly resizes.
 */
object RtveImageSizing {
    private const val HOST = "https://img.rtve.es/"
    const val CARD_PX = 400
    const val LARGE_PX = 1600
    private const val LARGE_FROM_PX = 800
    private const val LOGO_PX = 320

    private val videoPath = Regex("""^v/(\d+)(?:/([A-Za-z0-9]+))?/?$""")
    private val programPath = Regex("""^p/(\d+)/?$""")

    fun sized(url: String, widthPx: Int = CARD_PX): String {
        if (!url.startsWith(HOST) || widthPx <= 0) return url
        val rest = url.removePrefix(HOST)
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val width = if (widthPx >= LARGE_FROM_PX) LARGE_PX else CARD_PX

        videoPath.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            val variant = match.groupValues[2]
            return when {
                variant.equals("vertical", true) || variant.equals("vertical2", true) ->
                    "${HOST}v/$id/${variant.lowercase()}/?h=$CARD_PX"
                variant.equals("horizontal2", true) -> "${HOST}v/$id/horizontal2?w=$width"
                variant.isEmpty() || variant.equals("horizontal", true) -> "${HOST}v/$id/?w=$width"
                else -> "${HOST}v/$id/$variant/?w=$CARD_PX"
            }
        }
        programPath.matchEntire(path)?.let { match ->
            val id = match.groupValues[1]
            val variant = (query["imgProgApi"] ?: "imgPortada").lowercase()
            return when {
                variant == "logo" -> "${HOST}p/$id/?w=$LOGO_PX&imgProgApi=logo"
                variant.startsWith("imgportada") -> "${HOST}p/$id/$variant/?w=$width"
                else -> "${HOST}p/$id/$variant/?h=$CARD_PX"
            }
        }
        if (path.startsWith("p/")) return url
        val original = when {
            path.startsWith("i/") -> query["i"]?.let { raw ->
                runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
            }?.takeIf { it.isNotBlank() }
            path.startsWith("imagenes/") || path.startsWith("css/") -> HOST + path
            else -> null
        } ?: return url
        return "${HOST}i/?w=$width&i=${URLEncoder.encode(original, "UTF-8")}"
    }
}
