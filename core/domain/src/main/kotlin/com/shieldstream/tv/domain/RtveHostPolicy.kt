package com.shieldstream.tv.domain

import java.net.URI

/**
 * Strict allowlist for RTVE catalog, images and token hosts.
 * Playback streams themselves follow CDN redirects and Widevine license hosts via Media3's HTTP client.
 */
class RtveHostPolicy(private val rootDomain: String = "rtve.es") {
    fun requireAllowed(uri: URI) {
        val host = uri.host?.lowercase()
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS is allowed" }
        require(uri.rawUserInfo == null) { "URL must not contain credentials" }
        require(uri.port == -1 || uri.port == 443) { "Port not allowed" }
        require(host == rootDomain || host?.endsWith(".$rootDomain") == true) {
            "Host not allowed"
        }
    }

    fun isAllowed(value: String): Boolean = runCatching {
        requireAllowed(URI(value))
        true
    }.getOrDefault(false)

    /** Upgrades `http://` image/feed links returned by RTVE APIs to `https://` and validates the host. */
    fun sanitize(value: String): String? {
        val upgraded = value.trim().replaceFirst(HTTP_SCHEME, "https://")
        return upgraded.takeIf(::isAllowed)
    }

    private companion object {
        val HTTP_SCHEME = Regex("^http://", RegexOption.IGNORE_CASE)
    }
}
