package com.shieldstream.tv.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Measures hardcoded top/bottom black bars baked inside a 16:9 video frame (e.g. Blu-ray remuxes
 * storing 2.39:1 inside 1920x1080) by capturing a downscaled frame from the active [SurfaceView]
 * via [PixelCopy] without opening a second hardware decoder or performing extra HTTP requests.
 */
internal object LetterboxDetector {
    private const val TAG = "Letterbox"
    private const val TARGET_HEIGHT_PX = 180
    private const val COPY_TIMEOUT_MS = 1_500L
    private const val MIN_SCALE = 1.03f
    private const val MAX_SCALE = 1.6f

    suspend fun measureScale(surfaceView: SurfaceView): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val surface = surfaceView.holder?.surface ?: return null
        if (!surface.isValid) return null
        val srcW = surfaceView.width
        val srcH = surfaceView.height
        if (srcW <= 0 || srcH <= 0) return null

        val dstH = TARGET_HEIGHT_PX.coerceAtMost(srcH)
        val dstW = (srcW.toFloat() * dstH / srcH).roundToInt().coerceAtLeast(1)
        val bitmap = runCatching {
            Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null

        try {
            val copied = withTimeoutOrNull(COPY_TIMEOUT_MS) {
                copySurface(surfaceView, bitmap)
            } ?: false
            if (!copied) {
                Log.i(TAG, "PixelCopy failed or timed out; using fallback scale")
                return null
            }
            val bars = withContext(Dispatchers.Default) { bars(bitmap) } ?: return null
            val bar = minOf(bars.first, bars.second)
            val usableHeight = bitmap.height - 2 * bar
            if (usableHeight <= 0) return null
            val scale = bitmap.height.toFloat() / usableHeight
            Log.i(TAG, "Bars: top ${bars.first}, bottom ${bars.second} of ${bitmap.height} px -> scale x${"%.3f".format(scale)}")
            return scale.takeIf { it in MIN_SCALE..MAX_SCALE }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun copySurface(surfaceView: SurfaceView, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val requested = runCatching {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { result ->
                        if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS)
                    },
                    handler,
                )
            }
            if (requested.isFailure && cont.isActive) {
                Log.w(TAG, "PixelCopy request error: ${requested.exceptionOrNull()?.message}")
                cont.resume(false)
            }
        }
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
        if (abs(top - bottom) > h / 25) return null
        return Pair(top, bottom)
    }
}
