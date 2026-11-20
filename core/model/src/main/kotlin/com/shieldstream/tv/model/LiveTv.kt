package com.shieldstream.tv.model

data class LiveChannel(
    val id: String,
    val assetId: String? = null,
    val title: String,
    val category: String? = null,
    val imageUrl: String? = null,
    val logoUrl: String? = null,
    val isOnAir: Boolean = true,
    val startsAtMillis: Long? = null,
    val durationMinutes: Int? = null,
    val progressPercent: Int? = null,
    val allowedInCountry: Boolean? = null,
    val paid: Boolean = false,
    val hasDrm: Boolean = false,
    val directUrls: List<String> = emptyList(),
    val provider: String = "RTVE",
) {
    val key: String get() = "${id}_${assetId.orEmpty()}"

    fun progressAt(nowMillis: Long): Float? {
        val start = startsAtMillis
        val minutes = durationMinutes
        if (start != null && minutes != null && minutes > 0) {
            return ((nowMillis - start).toFloat() / (minutes * 60_000L)).coerceIn(0f, 1f)
        }
        return progressPercent?.let { (it / 100f).coerceIn(0f, 1f) }
    }

    fun isUpcomingAt(nowMillis: Long): Boolean =
        !isOnAir && startsAtMillis != null && startsAtMillis > nowMillis
}

data class LiveTvSection(
    val id: String,
    val title: String,
    val channels: List<LiveChannel>,
)

data class LiveChannelEntry(
    val key: String,
    val title: String,
    val channelName: String? = null,
    val logoUrl: String? = null,
    val sources: List<StreamSource>,
) {
    val displayTitle: String
        get() = listOfNotNull(
            channelName?.takeIf { it.isNotBlank() },
            title.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" · ").ifBlank { key }
}

enum class LiveBlockReason {
    GEO_RESTRICTED,
    NOT_STARTED_YET,
    SUBSCRIPTION_REQUIRED,
    NO_SOURCE,
}

sealed interface LivePlaybackDecision {
    data class Ready(
        val sources: List<StreamSource>,
        val title: String,
    ) : LivePlaybackDecision

    data class Blocked(
        val reason: LiveBlockReason,
        val startsAtMillis: Long? = null,
    ) : LivePlaybackDecision
}
