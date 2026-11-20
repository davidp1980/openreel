package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.domain.RtveHostPolicy
import com.shieldstream.tv.domain.RtveImageSizing
import com.shieldstream.tv.model.LiveChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class LiveRowSource(
    val title: String,
    val order: Int,
    val contentUrl: String,
)

data class ThematicChannelSource(
    val title: String,
    val imageUrl: String?,
    val contentUrl: String,
)

/**
 * Tolerant parser for RTVE's live TV JSON feeds (`index_apps.json`, `estructuraAndroidTV2.json`,
 * `lives/...`, and `broadcasts/{id}.json`).
 */
class RtveLiveParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val hostPolicy: RtveHostPolicy = RtveHostPolicy(),
) {
    fun parseLiveRowSources(raw: String): List<LiveRowSource> {
        val root = json.parseToJsonElement(raw).jsonObject
        return root.array("rows")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { row ->
                val moduleType = row.text("moduleType")
                val presentation = row.text("tipo")
                val isLiveRow = moduleType.equals("livesCollection", ignoreCase = true) ||
                    presentation?.lowercase()?.startsWith("directos") == true
                if (!isLiveRow) return@mapNotNull null
                val url = row.text("urlContent")?.let(hostPolicy::sanitize) ?: return@mapNotNull null
                LiveRowSource(
                    title = row.text("title", "name")?.let(::editorialTitle).orEmpty(),
                    order = row.number("orden", "order") ?: Int.MAX_VALUE,
                    contentUrl = url,
                )
            }
            .sortedBy(LiveRowSource::order)
            .distinctBy(LiveRowSource::contentUrl)
    }

    fun parseThematicChannelSources(raw: String): List<ThematicChannelSource> {
        val root = json.parseToJsonElement(raw).jsonObject
        val blocks = root.obj("television")?.array("menuBloques")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
        val sources = mutableListOf<ThematicChannelSource>()
        fun collect(items: JsonArray?) {
            items.orEmpty().mapNotNull { it as? JsonObject }.forEach { item ->
                if (item.flag("subscriptor") == true) return@forEach
                val tipo = item.text("tipo")
                if (tipo == "portadaCanalTematico") {
                    val title = item.text("title") ?: return@forEach
                    val url = item.text("urlContent")?.let(hostPolicy::sanitize) ?: return@forEach
                    sources += ThematicChannelSource(
                        title = title,
                        imageUrl = item.text("imgBackground")?.let(hostPolicy::sanitize),
                        contentUrl = url,
                    )
                } else if (tipo == "submenu") {
                    collect(item.array("menuItems"))
                }
            }
        }
        blocks.forEach { block ->
            if (block.flag("subscriptor") != true) {
                collect(block.array("menuItems"))
            }
        }
        return sources.distinctBy(ThematicChannelSource::contentUrl)
    }

    fun parseLiveChannels(
        raw: String,
        fallbackTitle: String? = null,
        fallbackImageUrl: String? = null,
    ): List<LiveChannel> {
        val root = json.parseToJsonElement(raw).jsonObject
        val pageItems = root.obj("page")?.array("items")
            ?: root.array("items")
            ?: JsonArray(emptyList())
        val collection = (pageItems.singleOrNull() as? JsonObject)
            ?.takeIf { it.array("collectionItems") != null }
        val rawObjects = (collection?.array("collectionItems") ?: pageItems)
            .mapNotNull { it as? JsonObject }

        return rawObjects
            .mapNotNull { toLiveChannel(it, fallbackTitle, fallbackImageUrl) }
            .distinctBy(LiveChannel::key)
            .distinctBy(LiveChannel::id)
    }

    private fun toLiveChannel(
        item: JsonObject,
        fallbackTitle: String?,
        fallbackImageUrl: String?,
    ): LiveChannel? {
        if (item.flag("audio") == true) return null
        val editorialType = item.text("tipo")
        val contentType = item.text("contentType")
        val assetId = item.text("idAsset")
        val isLive = editorialType?.lowercase() in LIVE_EDITORIAL_TYPES ||
            contentType?.lowercase() in LIVE_CONTENT_TYPES ||
            (contentType == null && assetId != null)
        if (!isLive) return null

        val id = item.text("id", "uid") ?: assetId ?: return null
        val title = item.text("titulo", "title", "name", "longTitle", "shortTitle")
            ?: fallbackTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackTitleFromItem(item)
            ?: return null

        val rawImage = firstAllowed(
            item.text("thumb", "imagen", "imgPortada", "imgPortada2", "thumbnail"),
            item.obj("previews")?.text("horizontal2", "horizontal"),
            item.text("imageSEO", "imgBackground"),
            fallbackImageUrl,
        )
        val rawLogo = item.text("logo")?.let(hostPolicy::sanitize)

        return LiveChannel(
            id = id,
            assetId = assetId,
            title = title,
            category = item.text("antetitulo")?.let(::titleCase),
            imageUrl = rawImage?.let { RtveImageSizing.sized(it, RtveImageSizing.CARD_PX) },
            logoUrl = rawLogo?.let { RtveImageSizing.sized(it, LOGO_WIDTH_PX) },
            isOnAir = item.flag("live") ?: true,
            startsAtMillis = item.text("inicio")?.let(::parseMadridTime),
            durationMinutes = item.number("duracion")?.takeIf { it > 0 },
            progressPercent = item.number("porcentaje"),
            allowedInCountry = item.flag("allowedInCountry"),
            paid = item.flag("paidContent") == true,
            hasDrm = item.flag("hasDRM") == true,
        )
    }

    private fun fallbackTitleFromItem(item: JsonObject): String? {
        val description = item.text("descripcion", "description")
            ?.substringBefore(':')
            ?.substringBefore('.')
            ?.trim()
            ?.takeIf { it.length in 3..60 }
        return description ?: item.text("permalink")
            ?.split('-')
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseMadridTime(value: String): Long? = runCatching {
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Madrid") }
            .parse(value)?.time
    }.getOrNull()

    private fun editorialTitle(value: String): String =
        value.substringAfterLast(" > ").trim().ifEmpty { value }

    private fun titleCase(value: String): String =
        if (value != value.uppercase()) {
            value
        } else {
            value.lowercase().split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }

    private fun firstAllowed(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { it?.let(hostPolicy::sanitize) }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (get(key) as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun JsonObject.number(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        val prim = get(key) as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.flag(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

    private companion object {
        val LIVE_EDITORIAL_TYPES = setOf("broadcast", "peticion", "directo")
        val LIVE_CONTENT_TYPES = setOf("directo", "live", "broadcast")
        const val LOGO_WIDTH_PX = 320
    }
}
