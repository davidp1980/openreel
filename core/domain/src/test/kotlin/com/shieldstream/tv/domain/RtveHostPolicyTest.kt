package com.shieldstream.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RtveHostPolicyTest {

    private val policy = RtveHostPolicy()

    @Test
    fun `allows rtve apex and subdomains over https`() {
        assertTrue(policy.isAllowed("https://rtve.es/play/index_apps.json"))
        assertTrue(policy.isAllowed("https://www.rtve.es/play/index_apps.json"))
        assertTrue(policy.isAllowed("https://api.rtve.es/api/lives/1.json"))
        assertTrue(policy.isAllowed("https://ztnr.rtve.es/ztnr/123.m3u8"))
        assertTrue(policy.isAllowed("https://img.rtve.es/v/123/?w=400"))
    }

    @Test
    fun `rejects non-rtve hosts, userinfo and non-standard ports`() {
        assertFalse(policy.isAllowed("https://rtve.es.evil.com/index.json"))
        assertFalse(policy.isAllowed("https://notrtve.es/index.json"))
        assertFalse(policy.isAllowed("https://user@www.rtve.es/index.json"))
        assertFalse(policy.isAllowed("https://www.rtve.es:8443/index.json"))
        assertFalse(policy.isAllowed("http://www.rtve.es/index.json"))
        assertFalse(policy.isAllowed("file:///etc/passwd"))
    }

    @Test
    fun `sanitize upgrades http rtve urls to https and rejects external hosts`() {
        assertEquals(
            "https://api.rtve.es/api/lives/agr-directos/29/directos.json",
            policy.sanitize("  http://api.rtve.es/api/lives/agr-directos/29/directos.json  "),
        )
        assertNull(policy.sanitize("http://evil.example/stream.m3u8"))
        assertNull(policy.sanitize("javascript:alert(1)"))
    }
}
