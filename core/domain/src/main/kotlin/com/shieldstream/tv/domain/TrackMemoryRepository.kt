package com.shieldstream.tv.domain

interface TrackMemoryRepository {
    fun audio(mediaKey: String): String?

    fun subtitle(mediaKey: String): String?

    fun saveAudio(mediaKey: String, language: String)

    fun saveSubtitle(mediaKey: String, language: String)

    fun saveSubtitlesOff(mediaKey: String)

    companion object {
        const val SUBTITLES_OFF = "off"
    }
}
