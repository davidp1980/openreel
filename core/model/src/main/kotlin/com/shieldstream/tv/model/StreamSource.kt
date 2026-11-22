package com.shieldstream.tv.model

data class StreamSource(
    val id: String,
    val title: String,
    val infoHash: String,
    val fileIdx: Int?,
    val magnet: String,
    val quality: Quality,
    val hdrType: HdrType,
    val sizeBytes: Long,
    val seeders: Int?,
    val isCached: Boolean,
    val directUrl: String? = null,
    val fileName: String? = null,
    val languages: List<String> = emptyList(),
    val videoCodec: VideoCodec? = null,
    val audioCodecs: Set<AudioCodec> = emptySet(),
    val softwareDecode: Boolean = false,
    val resolveUrl: String? = null,
    val debrid: String? = null,
    val provider: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val mimeType: String? = null,
    val drmTokenUrl: String? = null,
    val isLive: Boolean = false,
) {
    val displayLabel: String
        get() = buildString {
            append(quality.label)
            if (hdrType != HdrType.SDR) append(" · ").append(hdrType.label)
        }

    val isHdr: Boolean get() = hdrType != HdrType.SDR

    val spanishAudioScore: Int get() = SpanishAudio.score(this)

    val advertisesSpanishAudio: Boolean get() = spanishAudioScore >= 2
}

object SpanishAudio {
    private val explicitCastellano = Regex("""(?i)(\bcastellano\b|\bcast\b|\bes-es\b|\bespa[ñn]a\b)""")
    private val castellano = Regex("""(?i)(\bcastellano\b|espa[ñn]ol|\besp\b|\bspa\b|\bcast\b|\bes-es\b|\bespa[ñn]a\b)""")
    private val spanishWord = Regex("""(?i)\bspanish\b""")
    private val latino = Regex("""(?i)(\blatino\b|\blat\b|\blatam\b|\blatin\b|\bes-419\b|\bes-mx\b|\bes-la\b|\besla\b|\besp-lat\b|\blat-eng\b|\bspa-lat\b|\bmx\b)""")
    private val voOrDual = Regex("""(?i)(\bdual\b|\bmulti\b|\bvo\b|\bvose\b|\bingl[eé]s\b|\beng(lish)?\b)""")
    private val subsOnly = Regex("""(?i)(\bvose\b|\bvos\b|\bsubs?\.?\s*(esp|spa|spanish)\b)""")

    fun score(source: StreamSource): Int {
        val text = source.title + " " + source.fileName.orEmpty()
        val flagEs = "ES" in source.languages
        val flagLat = "LAT" in source.languages
        val hasExplicitCastellano = explicitCastellano.containsMatchIn(text)
        val hasLatinoText = latino.containsMatchIn(text)
        val isLatinoOnly = (hasLatinoText && !hasExplicitCastellano) || (flagLat && !hasExplicitCastellano && !flagEs)
        val hasCastellanoText = castellano.containsMatchIn(text)
        val hasSpanishWord = spanishWord.containsMatchIn(text)
        val hasVoOrDual = voOrDual.containsMatchIn(text) || source.languages.any { it != "ES" && it != "LAT" && it != "MULTI" }
        return when {
            isLatinoOnly -> -1
            subsOnly.containsMatchIn(text) && !hasCastellanoText && !hasSpanishWord && !flagEs -> 0
            (hasCastellanoText || (flagEs && source.languages.size <= 3)) && hasVoOrDual -> 4
            hasCastellanoText || (flagEs && source.languages.size <= 3) -> 3
            hasSpanishWord -> 2
            flagEs -> 1
            else -> 0
        }
    }
}
