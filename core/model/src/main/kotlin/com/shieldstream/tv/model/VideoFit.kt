package com.shieldstream.tv.model

enum class VideoFit {
    FIT,
    ZOOM,
    STRETCH,
    ;

    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]
}
