package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.domain.LiveTvRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtveLiveRepositoryImpl internal constructor(
    private val http: RtveTextClient,
    private val parser: RtveLiveParser,
    private val nowMillis: () -> Long,
    private val tdtHttp: TdtTextClient = TdtTextClient { _, _ -> throw java.io.IOException("TDT client not configured") },
    private val tdtParser: TdtLiveParser = TdtLiveParser(),
) : LiveTvRepository {

    @Inject
    constructor(http: RtveTextClient, tdtHttp: TdtTextClient, json: Json) : this(
        http = http,
        parser = RtveLiveParser(json),
        nowMillis = System::currentTimeMillis,
        tdtHttp = tdtHttp,
        tdtParser = TdtLiveParser(json),
    )

    private data class CachedDoc(
        val raw: String,
        val etag: String?,
        val savedAtMillis: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedDoc>()

    override suspend fun loadMainSections(forceRefresh: Boolean): List<LiveTvSection> {
        val homeRaw = fetchCached(INDEX_APPS_URL, HOME_FRESH_MS, forceRefresh)
        val rowSources = withContext(Dispatchers.Default) { parser.parseLiveRowSources(homeRaw) }
        if (rowSources.isEmpty()) return emptyList()

        if (rowSources.size == 1) {
            val row = rowSources.single()
            val rowRaw = fetchCached(row.contentUrl, LIVE_FRESH_MS, forceRefresh)
            val channels = withContext(Dispatchers.Default) {
                parser.parseLiveChannels(rowRaw, fallbackTitle = row.title)
            }
            return if (channels.isEmpty()) {
                emptyList()
            } else {
                listOf(LiveTvSection(id = row.contentUrl, title = row.title, channels = channels))
            }
        }

        val outcomes = rowSources.mapConcurrent(concurrency = CONCURRENCY) { row ->
            try {
                val rowRaw = fetchCached(row.contentUrl, LIVE_FRESH_MS, forceRefresh)
                val channels = withContext(Dispatchers.Default) {
                    parser.parseLiveChannels(rowRaw, fallbackTitle = row.title)
                }
                Result.success(
                    if (channels.isEmpty()) null
                    else LiveTvSection(id = row.contentUrl, title = row.title, channels = channels),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        val sections = outcomes.mapNotNull { it.getOrNull() }
        if (sections.isEmpty() && outcomes.all { it.isFailure }) {
            throw outcomes.first().exceptionOrNull()!!
        }
        return sections
    }

    override suspend fun loadThematicChannels(forceRefresh: Boolean): List<LiveChannel> {
        val configRaw = fetchCached(REMOTE_CONFIG_TV_URL, CONFIG_FRESH_MS, forceRefresh)
        val sources = withContext(Dispatchers.Default) { parser.parseThematicChannelSources(configRaw) }
        if (sources.isEmpty()) return emptyList()

        return sources
            .mapConcurrent(concurrency = CONCURRENCY) { source ->
                try {
                    val raw = fetchCached(source.contentUrl, LIVE_FRESH_MS, forceRefresh)
                    withContext(Dispatchers.Default) {
                        parser.parseLiveChannels(
                            raw = raw,
                            fallbackTitle = source.title,
                            fallbackImageUrl = source.imageUrl,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            .flatten()
            .distinctBy { it.assetId ?: it.id }
    }

    override suspend fun loadOpenSections(forceRefresh: Boolean): List<LiveTvSection> {
        val raw = fetchCached(
            url = TDT_TV_JSON_URL,
            freshForMillis = TDT_FRESH_MS,
            staleFallbackMillis = TDT_STALE_FALLBACK_MS,
            forceRefresh = forceRefresh,
            fetcher = tdtHttp::fetch,
        )
        return withContext(Dispatchers.Default) { tdtParser.parseSections(raw) }
    }

    private suspend fun fetchCached(
        url: String,
        freshForMillis: Long,
        forceRefresh: Boolean,
        staleFallbackMillis: Long = STALE_FALLBACK_MS,
        fetcher: suspend (String, String?) -> RtveTextResponse = http::fetch,
    ): String {
        val now = nowMillis()
        val cached = cache[url]
        val age = if (cached != null) now - cached.savedAtMillis else Long.MAX_VALUE
        if (!forceRefresh && cached != null && age <= freshForMillis) {
            return cached.raw
        }

        return try {
            val response = fetcher(url, cached?.etag)
            if (response.notModified && cached != null) {
                cache[url] = cached.copy(savedAtMillis = now)
                cached.raw
            } else {
                val body = response.body ?: throw java.io.IOException("Empty response body")
                cache[url] = CachedDoc(raw = body, etag = response.etag, savedAtMillis = now)
                body
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (cached != null && age <= staleFallbackMillis) cached.raw else throw error
        }
    }

    private companion object {
        const val INDEX_APPS_URL = "https://www.rtve.es/play/index_apps.json"
        const val REMOTE_CONFIG_TV_URL = "https://www.rtve.es/m/configs/rtve_play/2026/estructuraAndroidTV2.json"
        const val TDT_TV_JSON_URL = "https://www.tdtchannels.com/lists/tv.json"
        const val HOME_FRESH_MS = 2 * 60 * 1_000L
        const val LIVE_FRESH_MS = 45_000L
        const val CONFIG_FRESH_MS = 60 * 60 * 1_000L
        const val TDT_FRESH_MS = 30 * 60 * 1_000L
        const val STALE_FALLBACK_MS = 15 * 60 * 1_000L
        const val TDT_STALE_FALLBACK_MS = 2 * 60 * 60 * 1_000L
        const val CONCURRENCY = 4
    }
}
