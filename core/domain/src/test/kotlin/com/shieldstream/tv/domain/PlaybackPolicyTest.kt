package com.shieldstream.tv.domain

import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.PlaybackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun `automatic full profile preserves previous player defaults`() {
        val policy = PlaybackPolicy.automatic(512L * MIB)

        assertEquals(MemoryProfile.FULL, policy.memoryProfile)
        assertEquals(50, policy.minBufferSeconds)
        assertEquals(80, policy.maxBufferSeconds)
        assertEquals(112 * MIB, policy.targetBufferBytes)
        assertTrue(policy.seekThumbnails)
    }

    @Test
    fun `automatic constrained profile reduces memory use`() {
        val policy = PlaybackPolicy.automatic(256L * MIB)

        assertEquals(MemoryProfile.CONSTRAINED, policy.memoryProfile)
        assertEquals(30, policy.minBufferSeconds)
        assertEquals(40, policy.maxBufferSeconds)
        assertEquals(40 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `custom policy keeps manual preferences`() {
        val policy = PlaybackPolicy.custom(maxBufferSeconds = 90, seekThumbnails = false)

        assertEquals(90, policy.maxBufferSeconds)
        assertEquals(112 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `custom low memory buffer preserves 1_0_8 timings`() {
        val policy = PlaybackPolicy.custom(maxBufferSeconds = 40, seekThumbnails = true)

        assertEquals(20, policy.minBufferSeconds)
        assertEquals(40, policy.maxBufferSeconds)
        assertEquals(64 * MIB, policy.targetBufferBytes)
        assertEquals(3_000, policy.bufferForPlaybackMs)
        assertEquals(5_000, policy.backBufferMs)
    }

    @Test
    fun `automatic fire stick 1gb uses ultra constrained policy`() {
        val caps = DeviceCapabilities(
            model = "AFTSS",
            manufacturer = "Amazon",
            totalRamBytes = 1024L * MIB,
            isFireTv = true,
            heapLimitBytes = 512L * MIB,
            hardwareVideoCodecs = emptySet(),
            supportedHdr = emptySet(),
        )
        val policy = PlaybackPolicy.automatic(caps)

        assertEquals(MemoryProfile.CONSTRAINED, policy.memoryProfile)
        assertEquals(20, policy.minBufferSeconds)
        assertEquals(30, policy.maxBufferSeconds)
        assertEquals(32 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `automatic fire stick 4k max 2gb stays constrained even with largeHeap`() {
        val caps = DeviceCapabilities(
            model = "AFTKRT",
            manufacturer = "Amazon",
            totalRamBytes = 2048L * MIB,
            isFireTv = true,
            heapLimitBytes = 512L * MIB,
            hardwareVideoCodecs = emptySet(),
            supportedHdr = emptySet(),
        )
        val policy = PlaybackPolicy.automatic(caps)

        assertEquals(MemoryProfile.CONSTRAINED, policy.memoryProfile)
        assertEquals(30, policy.minBufferSeconds)
        assertEquals(40, policy.maxBufferSeconds)
        assertEquals(40 * MIB, policy.targetBufferBytes)
        assertFalse(policy.seekThumbnails)
    }

    @Test
    fun `automatic chromecast hd and 4k stay constrained`() {
        val hd = PlaybackPolicy.automatic(
            DeviceCapabilities(
                model = "Chromecast HD",
                manufacturer = "Google",
                device = "boreal",
                totalRamBytes = 1536L * MIB,
                heapLimitBytes = 512L * MIB,
                hardwareVideoCodecs = emptySet(),
                supportedHdr = emptySet(),
            ),
        )
        assertEquals(30, hd.maxBufferSeconds)
        assertEquals(32 * MIB, hd.targetBufferBytes)
        assertFalse(hd.seekThumbnails)

        val fourK = PlaybackPolicy.automatic(
            DeviceCapabilities(
                model = "Chromecast",
                manufacturer = "Google",
                device = "sabrina",
                totalRamBytes = 2048L * MIB,
                heapLimitBytes = 512L * MIB,
                hardwareVideoCodecs = emptySet(),
                supportedHdr = emptySet(),
            ),
        )
        assertEquals(40, fourK.maxBufferSeconds)
        assertEquals(40 * MIB, fourK.targetBufferBytes)
        assertFalse(fourK.seekThumbnails)
    }

    @Test
    fun `automatic shield and streamer 4k use full policy`() {
        val policy = PlaybackPolicy.automatic(
            DeviceCapabilities(
                model = "Google TV Streamer",
                manufacturer = "Google",
                device = "kirkwood",
                totalRamBytes = 4096L * MIB,
                heapLimitBytes = 512L * MIB,
                hardwareVideoCodecs = emptySet(),
                supportedHdr = emptySet(),
            ),
        )

        assertEquals(MemoryProfile.FULL, policy.memoryProfile)
        assertEquals(80, policy.maxBufferSeconds)
        assertEquals(112 * MIB, policy.targetBufferBytes)
        assertTrue(policy.seekThumbnails)
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}
