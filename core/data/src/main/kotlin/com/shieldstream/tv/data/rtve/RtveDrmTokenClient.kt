package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.domain.LiveDrmLicenseProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches ephemeral Widevine license URLs from `api.rtve.es/api/token/{idAsset}`.
 * Returned URLs contain short-lived rights tokens and must never be cached or logged.
 */
@Singleton
class RtveDrmTokenClient @Inject constructor(
    private val http: RtveTextClient,
    private val json: Json,
) : LiveDrmLicenseProvider {

    override suspend fun widevineLicenseUrl(tokenUrl: String): String {
        val root = json.parseToJsonElement(http.get(tokenUrl)).jsonObject
        val url = (root["widevineURL"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IOException("Response does not include a Widevine license URL")
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw IOException("Invalid Widevine license URL")
        }
        return url
    }
}
