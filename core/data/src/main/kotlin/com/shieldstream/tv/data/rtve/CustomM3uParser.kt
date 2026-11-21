package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import java.net.URI
import java.util.Locale

/**
 * Tolerant M3U/M3U8 parser for user-imported live TV playlists.
 *
 * Groups channels by their `group-title` attribute in order of appearance (falling back to
 * [DEFAULT_GROUP_TITLE] when absent) and merges duplicate channel titles within the same group
 * into fallback stream queues.
 */
class CustomM3uParser {

    fun parseSections(
        raw: String,
        defaultGroupTitle: String = DEFAULT_GROUP_TITLE,
    ): List<LiveTvSection> {
        val entries = parseEntries(raw)
        if (entries.isEmpty()) return emptyList()

        val groups = LinkedHashMap<String, Pair<String, MutableList<LiveChannel>>>()
        for (entry in entries) {
            val groupLabel = entry.groupTitle?.trim()?.takeIf { it.isNotEmpty() } ?: defaultGroupTitle
            val groupKey = groupLabel.lowercase(Locale.ROOT)
            val sectionSlug = "${SECTION_PREFIX}${slug(groupLabel)}"
            val bucket = groups.getOrPut(groupKey) {
                groupLabel to mutableListOf()
            }.second
            bucket += entry.toLiveChannel(sectionSlug = sectionSlug, groupLabel = groupLabel)
        }

        val usedSectionIds = HashSet<String>()
        return buildList {
            for ((_, pair) in groups) {
                val (title, list) = pair
                val merged = list.mergeByTitle()
                if (merged.isEmpty()) continue
                val baseId = "${SECTION_PREFIX}${slug(title)}"
                var sectionId = baseId
                var suffix = 2
                while (!usedSectionIds.add(sectionId)) {
                    sectionId = "${baseId}_${suffix++}"
                }
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

    internal fun parseEntries(raw: String): List<ParsedEntry> {
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
            if (title.isBlank()) continue
            val streamUrl = sanitizeStreamUrl(line) ?: continue
            val logoUrl = meta.logoUrl?.let(::sanitizeLogoUrl)

            result += ParsedEntry(
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

    private fun ParsedEntry.toLiveChannel(sectionSlug: String, groupLabel: String): LiveChannel = LiveChannel(
        id = "${sectionSlug}_${slug(title)}",
        assetId = null,
        title = title,
        category = groupLabel,
        imageUrl = null,
        logoUrl = logoUrl,
        isOnAir = true,
        directUrls = listOf(streamUrl),
        provider = PROVIDER_M3U,
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
        val title: String,
        val groupTitle: String?,
        val logoUrl: String?,
        val streamUrl: String,
    )

    companion object {
        const val SECTION_PREFIX = "m3u_"
        const val PROVIDER_M3U = "M3U"
        const val DEFAULT_GROUP_TITLE = "M3U"
        private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
        private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
    }
}
