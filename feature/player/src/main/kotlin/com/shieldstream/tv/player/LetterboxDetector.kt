package com.shieldstream.tv.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import com.google.common.util.concurrent.MoreExecutors
import com.shieldstream.tv.player.thumbs.ThumbFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Measures hardcoded top/bottom black bars baked inside a 16:9 video frame (e.g. Blu-ray remuxes
 * storing 2.39:1 inside 1920x1080) by sampling a few frames and taking the minimum bar height.
 */
@OptIn(UnstableApi::class)
internal object LetterboxDetector {
    private const val TAG = "Letterbox"
    private const val HEIGHT_PX = 270
    private const val FRAME_TIMEOUT_MS = 8_000L
    private const val MIN_VALID_FRAMES = 2
    private const val ENOUGH_FRAMES = 4
    private val SAMPLE_FRACTIONS = listOf(0.15, 0.3, 0.45, 0.6, 0.75, 0.88)

    suspend fun measureScale(context: Context, uri: Uri, pqHdr: Boolean, durationMs: Long): Float {
        val extractor = runCatching {
            ThumbFrameExtractor(
                context,
                ThumbFrameExtractor.Configuration.Builder()
                    .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    .setMediaCodecSelector(HardwareWithoutDolbyVision)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Could not create extractor: ${it.message}") }.getOrNull() ?: return 1f
        try {
            extractor.setMediaItem(
                MediaItem.fromUri(uri),
                buildList<Effect> {
                    add(Presentation.createForHeight(HEIGHT_PX))
                    if (pqHdr) add(PqToSdr.lut)
                },
            )
            var minTop = Int.MAX_VALUE
            var minBottom = Int.MAX_VALUE
            var height = 0
            var valid = 0
            for (fraction in SAMPLE_FRACTIONS) {
                val positionMs = (durationMs * fraction).toLong()
                val result = withTimeoutOrNull(FRAME_TIMEOUT_MS) { frameAt(extractor, positionMs) }
                if (result == null) {
                    Log.w(TAG, "Frame at $positionMs ms timed out")
                    break
                }
                val bitmap = result.getOrNull() ?: continue
                val bars = withContext(Dispatchers.Default) { bars(bitmap) } ?: continue
                height = bitmap.height
                minTop = minOf(minTop, bars.first)
                minBottom = minOf(minBottom, bars.second)
                if (++valid >= ENOUGH_FRAMES) break
            }
            if (valid < MIN_VALID_FRAMES || height == 0) {
                Log.i(TAG, "Not enough valid frames ($valid)")
                return 1f
            }
            val bar = minOf(minTop, minBottom)
            val scale = height.toFloat() / (height - 2 * bar)
            Log.i(TAG, "Bars: top $minTop, bottom $minBottom of $height px -> scale x${"%.3f".format(scale)}")
            return if (scale < 1.03f || scale > 1.6f) 1f else scale
        } finally {
            runCatching { extractor.release() }
        }
    }

    private suspend fun frameAt(extractor: ThumbFrameExtractor, positionMs: Long): Result<Bitmap> =
        suspendCancellableCoroutine { cont ->
            val future = extractor.getFrame(positionMs)
            future.addListener(
                {
                    val result = runCatching { future.get().bitmap }
                    if (cont.isActive) cont.resume(result)
                },
                MoreExecutors.directExecutor(),
            )
        }

    private fun bars(bitmap: Bitmap): Pair<Int, Int>? {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        fun isBlack(y: Int): Boolean {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var bright = 0
            for (p in row) {
                val luma = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
                if (luma > 40) bright++
            }
            return bright <= w / 100
        }
        var top = 0
        while (top < h / 2 && isBlack(top)) top++
        if (top >= h / 2) return null
        var bottom = 0
        while (bottom < h / 2 && isBlack(h - 1 - bottom)) bottom++
        if (kotlin.math.abs(top - bottom) > h / 25) return Pair(0, 0)
        return Pair(top, bottom)
    }
}
