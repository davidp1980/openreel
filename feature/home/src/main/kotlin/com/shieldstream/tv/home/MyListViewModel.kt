package com.shieldstream.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.CatalogRepository
import com.shieldstream.tv.domain.EpisodeReleaseRepository
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.MyListEntry
import com.shieldstream.tv.domain.MyListRepository
import com.shieldstream.tv.domain.TraktRepository
import com.shieldstream.tv.domain.WatchProgressRepository
import com.shieldstream.tv.domain.mapConcurrent
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.MediaType
import com.shieldstream.tv.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class MyListFilter { ALL, TV, MOVIES, HISTORY }
enum class MyListSort { NEW_FIRST, RECENT, TITLE }

data class HistoryBadge(
    val season: Int,
    val episode: Int,
    val watchedCount: Int,
)

internal data class HistoryGroup(
    val type: MediaType,
    val id: String,
    val watchedAt: Long,
    val keys: List<String>,
    val badge: HistoryBadge?,
) {
    val key: String get() = "${type.name}:$id"
}

private data class MyListControls(
    val filter: MyListFilter,
    val sort: MyListSort,
    val hideCompleted: Boolean,
)

sealed interface MyListUiState {
    data object Loading : MyListUiState
    data class Empty(
        val filter: MyListFilter,
        val sort: MyListSort = MyListSort.RECENT,
        val hideCompleted: Boolean = false,
    ) : MyListUiState
    data class Content(
        val filter: MyListFilter,
        val items: List<CatalogItem>,
        val newEpisodeCounts: Map<String, Int> = emptyMap(),
        val historyBadges: Map<String, HistoryBadge> = emptyMap(),
        val sort: MyListSort,
        val hideCompleted: Boolean,
        val traktConnected: Boolean = false,
    ) : MyListUiState
}

private data class MyListQuery(
    val entries: List<MyListEntry>,
    val controls: MyListControls,
    val watchedProgress: Map<String, Long>,
    val dismissedKeys: Set<String>,
)

private data class LoadedMyListItem(
    val item: CatalogItem,
    val addedAt: Long,
    val newEpisodes: List<Episode> = emptyList(),
    val complete: Boolean = false,
)

