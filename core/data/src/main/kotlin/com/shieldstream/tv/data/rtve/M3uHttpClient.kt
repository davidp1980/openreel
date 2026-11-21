package com.shieldstream.tv.data.rtve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

fun interface M3uTextClient {
    suspend fun fetch(url: String, etag: String?): RtveTextResponse
}

/**
 * HTTP/HTTPS client for user-configured M3U/M3U8 live TV playlists.
 */
@Singleton
class M3uHttpClient @Inject constructor(
    private val client: OkHttpClient,
) : M3uTextClient {

    override suspend fun fetch(url: String, etag: String?): RtveTextResponse = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        if (uri == null || (scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
            throw IOException("Invalid M3U URL: $url")
        }
        val request = Request.Builder()
            .url(url.trim())
            .header("Accept", "application/vnd.apple.mpegurl, audio/x-mpegurl, text/plain, */*")
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
                        throw IOException("Playlist exceeds max size")
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
        const val MAX_PAYLOAD_BYTES = 8L * 1024 * 1024L
    }
}
