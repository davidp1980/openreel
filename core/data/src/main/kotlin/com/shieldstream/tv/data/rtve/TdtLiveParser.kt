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
            .header("Accept", "application/json")
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
 * Keeps only direct official HLS (`m3u8`) and DASH (`mpd`) streams and skips RTVE channels that
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

        val sections = mutableListOf<LiveTvSection>()
        for (country in countries) {
            val ambits = country.array("ambits")
                .orEmpty()
                .mapNotNull { it as? JsonObject }
            for (ambit in ambits) {
                val ambitName = ambit.text("name") ?: continue
                val ambitSlug = slug(ambitName)
                val channels = ambit.array("channels")
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { toLiveChannel(it, ambitName, ambitSlug) }
                    .distinctBy(LiveChannel::id)
                if (channels.isNotEmpty()) {
                    sections += LiveTvSection(
                        id = "tdt_$ambitSlug",
                        title = ambitName,
                        channels = channels,
                    )
                }
            }
        }
        return sections.distinctBy(LiveTvSection::id)
    }

    private fun toLiveChannel(
        channel: JsonObject,
        ambitName: String,
        ambitSlug: String,
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
            id = "tdt_${ambitSlug}_${slug(name)}",
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
        val SUPPORTED_FORMATS = setOf("m3u8", "mpd")
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
