package com.shieldstream.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {
    @Test
    fun nokiaDefaultsToLightweightUiAndSmallerBuffer() {
        assertTrue(DeviceProfile.lightweightUiDefault("Nokia_Streaming_Box_8010"))
        assertTrue(DeviceProfile.lightweightUiDefault("Nokia Streaming Box 8010"))
        assertEquals(DeviceTier.CONSTRAINED, DeviceProfile.tier("Nokia_Streaming_Box_8010"))
        assertEquals(40, DeviceProfile.defaultBufferSeconds("Nokia_Streaming_Box_8010"))
        assertEquals(24 * MIB, DeviceProfile.imageMemoryCacheBytes("Nokia Streaming Box 8010"))
        assertEquals(150L * MIB, DeviceProfile.imageDiskCacheBytes("Nokia Streaming Box 8010"))
        assertTrue(DeviceProfile.supportsWatchNext("Nokia_Streaming_Box_8010"))
    }

    @Test
    fun fireStickBasicAnd1GbUseUltraConstrainedProfileAndDisableWatchNext() {
        for (model in listOf("AFTSS", "AFTMM", "AFTSSS", "AFTT")) {
            assertTrue(DeviceProfile.isFireTv(model, manufacturer = "Amazon"))
            assertEquals(DeviceFamily.FIRE_TV, DeviceProfile.family(model, manufacturer = "Amazon"))
            assertEquals(DeviceTier.ULTRA_CONSTRAINED, DeviceProfile.tier(model, manufacturer = "Amazon", totalRamBytes = 1536L * MIB))
            assertTrue(DeviceProfile.lightweightUiDefault(model, manufacturer = "Amazon", totalRamBytes = 1536L * MIB))
            assertEquals(30, DeviceProfile.defaultBufferSeconds(model, manufacturer = "Amazon"))
            assertEquals(16 * MIB, DeviceProfile.imageMemoryCacheBytes(model, manufacturer = "Amazon"))
            assertEquals(100L * MIB, DeviceProfile.imageDiskCacheBytes(model, manufacturer = "Amazon"))
            assertEquals(12 * MIB, DeviceProfile.seekThumbnailsCacheBytes(model, manufacturer = "Amazon"))
            assertFalse(DeviceProfile.supportsWatchNext(model, manufacturer = "Amazon"))
        }
    }

    @Test
    fun fireTv2GbUsesConstrainedProfileAndDisablesWatchNext() {
        for (model in listOf("AFTKA", "AFTKRT", "AFTKM")) {
            val ram = 2048L * MIB
            assertEquals(DeviceTier.CONSTRAINED, DeviceProfile.tier(model, manufacturer = "Amazon", totalRamBytes = ram))
            assertTrue(DeviceProfile.lightweightUiDefault(model, manufacturer = "Amazon", totalRamBytes = ram))
            assertEquals(40, DeviceProfile.defaultBufferSeconds(model, manufacturer = "Amazon", totalRamBytes = ram))
            assertEquals(24 * MIB, DeviceProfile.imageMemoryCacheBytes(model, manufacturer = "Amazon", totalRamBytes = ram))
            assertEquals(150L * MIB, DeviceProfile.imageDiskCacheBytes(model, manufacturer = "Amazon", totalRamBytes = ram))
            assertFalse(DeviceProfile.supportsWatchNext(model, manufacturer = "Amazon", hasFireTvFeature = true))
        }
    }

    @Test
    fun chromecastHdAnd4kUseTailoredStorageAndKeepWatchNext() {
        assertEquals(DeviceFamily.CHROMECAST_TV, DeviceProfile.family("Chromecast HD", manufacturer = "Google", device = "boreal"))
        assertEquals(DeviceTier.ULTRA_CONSTRAINED, DeviceProfile.tier("Chromecast HD", manufacturer = "Google", device = "boreal", totalRamBytes = 1536L * MIB))
        assertEquals(30, DeviceProfile.defaultBufferSeconds("Chromecast HD", manufacturer = "Google", device = "boreal"))
        assertEquals(16 * MIB, DeviceProfile.imageMemoryCacheBytes("Chromecast HD", manufacturer = "Google", device = "boreal"))
        assertEquals(96L * MIB, DeviceProfile.imageDiskCacheBytes("Chromecast HD", manufacturer = "Google", device = "boreal"))
        assertTrue(DeviceProfile.supportsWatchNext("Chromecast HD", manufacturer = "Google"))

        assertEquals(DeviceFamily.CHROMECAST_TV, DeviceProfile.family("Chromecast", manufacturer = "Google", device = "sabrina"))
        assertEquals(DeviceTier.CONSTRAINED, DeviceProfile.tier("Chromecast", manufacturer = "Google", device = "sabrina", totalRamBytes = 2048L * MIB))
        assertEquals(40, DeviceProfile.defaultBufferSeconds("Chromecast", manufacturer = "Google", device = "sabrina"))
        assertEquals(24 * MIB, DeviceProfile.imageMemoryCacheBytes("Chromecast", manufacturer = "Google", device = "sabrina"))
        assertEquals(100L * MIB, DeviceProfile.imageDiskCacheBytes("Chromecast", manufacturer = "Google", device = "sabrina"))
        assertTrue(DeviceProfile.supportsWatchNext("Chromecast", manufacturer = "Google"))
    }

    @Test
    fun highEndDevicesAndStreamer4kKeepFullProfile() {
        for ((model, device, ram) in listOf(
            Triple("SHIELD Android TV", "mdarcy", 3072L * MIB),
            Triple("Google TV Streamer", "kirkwood", 4096L * MIB),
        )) {
            assertEquals(DeviceTier.FULL, DeviceProfile.tier(model, device = device, totalRamBytes = ram))
            assertFalse(DeviceProfile.lightweightUiDefault(model, device = device, totalRamBytes = ram))
            assertEquals(80, DeviceProfile.defaultBufferSeconds(model, device = device, totalRamBytes = ram))
            assertEquals(64 * MIB, DeviceProfile.imageMemoryCacheBytes(model, device = device, totalRamBytes = ram))
            assertEquals(512L * MIB, DeviceProfile.imageDiskCacheBytes(model, device = device, totalRamBytes = ram))
            assertEquals(24 * MIB, DeviceProfile.seekThumbnailsCacheBytes(model, device = device, totalRamBytes = ram))
            assertTrue(DeviceProfile.supportsWatchNext(model))
        }
    }

    @Test
    fun genericLowRamBoxFallsBackToLowPowerTier() {
        assertEquals(DeviceTier.ULTRA_CONSTRAINED, DeviceProfile.tier("Unknown Stick", totalRamBytes = 1024L * MIB))
        assertEquals(DeviceTier.CONSTRAINED, DeviceProfile.tier("MiBOX4", totalRamBytes = 2048L * MIB))
    }

    @Test
    fun diskCacheIsCappedByAvailableSpaceWith32MiBFloor() {
        assertEquals(100L * MIB, DeviceProfile.capDiskCacheByFreeSpace(100L * MIB, 1_000L * MIB))
        assertEquals(40L * MIB, DeviceProfile.capDiskCacheByFreeSpace(150L * MIB, 200L * MIB))
        assertEquals(32L * MIB, DeviceProfile.capDiskCacheByFreeSpace(96L * MIB, 80L * MIB))
        assertEquals(96L * MIB, DeviceProfile.capDiskCacheByFreeSpace(96L * MIB, 0L))
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}
