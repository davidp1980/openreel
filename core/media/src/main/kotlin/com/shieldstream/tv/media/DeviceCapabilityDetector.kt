package com.shieldstream.tv.media

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayCapabilities: DisplayCapabilityDetector,
) {
    private val staticCapabilities by lazy {
        DeviceCapabilities(
            model = Build.MODEL.ifBlank { "Android TV" },
            heapLimitBytes = Runtime.getRuntime().maxMemory(),
            hardwareVideoCodecs = hardwareVideoCodecs(),
            supportedHdr = emptySet(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            totalRamBytes = totalPhysicalRamBytes(),
            isFireTv = runCatching { context.packageManager.hasSystemFeature(FIRE_TV_FEATURE) }.getOrDefault(false),
        )
    }

    val capabilities: DeviceCapabilities
        get() = staticCapabilities.copy(supportedHdr = displayCapabilities.supportedHdrTypes())

    private fun totalPhysicalRamBytes(): Long = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching 0L
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }.getOrDefault(0L)

    private fun hardwareVideoCodecs(): Set<VideoCodec> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .filterNot(::isSoftwareDecoder)
            .flatMap { codec -> runCatching { codec.supportedTypes.asList() }.getOrDefault(emptyList()) }
            .mapNotNull(VideoCodec::fromMimeType)
            .toSet()
    }.getOrDefault(emptySet())

    private fun isSoftwareDecoder(codec: android.media.MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return !codec.isHardwareAccelerated
        val name = codec.name.lowercase()
        return SOFTWARE_DECODER_PREFIXES.any(name::startsWith)
    }

    private companion object {
        const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
        val SOFTWARE_DECODER_PREFIXES = listOf("omx.google.", "c2.android.", "c2.dav1d.", "c2.lib")
    }
}
