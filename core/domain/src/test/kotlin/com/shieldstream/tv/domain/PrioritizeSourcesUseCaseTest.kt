package com.shieldstream.tv.domain

import com.shieldstream.tv.model.HdrPreference
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.StreamSource
import com.shieldstream.tv.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritizeSourcesUseCaseTest {
    private val prioritize = PrioritizeSourcesUseCase()

    @Test
    fun `hardware AV1 is not penalized or labelled as CPU decoding`() {
        val av1 = source(id = "av1", videoCodec = VideoCodec.AV1, softwareDecode = true)
        val hevc = source(id = "hevc", videoCodec = VideoCodec.HEVC)

        val result = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.AV1, VideoCodec.HEVC),
        )

        assertEquals("av1", result.first().id)
        assertFalse(result.first().softwareDecode)
    }

    @Test
    fun `software AV1 remains behind an equivalent hardware source`() {
        val av1 = source(id = "av1", videoCodec = VideoCodec.AV1, softwareDecode = true)
        val hevc = source(id = "hevc", videoCodec = VideoCodec.HEVC)

        val result = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC),
        )

        assertEquals("hevc", result.first().id)
        assertTrue(result.last().softwareDecode)
    }

    @Test
    fun `4K AV1 requires a hardware decoder`() {
        val av1 = source(
            id = "av1-4k",
            quality = Quality.UHD_2160,
            videoCodec = VideoCodec.AV1,
            softwareDecode = true,
        )
        val hevc = source(id = "hevc-4k", quality = Quality.UHD_2160, videoCodec = VideoCodec.HEVC)

        val softwareOnly = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC),
        )
        val hardwareAv1 = prioritize(
            listOf(av1, hevc),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = setOf(VideoCodec.HEVC, VideoCodec.AV1),
        )

        assertEquals(listOf("hevc-4k"), softwareOnly.map { it.id })
        assertEquals(setOf("av1-4k", "hevc-4k"), hardwareAv1.map { it.id }.toSet())
    }

    @Test
    fun `upscaled 1080p DS4K is not mistaken for AV1 4K`() {
        val av1 = source(
            id = "av1-upscaled",
            title = "Movie.1080p.DS4K.AV1",
            quality = Quality.UHD_2160,
            videoCodec = VideoCodec.AV1,
            softwareDecode = true,
        )

        val result = prioritize(
            listOf(av1),
            supportedHdr = setOf(HdrType.SDR),
            hardwareVideoCodecs = emptySet(),
        )

        assertEquals(listOf("av1-upscaled"), result.map { it.id })
    }

    @Test
    fun `Spanish-first puts advertised Spanish audio ahead of other languages and cache status`() {
        val cached = source(id = "cached", isCached = true, seeders = 100, languages = listOf("EN"))
        val spanish = source(
            id = "spanish",
            title = "Movie.Castellano.1080p",
            isCached = false,
            seeders = 1,
            languages = listOf("ES"),
        )

        val spanishFirst = prioritize(
            listOf(cached, spanish),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = true,
        )
        val languageNeutral = prioritize(
            listOf(cached, spanish),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = false,
        )

        assertEquals(listOf("spanish", "cached"), spanishFirst.map { it.id })
        assertEquals(listOf("cached", "spanish"), languageNeutral.map { it.id })
    }

    @Test
    fun `Spanish-first ranks Latino-only releases below Castellano and English`() {
        val latinoTitle = source(
            id = "latino-title",
            title = "Movie.Español.Latino.1080p",
            isCached = true,
            languages = listOf("LAT"),
        )
        val dualLatino = source(
            id = "dual-latino",
            title = "Movie.Dual.Latino.1080p",
            isCached = true,
            languages = listOf("EN"),
        )
        val english = source(
            id = "english",
            title = "Movie.1080p",
            isCached = true,
            languages = listOf("EN"),
        )
        val castellano = source(
            id = "castellano",
            title = "Movie.Castellano.1080p",
            isCached = false,
            languages = listOf("ES"),
        )
        val multiBoth = source(
            id = "multi-both",
            title = "Movie.Castellano.Latino.1080p",
            isCached = true,
            languages = listOf("ES", "LAT"),
        )

        val result = prioritize(
            listOf(latinoTitle, dualLatino, english, castellano, multiBoth),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = true,
        )

        assertEquals(
            listOf("multi-both", "castellano", "english", "latino-title", "dual-latino"),
            result.map { it.id },
        )
    }

    @Test
    fun `unspecified cached source is not demoted below uncached multilingual source without Spanish-first`() {
        val multilingual = source(
            id = "multilingual",
            isCached = false,
            languages = listOf("EN", "RU"),
        )
        val unspecified = source(id = "unspecified", isCached = true)

        val result = prioritize(
            listOf(multilingual, unspecified),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = false,
        )

        assertEquals(listOf("unspecified", "multilingual"), result.map { it.id })
    }

    @Test
    fun `Spanish-first prioritizes Dual Castellano plus VO over Castellano, VO, and Latino`() {
        val latinoWithEsFlag = source(
            id = "latino-es-flag",
            title = "Serie.Dual.Latino.Ingles.1080p",
            languages = listOf("ES", "EN"),
        )
        val vo = source(id = "vo", title = "Serie.1080p", languages = listOf("EN"))
        val castellano = source(id = "castellano", title = "Serie.Castellano.1080p", languages = listOf("ES"))
        val dualCastellanoVo = source(
            id = "dual-cast-vo",
            title = "Serie.Dual.Castellano.Ingles.1080p",
            languages = listOf("ES", "EN"),
        )

        val result = prioritize(
            listOf(latinoWithEsFlag, vo, castellano, dualCastellanoVo),
            supportedHdr = setOf(HdrType.SDR),
            preferSpanish = true,
        )

        assertEquals(listOf("dual-cast-vo", "castellano", "vo", "latino-es-flag"), result.map { it.id })
    }

    @Test
    fun `resolution outranks HDR within the same availability tier`() {
        val dv1080 = source(id = "dv-1080", quality = Quality.FHD_1080, hdrType = HdrType.DOLBY_VISION)
        val sdr4k = source(id = "sdr-4k", quality = Quality.UHD_2160, hdrType = HdrType.SDR)

        val result = prioritize(
            listOf(dv1080, sdr4k),
            supportedHdr = setOf(HdrType.SDR, HdrType.HDR10, HdrType.DOLBY_VISION),
        )

        assertEquals(listOf("sdr-4k", "dv-1080"), result.map { it.id })
    }

    @Test
    fun `empty supportedHdr does not promote HDR over SDR under AUTOMATIC`() {
        val smallerDv = source(
            id = "smaller-dv",
            hdrType = HdrType.DOLBY_VISION,
            sizeBytes = 5_000_000_000L,
        )
        val largerSdr = source(
            id = "larger-sdr",
            hdrType = HdrType.SDR,
            sizeBytes = 10_000_000_000L,
        )

        val result = prioritize(
            listOf(smallerDv, largerSdr),
            supportedHdr = emptySet(),
            hdrPreference = HdrPreference.AUTOMATIC,
        )

        assertEquals(listOf("larger-sdr", "smaller-dv"), result.map { it.id })
    }

    @Test
    fun `PREFER_SDR puts SDR ahead of HDR within the same resolution`() {
        val dv = source(id = "dv", hdrType = HdrType.DOLBY_VISION, sizeBytes = 20_000_000_000L)
        val hdr10 = source(id = "hdr10", hdrType = HdrType.HDR10, sizeBytes = 15_000_000_000L)
        val sdr = source(id = "sdr", hdrType = HdrType.SDR, sizeBytes = 8_000_000_000L)

        val result = prioritize(
            listOf(dv, hdr10, sdr),
            supportedHdr = setOf(HdrType.SDR, HdrType.HDR10, HdrType.DOLBY_VISION),
            hdrPreference = HdrPreference.PREFER_SDR,
        )

        assertEquals(listOf("sdr", "hdr10", "dv"), result.map { it.id })
    }

    @Test
    fun `IGNORE HDR preference orders purely by size after resolution`() {
        val smallerDv = source(id = "smaller-dv", hdrType = HdrType.DOLBY_VISION, sizeBytes = 5_000_000_000L)
        val largerSdr = source(id = "larger-sdr", hdrType = HdrType.SDR, sizeBytes = 12_000_000_000L)

        val result = prioritize(
            listOf(smallerDv, largerSdr),
            supportedHdr = setOf(HdrType.SDR, HdrType.HDR10, HdrType.DOLBY_VISION),
            hdrPreference = HdrPreference.IGNORE,
        )

        assertEquals(listOf("larger-sdr", "smaller-dv"), result.map { it.id })
    }

    @Test
    fun `cached sources sort by size before seeders while uncached sort by seeders before size`() {
        val cachedSmallHighSeeds = source(
            id = "cached-small",
            isCached = true,
            seeders = 500,
            sizeBytes = 4_000_000_000L,
        )
        val cachedLargeLowSeeds = source(
            id = "cached-large",
            isCached = true,
            seeders = 10,
            sizeBytes = 20_000_000_000L,
        )
        val uncachedSmallHighSeeds = source(
            id = "uncached-small",
            isCached = false,
            seeders = 200,
            sizeBytes = 4_000_000_000L,
        )
        val uncachedLargeLowSeeds = source(
            id = "uncached-large",
            isCached = false,
            seeders = 5,
            sizeBytes = 20_000_000_000L,
        )

        val result = prioritize(
            listOf(cachedSmallHighSeeds, uncachedLargeLowSeeds, uncachedSmallHighSeeds, cachedLargeLowSeeds),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(
            listOf("cached-large", "cached-small", "uncached-small", "uncached-large"),
            result.map { it.id },
        )
    }

    @Test
    fun `confirmed cached source outranks unconfirmed resolveUrl source`() {
        val unconfirmedResolve = source(
            id = "unconfirmed",
            isCached = false,
            resolveUrl = "https://addon.test/resolve/123",
            sizeBytes = 20_000_000_000L,
        )
        val confirmedCached = source(
            id = "confirmed",
            isCached = true,
            sizeBytes = 5_000_000_000L,
        )

        val result = prioritize(
            listOf(unconfirmedResolve, confirmedCached),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(listOf("confirmed", "unconfirmed"), result.map { it.id })
    }

    @Test
    fun `resolution outranks seed count within the same availability tier`() {
        val seeded = source(id = "seeded", quality = Quality.HD_720, seeders = 100)
        val higherQuality = source(id = "higher-quality", quality = Quality.UHD_2160, seeders = 1)

        val result = prioritize(
            listOf(higherQuality, seeded),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(listOf("higher-quality", "seeded"), result.map { it.id })
    }

    @Test
    fun `non-cached zero-seed torrent is demoted below seeded lower-quality source`() {
        val zeroSeed4K = source(
            id = "zero-seed-4k",
            quality = Quality.UHD_2160,
            seeders = 0,
            isCached = false,
        )
        val seeded720 = source(
            id = "seeded-720",
            quality = Quality.HD_720,
            seeders = 2,
            isCached = false,
        )

        val result = prioritize(
            listOf(zeroSeed4K, seeded720),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(listOf("seeded-720", "zero-seed-4k"), result.map { it.id })
    }

    @Test
    fun `cached and direct sources do not depend on seeders`() {
        val cached = source(id = "cached", seeders = 0, isCached = true, quality = Quality.UHD_2160)
        val direct = source(id = "direct", seeders = 0, isCached = false, directUrl = "https://video.test/direct")
        val seeded = source(id = "seeded", seeders = 100, isCached = false)

        val result = prioritize(
            listOf(seeded, direct, cached),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(listOf("cached", "direct", "seeded"), result.map { it.id })
    }

    @Test
    fun `unknown seeder count stays above non-cached zero-seed torrent`() {
        val unknown = source(id = "unknown", seeders = null, isCached = false)
        val zero = source(id = "zero", seeders = 0, isCached = false)
        val seeded = source(id = "seeded", seeders = 1, isCached = false)

        val result = prioritize(
            listOf(zero, unknown, seeded),
            supportedHdr = setOf(HdrType.SDR),
        )

        assertEquals(listOf("seeded", "unknown", "zero"), result.map { it.id })
    }

    private fun source(
        id: String,
        title: String = id,
        quality: Quality = Quality.FHD_1080,
        hdrType: HdrType = HdrType.SDR,
        sizeBytes: Long = 1_000_000_000L,
        videoCodec: VideoCodec? = null,
        softwareDecode: Boolean = false,
        seeders: Int? = 10,
        isCached: Boolean = true,
        languages: List<String> = emptyList(),
        directUrl: String? = null,
        resolveUrl: String? = null,
    ) = StreamSource(
        id = id,
        title = title,
        infoHash = id.padEnd(40, '0'),
        fileIdx = null,
        magnet = "magnet:?xt=urn:btih:$id",
        quality = quality,
        hdrType = hdrType,
        sizeBytes = sizeBytes,
        seeders = seeders,
        isCached = isCached,
        languages = languages,
        directUrl = directUrl,
        resolveUrl = resolveUrl,
        videoCodec = videoCodec,
        softwareDecode = softwareDecode,
    )
}
