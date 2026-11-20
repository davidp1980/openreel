package com.shieldstream.tv.data.rtve

import com.shieldstream.tv.domain.RtveHostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RtveTextResponse(val body: String?, val etag: String?) {
    val notModified: Boolean get() = body == null
}

interface RtveTextClient {
    suspend fun fetch(url: String, etag: String? = null): RtveTextResponse
    suspend fun get(url: String): String = fetch(url).body ?: throw IOException("Empty response body")
}

/**
 * HTTPS client for RTVE catalog and token endpoints. Enforces [RtveHostPolicy] on every hop
 * (including redirects) and upgrades `http://` redirects returned by RTVE APIs to HTTPS.
 */
@Singleton
class RtveHttpClient @Inject constructor(
    baseClient: OkHttpClient,
) : RtveTextClient {

    private val hostPolicy = RtveHostPolicy()

    private val client: OkHttpClient = baseClient.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor(HostPolicyInterceptor(hostPolicy))
        .build()

    override suspend fun fetch(url: String, etag: String?): RtveTextResponse = withContext(Dispatchers.IO) {
        var target = hostPolicy.sanitize(url) ?: throw IOException("Host not allowed: $url")
        repeat(MAX_REDIRECTS + 1) { hop ->
            val request = Request.Builder()
                .url(target)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .apply { if (etag != null && hop == 0) header("If-None-Match", etag) }
                .build()
            val result = client.newCall(request).awaitResponse { response ->
                when {
                    response.code == 304 && etag != null -> RtveTextResponse(body = null, etag = etag)
                    response.isRedirect -> {
                        if (hop == MAX_REDIRECTS) throw IOException("Too many redirects")
                        val location = response.header("Location")
                            ?: throw IOException("Redirect without Location header")
                        val resolved = request.url.resolve(location)?.toString() ?: location
                        target = hostPolicy.sanitize(resolved)
                            ?: throw IOException("Redirect to disallowed host")
                        null
                    }
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
            if (result != null) return@withContext result
        }
        throw IOException("Unable to complete RTVE request")
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

    private class HostPolicyInterceptor(
        private val hostPolicy: RtveHostPolicy,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            try {
                hostPolicy.requireAllowed(URI(chain.request().url.toString()))
            } catch (error: IllegalArgumentException) {
                throw IOException(error.message, error)
            }
            return chain.proceed(chain.request())
        }
    }

    private companion object {
        const val USER_AGENT = "OpenReel-AndroidTV"
        const val MAX_REDIRECTS = 3
        const val MAX_REQUESTS_PER_HOST = 16
        const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024L
    }
}
