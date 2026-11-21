package com.shieldstream.tv.model

import kotlin.math.abs
import kotlin.math.ln

object SourceSimilarity {
    fun orderBySimilarity(picked: StreamSource, candidates: List<StreamSource>): List<StreamSource> {
        val pickedSpanish = picked.advertisesSpanishAudio
        return candidates.sortedWith(
            compareBy<StreamSource> { if (pickedSpanish && !it.advertisesSpanishAudio) 1 else 0 }
                .thenBy { resolutionDistance(picked.quality.rank, it.quality.rank) }
                .thenBy { abs(picked.hdrType.rank - it.hdrType.rank) }
                .thenBy { if (picked.audioCodecs.isEmpty() || it.audioCodecs.any { c -> c in picked.audioCodecs }) 0 else 1 }
                .thenBy { if (picked.videoCodec == null || it.videoCodec == picked.videoCodec) 0 else 1 }
                .thenBy { sizeDistance(picked.sizeBytes, it.sizeBytes) },
        )
    }

    private fun resolutionDistance(picked: Int, other: Int): Int {
        val diff = picked - other
        return if (diff >= 0) diff * 2 else (-diff) * 2 - 1
    }

    private fun sizeDistance(a: Long, b: Long): Double =
        if (a <= 0 || b <= 0) 10.0 else abs(ln(a.toDouble() / b.toDouble()))
}
