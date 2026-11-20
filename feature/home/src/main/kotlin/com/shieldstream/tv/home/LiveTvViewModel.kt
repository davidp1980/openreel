package com.shieldstream.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.LiveTvRepository
import com.shieldstream.tv.domain.RtveLivePlaybackResolver
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.LiveBlockReason
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LivePlaybackDecision
import com.shieldstream.tv.model.LiveTvSection
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState
    data class Content(
        val sections: List<LiveTvSection>,
        val refreshing: Boolean = false,
    ) : LiveTvUiState
    data class Error(val message: String) : LiveTvUiState
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val resolver: RtveLivePlaybackResolver,
    private val texts: Texts,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveTvUiState>(LiveTvUiState.Loading)
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

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

    fun resolvePlayback(channel: LiveChannel): LivePlaybackDecision.Ready? =
        when (val decision = resolver.resolve(channel)) {
            is LivePlaybackDecision.Ready -> decision
            is LivePlaybackDecision.Blocked -> {
                _notice.value = blockedMessage(decision)
                null
            }
        }

    private fun load(forceRefresh: Boolean, silent: Boolean) {
        if (!silent) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previousThematic = (_uiState.value as? LiveTvUiState.Content)
                ?.sections
                ?.firstOrNull { it.id == THEMATIC_SECTION_ID }
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

            val baseSections = if (silent && previousThematic != null) {
                mainSections + previousThematic
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

            if (thematic != null) {
                val updated = if (thematic.isNotEmpty()) {
                    mainSections + LiveTvSection(
                        id = THEMATIC_SECTION_ID,
                        title = texts.get(StringsR.string.live_tv_section_thematic),
                        channels = thematic,
                    )
                } else {
                    mainSections
                }
                _uiState.value = LiveTvUiState.Content(sections = updated, refreshing = false)
            }
        }
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
    }
}
