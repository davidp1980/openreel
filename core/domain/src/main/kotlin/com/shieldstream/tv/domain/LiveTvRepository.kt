package com.shieldstream.tv.domain

import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveTvSection

interface LiveTvRepository {
    suspend fun loadMainSections(forceRefresh: Boolean = false): List<LiveTvSection>
    suspend fun loadThematicChannels(forceRefresh: Boolean = false): List<LiveChannel>
    suspend fun loadOpenSections(forceRefresh: Boolean = false): List<LiveTvSection> = emptyList()
}

fun interface LiveDrmLicenseProvider {
    suspend fun widevineLicenseUrl(tokenUrl: String): String?
}
