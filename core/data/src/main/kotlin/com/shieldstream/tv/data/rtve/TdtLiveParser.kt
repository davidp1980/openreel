package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface TdtTextClient {
    suspend fun fetch(url: String, etag: String?): RtveTextResponse
}

/**
 * HTTPS client for the public TDT free-to-air channel directory (`tdtchannels.com/lists/tv.json`).
 */
@Singleton
class TdtHttpClient @Inject constructor(
    private val client: OkHttpClient,
) : TdtTextClient {

    override suspend fun fetch(url: String, etag: String?): RtveTextResponse = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw IOException("Invalid HTTPS URL: $url")
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, application/vnd.apple.mpegurl, text/plain, */*")
            .header("User-Agent", USER_AGENT)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        client.newCall(request).awaitResponse { response ->
            when {
                response.code == 304 && etag != null -> RtveTextResponse(body = null, etag = etag)
                !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                else -> {
                    val body = response.body ?: throw IOException("Empty response body")
                    val source = body.source()
                    if (source.request(MAX_PAYLOAD_BYTES + 1)) {
                        throw IOException("Response exceeds max size")
                    }
                    RtveTextResponse(body = source.readUtf8(), etag = response.header("ETag"))
                }
            }
        }
    }

    private suspend fun <T> Call.awaitResponse(read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching { response.use(read) }
                    outcome.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWithException(it) },
                    )
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            },
        )
    }

    private companion object {
        const val USER_AGENT = "OpenReel-AndroidTV"
        const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024L
    }
}

/**
 * Tolerant parser for the public TDT free-to-air directory (`tv.json`).
 * Restores all playable channels with direct official HLS (`m3u8`) and DASH (`mpd`) streams,
 * organized into Nacionales, Autonómicas, and Internacionales, while skipping RTVE channels that
 * are already served with live EPG + Widevine DRM by [RtveLiveParser].
 */
class TdtLiveParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parseSections(raw: String): List<LiveTvSection> {
        val root = json.parseToJsonElement(raw).jsonObject
        val countries = root.array("countries")
            .orEmpty()
            .mapNotNull { it as? JsonObject }

        val national = mutableListOf<LiveChannel>()
        val regional = mutableListOf<LiveChannel>()
        val international = mutableListOf<LiveChannel>()

        for (country in countries) {
            val countryName = country.text("name")?.lowercase(Locale.ROOT).orEmpty()
            val ambits = country.array("ambits")
                .orEmpty()
                .mapNotNull { it as? JsonObject }
            for (ambit in ambits) {
                val ambitName = ambit.text("name") ?: continue
                val ambitLower = ambitName.lowercase(Locale.ROOT)
                val bucket = when {
                    countryName == COUNTRY_SPAIN && ambitLower in SPAIN_NATIONAL_AMBITS -> Bucket.NATIONAL
                    countryName == COUNTRY_SPAIN -> Bucket.REGIONAL
                    else -> Bucket.INTERNATIONAL
                }
                val channels = ambit.array("channels")
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                for (channelObj in channels) {
                    val channel = toLiveChannel(channelObj, bucket, ambitName) ?: continue
                    when (bucket) {
                        Bucket.NATIONAL -> national += channel
                        Bucket.REGIONAL -> regional += channel
                        Bucket.INTERNATIONAL -> international += channel
                    }
                }
            }
        }

        val organizedNational = national
            .mergeByTitle()
            .sortedBy { priorityIndex(it.title, NATIONAL_PRIORITY) }

        val organizedRegional = regional
            .mergeByTitle()

        val organizedInternational = international
            .mergeByTitle()
            .sortedBy { priorityIndex(it.title, INTERNATIONAL_PRIORITY) }

        return buildList {
            if (organizedNational.isNotEmpty()) {
                add(LiveTvSection(id = "tdt_nacionales", title = TITLE_NATIONAL, channels = organizedNational))
            }
            if (organizedRegional.isNotEmpty()) {
                add(LiveTvSection(id = "tdt_autonomicas", title = TITLE_REGIONAL, channels = organizedRegional))
            }
            if (organizedInternational.isNotEmpty()) {
                add(LiveTvSection(id = "tdt_internacionales", title = TITLE_INTERNATIONAL, channels = organizedInternational))
            }
        }
    }

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

    private fun toLiveChannel(
        channel: JsonObject,
        bucket: Bucket,
        ambitName: String,
    ): LiveChannel? {
        val name = channel.text("name") ?: return null
        if (isRtveNativeChannel(name)) return null

        val directUrls = channel.array("options")
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { option ->
                val format = option.text("format")?.lowercase(Locale.ROOT)
                if (format !in SUPPORTED_FORMATS) return@mapNotNull null
                option.text("url")?.let(::sanitizeStreamUrl)
            }
            .distinct()
        if (directUrls.isEmpty()) return null

        val logoUrl = channel.text("logo")?.let(::sanitizeLogoUrl)
        return LiveChannel(
            id = "tdt_${bucket.slug}_${slug(name)}",
            assetId = null,
            title = name,
            category = ambitName,
            imageUrl = null,
            logoUrl = logoUrl,
            isOnAir = true,
            directUrls = directUrls,
            provider = PROVIDER_TDT,
        )
    }

    private fun priorityIndex(name: String, list: List<String>): Int {
        val normalized = name.trim().lowercase(Locale.ROOT)
        val idx = list.indexOf(normalized)
        return if (idx >= 0) idx else list.size
    }

    private enum class Bucket(val slug: String, val title: String) {
        NATIONAL("nacionales", TITLE_NATIONAL),
        REGIONAL("autonomicas", TITLE_REGIONAL),
        INTERNATIONAL("internacionales", TITLE_INTERNATIONAL),
    }

    internal fun sanitizeStreamUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed
            .replace(CACHEBUSTER_REGEX, "0")
            .replace(MACRO_REGEX, "")
        val uri = runCatching { URI(cleaned) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return cleaned
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

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

    private companion object {
        const val PROVIDER_TDT = "TDT"
        const val COUNTRY_SPAIN = "spain"
        const val TITLE_NATIONAL = "TDT Nacionales"
        const val TITLE_REGIONAL = "TDT Autonómicas"
        const val TITLE_INTERNATIONAL = "TDT Internacionales"
        val SUPPORTED_FORMATS = setOf("m3u8", "mpd")
        val SPAIN_NATIONAL_AMBITS = setOf(
            "generalistas",
            "informativos",
            "deportivos",
            "infantiles",
            "eventuales",
        )
        val NATIONAL_PRIORITY = listOf(
            "clan",
            "trece",
            "euronews",
            "real madrid tv",
            "el toro tv",
            "negocios tv",
            "el país",
            "el confidencial",
            "¡hola! play",
            "canal parlamento",
            "top barça",
            "congreso de los diputados",
            "senado",
            "rne accesible",
        )
        val INTERNATIONAL_PRIORITY = listOf(
            "cnn internacional",
            "bloomberg europe",
            "sky news reino unido",
            "dw alemania",
            "bfm tv francia",
            "tv5monde francia",
            "al jazeera catar",
            "nhk world japón",
            "cbs news usa",
            "red bull tv",
            "abc news australia",
            "trt world turquía",
        )
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
        val CACHEBUSTER_REGEX = Regex("\\[CACHEBUSTER\\]", RegexOption.IGNORE_CASE)
        val MACRO_REGEX = Regex("\\[[^\\]]*\\]")
        val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
    }
}
