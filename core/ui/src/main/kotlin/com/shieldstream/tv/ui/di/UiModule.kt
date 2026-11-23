package com.shieldstream.tv.ui.di

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.shieldstream.tv.model.DeviceProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val totalRamBytes = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching 0L
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
        }.getOrDefault(0L)
        val hasFireTvFeature = runCatching {
            context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        }.getOrDefault(false)
        val desiredDiskBytes = DeviceProfile.imageDiskCacheBytes(
            model = model,
            manufacturer = manufacturer,
            device = device,
            totalRamBytes = totalRamBytes,
            hasFireTvFeature = hasFireTvFeature,
        )
        val diskBytes = DeviceProfile.capDiskCacheByFreeSpace(
            desiredBytes = desiredDiskBytes,
            availableBytes = runCatching { context.cacheDir.usableSpace }.getOrDefault(0L),
        )
        return ImageLoader.Builder(context)
            // Fading every poster/backdrop adds animation and compositing work on low-power TV boxes.
            .crossfade(false)
            .memoryCache(
                MemoryCache.Builder(context)
                    .maxSizeBytes(
                        DeviceProfile.imageMemoryCacheBytes(
                            model = model,
                            manufacturer = manufacturer,
                            device = device,
                            totalRamBytes = totalRamBytes,
                            hasFireTvFeature = hasFireTvFeature,
                        ),
                    )
                    .build(),
            )
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build(),
            )
            .build()
    }
}