private data class LoadedHistoryItem(
    val item: CatalogItem,
    val watchedAt: Long,
    val badge: HistoryBadge?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyListViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val myList: MyListRepository,
    private val watchProgress: WatchProgressRepository,
    private val episodeReleases: EpisodeReleaseRepository,
    private val tracking: EpisodeTrackingRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(MyListFilter.ALL)
    private val sort = MutableStateFlow(MyListSort.RECENT)
    private val hideCompleted = MutableStateFlow(false)
    private val controls = combine(filter, sort, hideCompleted, ::MyListControls)

    private val loaded = ConcurrentHashMap<String, CatalogItem>()

    val uiState: StateFlow<MyListUiState> = combine(
        myList.entries,
        watchProgress.progress,
        tracking.dismissedEpisodeKeys,
        controls,
    ) { entries, progress, dismissed, currentControls ->
        MyListQuery(
            entries,
            currentControls,
            progress.filterValues { it.isWatched }.mapValues { it.value.updatedAt },
            dismissed,
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { query -> load(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState.Loading)

    private fun load(query: MyListQuery): Flow<MyListUiState> {
        val current = query.controls
        if (current.filter == MyListFilter.HISTORY) return loadHistory(query)
        return flow {
            val wanted = query.entries.filter {
                when (current.filter) {
                    MyListFilter.ALL -> true
                    MyListFilter.TV -> it.type == MediaType.TV
                    MyListFilter.MOVIES -> it.type == MediaType.MOVIE
                    MyListFilter.HISTORY -> false
                }
            }
            // Under Recent, persisted items appear at once in their final order; new-episode badges and
            // uncached items follow when their requests finish. A cold start then needs no TMDB request
            // before the list is visible.
            if (current.sort == MyListSort.RECENT && !current.hideCompleted) {
                val cached = wanted.mapNotNull { entry ->
                    val item = loaded[entry.key] ?: runCatching { catalogRepository.cachedItem(entry.type, entry.id) }.getOrNull()
                    item?.let { LoadedMyListItem(it, entry.addedAt) }
                }
                if (cached.isNotEmpty()) {
                    emit(buildState(cached, current))
                    emit(buildState(loadItems(wanted, current), current))
                    return@flow
                }
            }
            // Without cached items, the newest entries are still exactly the top of the final list under
            // Recent, so they can appear first. Other sorts depend on data from every item; an early batch
            // would reshuffle when the rest arrived, so they keep showing the previous list until the
            // full one is ready.
            if (current.sort != MyListSort.RECENT || wanted.size <= INITIAL_BATCH_SIZE) {
                emit(buildState(loadItems(wanted, current), current))
                return@flow
            }
            val newestFirst = wanted.sortedByDescending { it.addedAt }
            val initial = loadItems(newestFirst.take(INITIAL_BATCH_SIZE), current)
            emit(buildState(initial, current))
            val remaining = loadItems(newestFirst.drop(INITIAL_BATCH_SIZE), current)
            emit(buildState(initial + remaining, current))
        }
    }

    private fun loadHistory(query: MyListQuery): Flow<MyListUiState> = flow {
        val effectiveSort = if (query.controls.sort == MyListSort.TITLE) MyListSort.TITLE else MyListSort.RECENT
        val groups = groupWatchHistory(query.watchedProgress)
        if (groups.isEmpty()) {
            emit(MyListUiState.Empty(MyListFilter.HISTORY, effectiveSort, hideCompleted = false))
            return@flow
        }
        if (effectiveSort == MyListSort.RECENT) {
            val cached = groups.mapNotNull { group ->
                val item = loaded[group.key] ?: runCatching { catalogRepository.cachedItem(group.type, group.id) }.getOrNull()
                item?.let {
                    loaded[group.key] = it
                    LoadedHistoryItem(it, group.watchedAt, group.badge)
                }
            }
            if (cached.isNotEmpty()) {
                emit(buildHistoryState(cached, effectiveSort))
                emit(buildHistoryState(loadHistoryItems(groups), effectiveSort))
                return@flow
            }
        }
        if (effectiveSort != MyListSort.RECENT || groups.size <= INITIAL_BATCH_SIZE) {
            emit(buildHistoryState(loadHistoryItems(groups), effectiveSort))
            return@flow
        }
        val initial = loadHistoryItems(groups.take(INITIAL_BATCH_SIZE))
        emit(buildHistoryState(initial, effectiveSort))
        val remaining = loadHistoryItems(groups.drop(INITIAL_BATCH_SIZE))
        emit(buildHistoryState(initial + remaining, effectiveSort))
    }

    private suspend fun loadHistoryItems(groups: List<HistoryGroup>): List<LoadedHistoryItem> =
        groups.mapConcurrent(concurrency = 4) { group ->
            val item = loaded[group.key] ?: withTimeoutOrNull(5_000) {
                runCatching { catalogRepository.getItem(group.type, group.id) }.getOrNull()
            }?.also { loaded[group.key] = it }
            item?.let { LoadedHistoryItem(it, group.watchedAt, group.badge) }
        }.filterNotNull()

    private fun buildHistoryState(items: List<LoadedHistoryItem>, sort: MyListSort): MyListUiState {
        val sorted = when (sort) {
            MyListSort.TITLE -> items.sortedBy { it.item.title.lowercase(Lang.locale) }
            MyListSort.RECENT, MyListSort.NEW_FIRST -> items.sortedByDescending { it.watchedAt }
        }
        if (sorted.isEmpty()) return MyListUiState.Empty(MyListFilter.HISTORY, sort, hideCompleted = false)
        return MyListUiState.Content(
            filter = MyListFilter.HISTORY,
            items = sorted.map { it.item },
            historyBadges = sorted.mapNotNull { entry -> entry.badge?.let { entry.item.key to it } }.toMap(),
            sort = sort,
            hideCompleted = false,
            traktConnected = trakt.isConnected,
        )
    }

    private suspend fun loadItems(
        entries: List<MyListEntry>,
        current: MyListControls,
    ): List<LoadedMyListItem> = entries.mapConcurrent(concurrency = 4) { entry ->
        val status = if (entry.type == MediaType.TV) {
            withTimeoutOrNull(8_000) { runCatching { episodeReleases.status(entry.id) }.getOrNull() }
        } else {
            null
        }
        val item = loaded[entry.key] ?: status?.item ?: withTimeoutOrNull(5_000) {
            runCatching { catalogRepository.getItem(entry.type, entry.id) }.getOrNull()
        }?.also { loaded[entry.key] = it }
        val complete = current.hideCompleted && entry.type == MediaType.TV &&
            runCatching { episodeReleases.isComplete(entry.id) }.getOrDefault(false)
        item?.let { LoadedMyListItem(it, entry.addedAt, status?.availableEpisodes.orEmpty(), complete) }
    }.filterNotNull().filterNot { it.complete }

    private fun buildState(items: List<LoadedMyListItem>, current: MyListControls): MyListUiState {
        val sorted = when (current.sort) {
            MyListSort.NEW_FIRST -> items.sortedWith(
                compareByDescending<LoadedMyListItem> { it.newEpisodes.size }.thenByDescending { it.addedAt },
            )
            MyListSort.RECENT -> items.sortedByDescending { it.addedAt }
            MyListSort.TITLE -> items.sortedBy { it.item.title.lowercase(Lang.locale) }
        }
        if (sorted.isEmpty()) return MyListUiState.Empty(current.filter, current.sort, current.hideCompleted)
        return MyListUiState.Content(
            filter = current.filter,
            items = sorted.map { it.item },
            newEpisodeCounts = sorted.associate { it.item.key to it.newEpisodes.size },
            sort = current.sort,
            hideCompleted = current.hideCompleted,
        )
    }

    fun setFilter(value: MyListFilter) { filter.value = value }
    fun setSort(value: MyListSort) { sort.value = value }
    fun toggleHideCompleted() { hideCompleted.value = !hideCompleted.value }

    fun remove(item: CatalogItem) = myList.remove(item.type, item.id)

    fun removeFromHistory(item: CatalogItem) {
        val current = watchProgress.progress.value
        val keys = when (item.type) {
            MediaType.MOVIE -> {
                val key = WatchProgress.movieKey(item.id)
                if (current[key]?.isWatched == true) listOf(key) else emptyList()
            }
            MediaType.TV -> {
                val prefix = WatchProgress.showPrefix(item.id)
                current.filter { (key, p) -> key.startsWith(prefix) && p.isWatched }.keys.toList()
            }
        }
        if (keys.isEmpty()) return
        trakt.setWatched(keys, watched = false)
        watchProgress.removeRemote(keys)
    }

    fun clearHistory() {
        val keys = watchProgress.progress.value.filterValues { it.isWatched }.keys.toList()
        if (keys.isEmpty()) return
        trakt.setWatched(keys, watched = false)
        watchProgress.removeRemote(keys)
    }
}

private data class HistoryEpisodeEntry(
    val key: String,
    val season: Int,
    val episode: Int,
    val updatedAt: Long,
)

internal fun groupWatchHistory(watchedProgress: Map<String, Long>): List<HistoryGroup> {
    if (watchedProgress.isEmpty()) return emptyList()
    val movies = ArrayList<HistoryGroup>()
    val shows = LinkedHashMap<String, MutableList<HistoryEpisodeEntry>>()

    for ((key, updatedAt) in watchedProgress) {
        val parts = key.split(':')
        when {
            parts.size == 2 && parts[0] == "movie" && parts[1].isNotBlank() -> {
                movies += HistoryGroup(
                    type = MediaType.MOVIE,
                    id = parts[1],
                    watchedAt = updatedAt,
                    keys = listOf(key),
                    badge = null,
                )
            }
            parts.size == 4 && parts[0] == "tv" && parts[1].isNotBlank() -> {
                val season = parts[2].toIntOrNull() ?: continue
                val episode = parts[3].toIntOrNull() ?: continue
                shows.getOrPut(parts[1]) { ArrayList() }.add(HistoryEpisodeEntry(key, season, episode, updatedAt))
            }
        }
    }

    val groupedShows = shows.map { (showId, episodes) ->
        val latest = episodes.maxWith(
            compareBy<HistoryEpisodeEntry> { it.updatedAt }.thenBy { it.season }.thenBy { it.episode },
        )
        HistoryGroup(
            type = MediaType.TV,
            id = showId,
            watchedAt = latest.updatedAt,
            keys = episodes.map { it.key },
            badge = HistoryBadge(latest.season, latest.episode, episodes.size),
        )
    }

    return (movies + groupedShows).sortedByDescending { it.watchedAt }
}

private const val INITIAL_BATCH_SIZE = 20
