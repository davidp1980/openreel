package com.shieldstream.tv.model

enum class DeviceTier {
    ULTRA_CONSTRAINED,
    CONSTRAINED,
    FULL,
}

enum class DeviceFamily {
    FIRE_TV,
    CHROMECAST_TV,
    NOKIA_8010,
    OTHER,
}

object DeviceProfile {
    private const val MIB = 1024L * 1024L
    private const val NOKIA_8010 = "Nokia_Streaming_Box_8010"
    private const val ULTRA_CONSTRAINED_RAM_BYTES = 1_700L * MIB
    private const val CONSTRAINED_RAM_BYTES = 2_560L * MIB
    private val ULTRA_CONSTRAINED_FIRE_MODELS = setOf(
        "AFTSS",
        "AFTSSS",
        "AFTT",
        "AFTMM",
        "AFTB",
        "AFTM",
        "AFTS",
        "AFTN",
    )

    fun isNokia8010(model: String): Boolean =
        model.trim().replace(' ', '_').equals(NOKIA_8010, ignoreCase = true)

    fun isFireTv(
        model: String,
        manufacturer: String = "",
        hasFireTvFeature: Boolean = false,
    ): Boolean = hasFireTvFeature ||
        manufacturer.trim().equals("Amazon", ignoreCase = true) ||
        model.trim().uppercase().startsWith("AFT")

    fun isChromecastHd(
        model: String,
        device: String = "",
        manufacturer: String = "",
    ): Boolean = device.trim().equals("boreal", ignoreCase = true) ||
        model.trim().equals("Chromecast HD", ignoreCase = true)

    fun isChromecast4k(
        model: String,
        device: String = "",
        manufacturer: String = "",
    ): Boolean {
        if (isChromecastHd(model, device, manufacturer)) return false
        val trimmedDevice = device.trim()
        val trimmedModel = model.trim()
        val trimmedManufacturer = manufacturer.trim()
        return trimmedDevice.equals("sabrina", ignoreCase = true) ||
            trimmedModel.equals("Chromecast", ignoreCase = true) ||
            (trimmedManufacturer.equals("Google", ignoreCase = true) && trimmedModel.startsWith("Chromecast", ignoreCase = true))
    }

    fun family(
        model: String,
        manufacturer: String = "",
        device: String = "",
        hasFireTvFeature: Boolean = false,
    ): DeviceFamily = when {
        isFireTv(model, manufacturer, hasFireTvFeature) -> DeviceFamily.FIRE_TV
        isChromecastHd(model, device, manufacturer) || isChromecast4k(model, device, manufacturer) -> DeviceFamily.CHROMECAST_TV
        isNokia8010(model) -> DeviceFamily.NOKIA_8010
        else -> DeviceFamily.OTHER
    }

    fun tier(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): DeviceTier {
        if (totalRamBytes in 1L..ULTRA_CONSTRAINED_RAM_BYTES) return DeviceTier.ULTRA_CONSTRAINED
        if (isChromecastHd(model, device, manufacturer)) return DeviceTier.ULTRA_CONSTRAINED
        if (isFireTv(model, manufacturer, hasFireTvFeature)) {
            return if (totalRamBytes > 0L) {
                if (totalRamBytes <= CONSTRAINED_RAM_BYTES) DeviceTier.CONSTRAINED else DeviceTier.FULL
            } else {
                if (model.trim().uppercase() in ULTRA_CONSTRAINED_FIRE_MODELS) {
                    DeviceTier.ULTRA_CONSTRAINED
                } else {
                    DeviceTier.CONSTRAINED
                }
            }
        }
        if (isChromecast4k(model, device, manufacturer) || isNokia8010(model)) return DeviceTier.CONSTRAINED
        if (totalRamBytes in 1L..CONSTRAINED_RAM_BYTES) return DeviceTier.CONSTRAINED
        return DeviceTier.FULL
    }

    fun isLowPowerBox(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Boolean = tier(model, manufacturer, device, totalRamBytes, hasFireTvFeature) != DeviceTier.FULL

    fun lightweightUiDefault(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Boolean = isLowPowerBox(model, manufacturer, device, totalRamBytes, hasFireTvFeature)

    fun defaultBufferSeconds(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Int = when (tier(model, manufacturer, device, totalRamBytes, hasFireTvFeature)) {
        DeviceTier.ULTRA_CONSTRAINED -> 30
        DeviceTier.CONSTRAINED -> 40
        DeviceTier.FULL -> 80
    }

    fun imageMemoryCacheBytes(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Int = when (tier(model, manufacturer, device, totalRamBytes, hasFireTvFeature)) {
        DeviceTier.ULTRA_CONSTRAINED -> 16 * 1024 * 1024
        DeviceTier.CONSTRAINED -> 24 * 1024 * 1024
        DeviceTier.FULL -> 64 * 1024 * 1024
    }

    fun imageDiskCacheBytes(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Long = when {
        isChromecastHd(model, device, manufacturer) -> 96L * MIB
        isChromecast4k(model, device, manufacturer) -> 100L * MIB
        tier(model, manufacturer, device, totalRamBytes, hasFireTvFeature) == DeviceTier.ULTRA_CONSTRAINED -> 100L * MIB
        tier(model, manufacturer, device, totalRamBytes, hasFireTvFeature) == DeviceTier.CONSTRAINED -> 150L * MIB
        else -> 512L * MIB
    }

    fun capDiskCacheByFreeSpace(desiredBytes: Long, availableBytes: Long): Long {
        if (availableBytes <= 0L) return desiredBytes
        return minOf(desiredBytes, maxOf(32L * MIB, availableBytes / 5L))
    }

    fun seekThumbnailsCacheBytes(
        model: String,
        manufacturer: String = "",
        device: String = "",
        totalRamBytes: Long = 0L,
        hasFireTvFeature: Boolean = false,
    ): Int = if (isLowPowerBox(model, manufacturer, device, totalRamBytes, hasFireTvFeature)) {
        12 * 1024 * 1024
    } else {
        24 * 1024 * 1024
    }

    fun supportsWatchNext(
        model: String,
        manufacturer: String = "",
        hasFireTvFeature: Boolean = false,
    ): Boolean = !isFireTv(model, manufacturer, hasFireTvFeature)
}
