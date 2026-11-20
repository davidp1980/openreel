package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.LiveBlockReason
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LivePlaybackDecision
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource

/**
 * Resolves a live RTVE channel into an ordered Media3 source queue (primary + `/ztnr/tv/` fallback)
 * using RTVE's public DASH (for DRM streams) and HLS (for non-DRM streams) routes.
 */
class RtveLivePlaybackResolver(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun resolve(channel: LiveChannel): LivePlaybackDecision {
        if (channel.allowedInCountry == false) {
            return LivePlaybackDecision.Blocked(LiveBlockReason.GEO_RESTRICTED)
        }
        if (channel.isUpcomingAt(nowMillis())) {
            return LivePlaybackDecision.Blocked(LiveBlockReason.NOT_STARTED_YET, channel.startsAtMillis)
        }
        if (channel.paid) {
            return LivePlaybackDecision.Blocked(LiveBlockReason.SUBSCRIPTION_REQUIRED)
        }
        val directUrls = channel.directUrls.filter { it.isNotBlank() }
        if (directUrls.isNotEmpty()) {
            val displayTitle = channel.title.ifBlank {
                channel.category?.takeIf { it.isNotBlank() } ?: channel.id
            }
            val sources = directUrls.mapIndexed { index, url ->
                val cleanPath = url.substringBefore('?').substringBefore('#')
                val mimeType = if (cleanPath.endsWith(".mpd", ignoreCase = true)) MIME_DASH else MIME_HLS
                liveSource(
                    id = "${channel.id}_$index",
                    title = displayTitle,
                    url = url,
                    mimeType = mimeType,
                    drmTokenUrl = null,
                    provider = channel.provider,
                )
            }
            return LivePlaybackDecision.Ready(sources = sources, title = displayTitle)
        }
        val assetId = channel.assetId?.takeIf { it.isNotBlank() }
            ?: return LivePlaybackDecision.Blocked(LiveBlockReason.NO_SOURCE)

        val displayTitle = listOfNotNull(
            channel.category?.takeIf { it.isNotBlank() },
            channel.title.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" · ").ifBlank { assetId }

        val sources = if (channel.hasDrm) {
            listOf(
                liveSource(
                    id = "rtve_${assetId}_dash",
                    title = displayTitle,
                    url = "$ZTNR_BASE/$assetId.mpd",
                    mimeType = MIME_DASH,
                    drmTokenUrl = "$API_BASE/token/$assetId",
                ),
                liveSource(
                    id = "rtve_${assetId}_dash_fallback",
                    title = displayTitle,
                    url = "$ZTNR_BASE/tv/$assetId.mpd",
                    mimeType = MIME_DASH,
                    drmTokenUrl = null,
                ),
            )
        } else {
            listOf(
                liveSource(
                    id = "rtve_${assetId}_hls",
                    title = displayTitle,
                    url = "$ZTNR_BASE/$assetId.m3u8",
                    mimeType = MIME_HLS,
                    drmTokenUrl = null,
                ),
                liveSource(
                    id = "rtve_${assetId}_hls_fallback",
                    title = displayTitle,
                    url = "$ZTNR_BASE/tv/$assetId.m3u8",
                    mimeType = MIME_HLS,
                    drmTokenUrl = null,
                ),
            )
        }
        return LivePlaybackDecision.Ready(sources = sources, title = displayTitle)
    }

    private fun liveSource(
        id: String,
        title: String,
        url: String,
        mimeType: String,
        drmTokenUrl: String?,
        provider: String = PROVIDER,
    ) = StreamSource(
        id = id,
        title = title,
        infoHash = "",
        fileIdx = null,
        magnet = "",
        quality = Quality.FHD_1080,
        hdrType = HdrType.SDR,
        sizeBytes = 0L,
        seeders = null,
        isCached = true,
        directUrl = url,
        provider = provider,
        mimeType = mimeType,
        drmTokenUrl = drmTokenUrl,
        isLive = true,
    )

    private companion object {
        const val PROVIDER = "RTVE"
        const val ZTNR_BASE = "https://ztnr.rtve.es/ztnr"
        const val API_BASE = "https://api.rtve.es/api"
        const val MIME_DASH = "application/dash+xml"
        const val MIME_HLS = "application/x-mpegURL"
    }
}
