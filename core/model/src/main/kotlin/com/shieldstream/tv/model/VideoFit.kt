package com.shieldstream.tv.model

import kotlin.math.abs

data class VideoFitTransform(
    val useFill: Boolean,
    val useZoom: Boolean,
    val scaleX: Float,
    val scaleY: Float,
)

enum class VideoFit {
    FIT,
    ZOOM,
    STRETCH,
    ;

    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]

    fun computeTransform(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f,
        detectedBarScale: Float? = null,
    ): VideoFitTransform {
        if (this == FIT) {
            return VideoFitTransform(useFill = false, useZoom = false, scaleX = 1f, scaleY = 1f)
        }
        val par = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        val frameIs16x9 = videoWidth > 0 && videoHeight > 0 &&
            abs(videoWidth * par / videoHeight - ASPECT_16_9) < ASPECT_TOLERANCE
        if (!frameIs16x9) {
            return when (this) {
                FIT -> VideoFitTransform(useFill = false, useZoom = false, scaleX = 1f, scaleY = 1f)
                ZOOM -> VideoFitTransform(useFill = false, useZoom = true, scaleX = 1f, scaleY = 1f)
                STRETCH -> VideoFitTransform(useFill = true, useZoom = false, scaleX = 1f, scaleY = 1f)
            }
        }
        val barScale = detectedBarScale?.takeIf { it in MIN_LETTERBOX_SCALE..MAX_LETTERBOX_SCALE }
            ?: DEFAULT_LETTERBOX_SCALE
        return when (this) {
            FIT -> VideoFitTransform(useFill = false, useZoom = false, scaleX = 1f, scaleY = 1f)
            ZOOM -> VideoFitTransform(useFill = false, useZoom = false, scaleX = barScale, scaleY = barScale)
            STRETCH -> VideoFitTransform(useFill = true, useZoom = false, scaleX = 1f, scaleY = barScale)
        }
    }

    companion object {
        const val DEFAULT_LETTERBOX_SCALE = 4f / 3f
        private const val ASPECT_16_9 = 16f / 9f
        private const val ASPECT_TOLERANCE = 0.03f
        private const val MIN_LETTERBOX_SCALE = 1.03f
        private const val MAX_LETTERBOX_SCALE = 1.6f

        fun is16x9Frame(videoWidth: Int, videoHeight: Int, pixelWidthHeightRatio: Float = 1f): Boolean {
            if (videoWidth <= 0 || videoHeight <= 0) return false
            val par = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
            return abs(videoWidth * par / videoHeight - ASPECT_16_9) < ASPECT_TOLERANCE
        }
    }
}
