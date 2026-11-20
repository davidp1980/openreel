package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import java.net.URI
import java.util.Locale

data class FastPlaylistSource(
    val provider: String,
    val raw: String,
    val dedicatedSectionId: String? = null,
    val dedicatedSectionTitle: String? = null,
)

/**
 * Tolerant M3U/M3U8 parser for free ad-supported streaming TV (FAST) playlists such as
 * Samsung TV Plus, Pluto TV, and Plex Live TV.
 *
 * Normalizes provider-specific `group-title` categories into a unified, ordered set of thematic
 * sections and merges duplicate channel names within the same section into fallback stream queues.
 */
class FastM3uParser {

    fun parseAndOrganize(sources: List<FastPlaylistSource>): List<LiveTvSection> {
        val themedBuckets = FastCategory.entries.associateWith { mutableListOf<LiveChannel>() }
        val dedicatedSections = LinkedHashMap<String, Pair<String, MutableList<LiveChannel>>>()

        for (source in sources) {
            val entries = parseEntries(source.raw, source.provider)
            val dedicatedId = source.dedicatedSectionId
            val dedicatedTitle = source.dedicatedSectionTitle
            if (dedicatedId != null && dedicatedTitle != null) {
                val bucket = dedicatedSections.getOrPut(dedicatedId) {
                    dedicatedTitle to mutableListOf()
                }.second
                for (entry in entries) {
                    bucket += entry.toLiveChannel(sectionSlug = dedicatedId)
                }
            } else {
                for (entry in entries) {
                    val category = classifyCategory(entry.groupTitle)
                    themedBuckets.getValue(category) += entry.toLiveChannel(sectionSlug = category.id)
                }
            }
        }

        return buildList {
            for (category in FastCategory.entries) {
                val merged = themedBuckets.getValue(category).mergeByTitle()
                if (merged.isNotEmpty()) {
                    add(
                        LiveTvSection(
                            id = category.id,
                            title = category.title,
                            channels = merged,
                        ),
                    )
                }
            }
            for ((sectionId, pair) in dedicatedSections) {
                val (title, list) = pair
                val merged = list.mergeByTitle()
                if (merged.isNotEmpty()) {
                    add(
                        LiveTvSection(
                            id = sectionId,
                            title = title,
                            channels = merged,
                        ),
                    )
                }
            }
        }
    }

    internal fun parseEntries(raw: String, provider: String): List<ParsedEntry> {
        val lines = raw.lineSequence()
        val result = mutableListOf<ParsedEntry>()
        var pendingMeta: ExtInfMeta? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingMeta = parseExtInf(line)
                continue
            }
            if (line.startsWith("#")) continue

            val meta = pendingMeta ?: continue
            pendingMeta = null

            val title = meta.title
            if (title.isBlank() || isRtveNativeChannel(title)) continue
            val streamUrl = sanitizeStreamUrl(line) ?: continue
            val logoUrl = meta.logoUrl?.let(::sanitizeLogoUrl)

