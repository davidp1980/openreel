package com.shieldstream.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.LiveTvRepository
import com.shieldstream.tv.domain.RtveLivePlaybackResolver
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.LiveBlockReason
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveChannelEntry
import com.shieldstream.tv.model.LivePlaybackDecision
import com.shieldstream.tv.model.LiveTvSection
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState
    data class Content(
        val sections: List<LiveTvSection>,
        val refreshing: Boolean = false,
    ) : LiveTvUiState
    data class Error(val message: String) : LiveTvUiState
}

data class LiveLineupSelection(
    val channels: List<LiveChannelEntry>,
    val index: Int,
)

data class M3uEditorState(
    val initialUrl: String,
    val hasSavedUrl: Boolean,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val resolver: RtveLivePlaybackResolver,
    private val settings: SettingsRepository,
    private val texts: Texts,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveTvUiState>(LiveTvUiState.Loading)
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _m3uEditor = MutableStateFlow<M3uEditorState?>(null)
    val m3uEditor: StateFlow<M3uEditorState?> = _m3uEditor.asStateFlow()

    val customM3uUrl: StateFlow<String?> = settings.customM3uUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var activated = false
    private var loadJob: Job? = null

    fun activate() {
        if (activated) return
        activated = true
        load(forceRefresh = false, silent = false)
    }

    fun refresh() {
        activated = true
        load(forceRefresh = true, silent = false)
    }

    fun refreshSilent() {
        if (!activated) return
        if (loadJob?.isActive == true || _uiState.value !is LiveTvUiState.Content) return
        load(forceRefresh = true, silent = true)
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun openM3uEditor() {
        val current = customM3uUrl.value.orEmpty()
        _m3uEditor.value = M3uEditorState(
            initialUrl = current,
            hasSavedUrl = current.isNotBlank(),
        )
    }

    fun closeM3uEditor() {
        if (_m3uEditor.value?.busy != true) {
            _m3uEditor.value = null
        }
    }

    fun saveM3uUrl(input: String) {
        val current = _m3uEditor.value ?: return
        if (current.busy) return
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            _m3uEditor.value = current.copy(error = texts.get(StringsR.string.live_tv_m3u_error_empty))
            return
        }
        if (!isValidHttpUrl(normalized)) {
            _m3uEditor.value = current.copy(error = texts.get(StringsR.string.live_tv_m3u_error_invalid_url))
            return
        }
        _m3uEditor.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val m3uSections = repository.validateAndLoadCustomM3u(normalized)
                settings.setCustomM3uUrl(normalized)
                val channelCount = m3uSections.sumOf { it.channels.size }
                _uiState.update { state ->
                    when (state) {
                        is LiveTvUiState.Content -> {
                            val rtveOnly = state.sections.filterNot { isCustomM3uSectionId(it.id) }
                            state.copy(sections = rtveOnly + m3uSections)
                        }
                        else -> LiveTvUiState.Content(sections = m3uSections, refreshing = false)
                    }
                }
                _m3uEditor.value = null
                _notice.value = texts.get(StringsR.string.live_tv_m3u_imported, channelCount)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _m3uEditor.value = current.copy(
                    busy = false,
                    error = texts.get(StringsR.string.live_tv_m3u_error_load),
                )
            }
        }
    }

    fun removeM3uUrl() {
        val current = _m3uEditor.value ?: return
        if (current.busy) return
        viewModelScope.launch {
            settings.setCustomM3uUrl(null)
            _uiState.update { state ->
                (state as? LiveTvUiState.Content)?.let { content ->
                    content.copy(sections = content.sections.filterNot { isCustomM3uSectionId(it.id) })
                } ?: state
            }
            _m3uEditor.value = null
            _notice.value = texts.get(StringsR.string.live_tv_m3u_removed)
        }
    }

    fun resolvePlayback(channel: LiveChannel): LivePlaybackDecision.Ready? =
        when (val decision = resolver.resolve(channel)) {
            is LivePlaybackDecision.Ready -> decision
            is LivePlaybackDecision.Blocked -> {
                _notice.value = blockedMessage(decision)
                null
            }
        }

    fun buildPlaybackLineup(
        selected: LiveChannel,
        sourceSections: List<LiveTvSection>? = null,
    ): LiveLineupSelection? {
        val ready = resolvePlayback(selected) ?: return null
        val selectedEntry = selected.toEntry(ready)
        val sections = sourceSections ?: (_uiState.value as? LiveTvUiState.Content)?.sections.orEmpty()
        val entries = sections
            .flatMap { it.channels }
            .distinctBy { it.key }
            .mapNotNull { channel ->
                (resolver.resolve(channel) as? LivePlaybackDecision.Ready)?.let { channel.toEntry(it) }
            }
        val index = entries.indexOfFirst { it.key == selected.key }
        return if (index < 0) {
            LiveLineupSelection(listOf(selectedEntry), 0)
        } else {
            LiveLineupSelection(entries, index)
        }
    }

    private fun LiveChannel.toEntry(ready: LivePlaybackDecision.Ready): LiveChannelEntry {
        val channelLabel = if (directUrls.isNotEmpty()) {
            null
        } else {
            category?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
        }
        return LiveChannelEntry(
            key = key,
            title = title,
            channelName = channelLabel,
            logoUrl = logoUrl,
            sources = ready.sources,
        )
    }

    private fun load(forceRefresh: Boolean, silent: Boolean) {
        if (!silent) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previousSections = (_uiState.value as? LiveTvUiState.Content)?.sections.orEmpty()
            val previousThematic = previousSections.firstOrNull { it.id == THEMATIC_SECTION_ID }
            val previousM3u = previousSections.filter { isCustomM3uSectionId(it.id) }
            if (!silent) {
                _uiState.update { current ->
                    (current as? LiveTvUiState.Content)?.copy(refreshing = true) ?: LiveTvUiState.Loading
                }
            }

            val mainSections = try {
                repository.loadMainSections(forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!silent) {
                    _uiState.value = LiveTvUiState.Error(texts.get(StringsR.string.live_tv_error))
                } else {
                    _uiState.update { (it as? LiveTvUiState.Content)?.copy(refreshing = false) ?: it }
                }
                return@launch
            }

            val baseSections = if (silent) {
                buildList {
                    addAll(mainSections)
                    if (previousThematic != null) add(previousThematic)
                    addAll(previousM3u)
                }
            } else {
                mainSections
            }
            _uiState.value = LiveTvUiState.Content(sections = baseSections, refreshing = false)

            val thematic = try {
                repository.loadThematicChannels(forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            val customM3uSections = try {
                repository.loadCustomM3uSections(forceRefresh = forceRefresh && !silent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            val thematicSection = when {
                thematic != null -> thematic.takeIf { it.isNotEmpty() }?.let {
                    LiveTvSection(
                        id = THEMATIC_SECTION_ID,
                        title = texts.get(StringsR.string.live_tv_section_thematic),
                        channels = it,
                    )
                }
                silent -> previousThematic
                else -> null
            }
            val resolvedM3u = customM3uSections ?: if (silent) previousM3u else emptyList()

            if (thematic != null || customM3uSections != null) {
                val updated = buildList {
                    addAll(mainSections)
                    if (thematicSection != null) add(thematicSection)
                    addAll(resolvedM3u)
                }
                _uiState.value = LiveTvUiState.Content(sections = updated, refreshing = false)
            }
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    private fun blockedMessage(decision: LivePlaybackDecision.Blocked): String = when (decision.reason) {
        LiveBlockReason.GEO_RESTRICTED -> texts.get(StringsR.string.live_tv_blocked_geo)
        LiveBlockReason.NOT_STARTED_YET -> decision.startsAtMillis?.let { millis ->
            val time = SimpleDateFormat("HH:mm", Lang.locale).format(Date(millis))
            texts.get(StringsR.string.live_tv_blocked_upcoming, time)
        } ?: texts.get(StringsR.string.live_tv_blocked_upcoming_soon)
        LiveBlockReason.SUBSCRIPTION_REQUIRED -> texts.get(StringsR.string.live_tv_blocked_paid)
        LiveBlockReason.NO_SOURCE -> texts.get(StringsR.string.live_tv_blocked_unavailable)
    }

    internal companion object {
        const val THEMATIC_SECTION_ID = "rtve_thematic_channels"
        const val M3U_SECTION_PREFIX = "m3u_"

        internal fun isCustomM3uSectionId(id: String): Boolean =
            id.startsWith(M3U_SECTION_PREFIX)
    }
}
