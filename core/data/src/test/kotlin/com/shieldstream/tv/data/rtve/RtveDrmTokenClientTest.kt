package com.shieldstream.tv.data.rtve

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RtveDrmTokenClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extracts valid https widevine license url`() = runTest {
        val client = RtveDrmTokenClient(
            http = FakeRtveTextClient("""{"token":"abc","widevineURL":"https://drm.axinom.test/wv?token=123"}"""),
            json = json,
        )

        val url = client.widevineLicenseUrl("https://api.rtve.es/api/token/999")

        assertEquals("https://drm.axinom.test/wv?token=123", url)
    }

    @Test(expected = IOException::class)
    fun `rejects missing widevineURL`() = runTest {
        val client = RtveDrmTokenClient(FakeRtveTextClient("""{"token":"abc"}"""), json)
        client.widevineLicenseUrl("https://api.rtve.es/api/token/999")
    }

    @Test(expected = IOException::class)
    fun `rejects non-https widevineURL`() = runTest {
        val client = RtveDrmTokenClient(
            FakeRtveTextClient("""{"widevineURL":"http://drm.axinom.test/wv"}"""),
            json,
        )
        client.widevineLicenseUrl("https://api.rtve.es/api/token/999")
    }

    private class FakeRtveTextClient(private val payload: String) : RtveTextClient {
        override suspend fun fetch(url: String, etag: String?) = RtveTextResponse(payload, null)
    }
}