            result += ParsedEntry(
                provider = provider,
                title = title,
                groupTitle = meta.groupTitle,
                logoUrl = logoUrl,
                streamUrl = streamUrl,
            )
        }
        return result
    }

    private fun parseExtInf(line: String): ExtInfMeta {
        val attrs = HashMap<String, String>()
        for (match in ATTR_REGEX.findAll(line)) {
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            val value = match.groupValues[2].trim()
            if (value.isNotEmpty()) {
                attrs[key] = value
            }
        }
        val commaTitle = line.substringAfterLast(',', "").trim()
        val title = commaTitle.ifEmpty { attrs["tvg-name"].orEmpty() }.trim()
        return ExtInfMeta(
            title = title,
            groupTitle = attrs["group-title"],
            logoUrl = attrs["tvg-logo"],
        )
    }

    private fun ParsedEntry.toLiveChannel(sectionSlug: String): LiveChannel = LiveChannel(
        id = "${sectionSlug}_${slug(provider)}_${slug(title)}",
        assetId = null,
        title = title,
        category = provider,
        imageUrl = null,
        logoUrl = logoUrl,
        isOnAir = true,
        directUrls = listOf(streamUrl),
        provider = provider,
    )

    private fun List<LiveChannel>.mergeByTitle(): List<LiveChannel> {
        val byTitle = LinkedHashMap<String, LiveChannel>()
        for (channel in this) {
            val key = channel.title.trim().lowercase(Locale.ROOT)
            val existing = byTitle[key]
            if (existing == null) {
                byTitle[key] = channel
            } else {
                byTitle[key] = existing.copy(
                    logoUrl = existing.logoUrl ?: channel.logoUrl,
                    directUrls = (existing.directUrls + channel.directUrls).distinct(),
                )
            }
        }
        return byTitle.values.toList()
    }

    internal fun classifyCategory(groupTitle: String?): FastCategory {
        val normalized = groupTitle?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            normalized.contains("cine") || normalized.contains("movie") || normalized.contains("pelíc") || normalized.contains("pelic") ->
                FastCategory.MOVIES
            normalized.contains("serie") || normalized.contains("telenovela") || normalized.contains("policiac") || normalized.contains("drama") ->
                FastCategory.SERIES
            normalized.contains("entreten") || normalized.contains("comedia") || normalized.contains("comedy") ||
                normalized.contains("reality") || normalized.contains("nuevo") || normalized.contains("lifestyle") ->
                FastCategory.ENTERTAINMENT
            normalized.contains("docu") || normalized.contains("cultura") || normalized.contains("crimen") ||
                normalized.contains("crime") || normalized.contains("viaje") || normalized.contains("cocina") ||
                normalized.contains("historia") || normalized.contains("ciencia") || normalized.contains("naturaleza") ->
                FastCategory.DOCS_AND_CRIME
            normalized.contains("deport") || normalized.contains("sport") ->
                FastCategory.SPORTS
            normalized.contains("notic") || normalized.contains("news") || normalized.contains("informat") ->
                FastCategory.NEWS
            normalized.contains("niñ") || normalized.contains("infantil") || normalized.contains("kids") || normalized.contains("anime") ->
                FastCategory.KIDS_AND_ANIME
            normalized.contains("músic") || normalized.contains("music") || normalized.contains("mtv") ->
                FastCategory.MUSIC
            else -> FastCategory.OTHER
        }
    }

    internal fun sanitizeStreamUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return trimmed
    }

    internal fun sanitizeLogoUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val https = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://${trimmed.substring(7)}"
        } else {
            trimmed
        }
        val uri = runCatching { URI(https) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        return https
    }

    private fun isRtveNativeChannel(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)
        if (normalized.endsWith("(rtve)")) return true
        return normalized in RTVE_NATIVE_NAMES
    }

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(NON_ALPHANUM_REGEX, "_")
            .trim('_')
            .ifEmpty { "ch" }

    private data class ExtInfMeta(
        val title: String,
        val groupTitle: String?,
        val logoUrl: String?,
    )

    internal data class ParsedEntry(
        val provider: String,
        val title: String,
        val groupTitle: String?,
        val logoUrl: String?,
        val streamUrl: String,
    )

    enum class FastCategory(val id: String, val title: String) {
        MOVIES("fast_cine", "Cine"),
        SERIES("fast_series", "Series"),
        ENTERTAINMENT("fast_entretenimiento", "Entretenimiento"),
        DOCS_AND_CRIME("fast_documentales", "Documentales y Crimen"),
        SPORTS("fast_deportes", "Deportes"),
        NEWS("fast_noticias", "Noticias"),
        KIDS_AND_ANIME("fast_infantil", "Infantil y Anime"),
        MUSIC("fast_musica", "Música"),
        OTHER("fast_otros", "Otros canales"),
    }

    private companion object {
        val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
        val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
        val RTVE_NATIVE_NAMES = setOf(
            "la 1",
            "la 2",
            "24h",
            "canal 24h",
            "teledeporte",
            "tdp",
            "+24",
            "+tdp",
        )
    }
}
