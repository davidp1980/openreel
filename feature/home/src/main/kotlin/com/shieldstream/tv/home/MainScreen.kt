package com.shieldstream.tv.home

import com.shieldstream.tv.ui.theme.AppColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.shieldstream.tv.model.CatalogItem
import com.shieldstream.tv.model.Category
import com.shieldstream.tv.model.ContinueInfo
import com.shieldstream.tv.model.Episode
import com.shieldstream.tv.model.HomeFilter
import com.shieldstream.tv.model.LiveChannelEntry
import androidx.hilt.navigation.compose.hiltViewModel
import com.shieldstream.tv.ui.component.NavTab
import com.shieldstream.tv.ui.component.TopNavBar
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.home.calendar.CalendarScreen

@Composable
fun MainScreen(
    onExit: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onContinue: (CatalogItem, ContinueInfo) -> Unit,
    onOpenNewEpisode: (CatalogItem, Episode) -> Unit,
    onUpNext: (CatalogItem, Episode) -> Unit,
    onOpenSettings: () -> Unit,
    onPlayLive: (List<LiveChannelEntry>, Int) -> Unit = { _, _ -> },
    consumeLastLiveChannelKey: () -> String? = { null },
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    kidsProfile: Boolean = false,
    onSwitchProfile: () -> Unit = {},
) {
    var savedTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    val tabs = availableNavTabs(kidsProfile)
    val tab = visibleNavTab(savedTab, kidsProfile)
    LaunchedEffect(savedTab, kidsProfile) {
        if (savedTab != tab) savedTab = tab
    }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var focusMenuAfterSwitch by remember { mutableStateOf(false) }
    var navFocused by remember { mutableStateOf(false) }
    // While Home is still loading nothing else is focusable, so Android would hand the startup
    // focus to the top bar. Keep it unfocusable until Home is usable.
    var homeInteractive by rememberSaveable { mutableStateOf(false) }
    var homeTopNavVisible by remember { mutableStateOf(true) }
    var revealTopNav by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val navRequester = remember { FocusRequester() }
    val contentRequester = remember { FocusRequester() }
    val filter = when (tab) {
        NavTab.TV -> HomeFilter.TV
        NavTab.MOVIES -> HomeFilter.MOVIES
        else -> HomeFilter.ALL
    }
    val episodeCenter: EpisodeCenterViewModel = hiltViewModel()
    val episodeState by episodeCenter.uiState.collectAsState()
    LaunchedEffect(tab) {
        homeTopNavVisible = true
        revealTopNav = false
        if (tab == NavTab.EPISODES) episodeCenter.activate()
    }
    val focusNav: () -> Unit = { runCatching { navRequester.requestFocus() } }
    val isBrowseTab = tab == NavTab.HOME || tab == NavTab.MOVIES || tab == NavTab.TV
    val showTopNav = !isBrowseTab || homeTopNavVisible || navFocused || revealTopNav
    LaunchedEffect(revealTopNav) { if (revealTopNav) navRequester.requestFocusWithRetry() }

    val navFocusable = homeInteractive || !isBrowseTab
    BackHandler(enabled = showExitConfirmation || shouldRequestExit(category != null, navFocused || !navFocusable)) {
        showExitConfirmation = !showExitConfirmation
    }

    val onSelectTab: (NavTab) -> Unit = { selected ->
        if (selected in tabs && selected != tab) {
            savedTab = selected
            focusMenuAfterSwitch = selected != NavTab.SEARCH && selected != NavTab.MY_LIST &&
                selected != NavTab.EPISODES && selected != NavTab.CALENDAR && selected != NavTab.LIVE
        }
    }
    val newEpisodeCount = (episodeState as? EpisodeCenterUiState.Content)?.newEpisodeCount ?: 0
    val onNavFocusChange: (Boolean) -> Unit = { focused ->
        if (navFocused && !focused) revealTopNav = false
        navFocused = focused
    }

    val saveable = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        val openCategory = category
        if (openCategory != null) {
            CategoryScreen(
                category = openCategory,
                filter = filter,
                onItemClick = onOpenItem,
                onBack = { category = null },
            )
        } else if (!isBrowseTab) {
            Column(Modifier.fillMaxSize()) {
                TopNavBar(
                    selected = tab,
                    onSelect = onSelectTab,
                    onSettings = onOpenSettings,
                    selectedRequester = navRequester,
                    contentRequester = contentRequester,
                    focusable = navFocusable,
                    showSettings = !kidsProfile,
                    solidBackground = true,
                    newEpisodeCount = newEpisodeCount,
                    tabs = tabs,
                    profileInitial = profileInitial,
                    profileAvatar = profileAvatar,
                    onProfile = onSwitchProfile,
                    onFocusChange = onNavFocusChange,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onBackKey(enabled = true) { focusNav() }
                        .focusRequester(contentRequester)
                        .focusGroup(),
                ) {
                    when (tab) {
                        NavTab.CALENDAR -> CalendarScreen(
                            onOpenEpisode = { onOpenNewEpisode(it.show, it.episode) },
                            onOpenRelease = { onOpenItem(it.item) },
                        )
                        NavTab.SEARCH -> SearchScreen(onItemClick = onOpenItem)
                        NavTab.EPISODES -> EpisodeCenterScreen(onOpenEpisode = onOpenNewEpisode, viewModel = episodeCenter)
                        NavTab.LIVE -> LiveTvScreen(
                            onPlayLive = onPlayLive,
                            consumeLastLiveChannelKey = consumeLastLiveChannelKey,
                        )
                        else -> saveable.SaveableStateProvider("tab_${tab.name}") { MyListScreen(onItemClick = onOpenItem) }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onBackKey(enabled = true) { if (showTopNav) focusNav() else revealTopNav = true }
                        .focusRequester(contentRequester)
                        .focusGroup(),
                ) {
                    saveable.SaveableStateProvider("tab_${tab.name}") {
                        HomeScreen(
                            filter = filter,
                            onItemClick = onOpenItem,
                            onContinueClick = onContinue,
                            onNewEpisodeClick = onOpenNewEpisode,
                            onUpNextClick = onUpNext,
                            onOpenCategory = { category = it },
                            onContentReady = episodeCenter::activate,
                            onInteractive = { homeInteractive = true },
                            navRequester = navRequester,
                            focusNav = focusNav,
                            preferNavFocus = focusMenuAfterSwitch,
                            onNavFocusConsumed = { focusMenuAfterSwitch = false },
                            onTopNavVisibleChange = { homeTopNavVisible = it },
                        )
                    }
                }
                if (showTopNav) {
                    TopNavBar(
                        selected = tab,
                        onSelect = onSelectTab,
                        onSettings = onOpenSettings,
                        selectedRequester = navRequester,
                        contentRequester = contentRequester,
                        focusable = navFocusable,
                        showSettings = !kidsProfile,
                        solidBackground = false,
                        newEpisodeCount = newEpisodeCount,
                        tabs = tabs,
                        profileInitial = profileInitial,
                        profileAvatar = profileAvatar,
                        onProfile = onSwitchProfile,
                        onFocusChange = onNavFocusChange,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        if (showExitConfirmation) {
            ExitConfirmationDialog(
                onDismiss = { showExitConfirmation = false },
                onExit = onExit,
            )
        }
    }
}

internal fun availableNavTabs(kidsProfile: Boolean): List<NavTab> =
    if (kidsProfile) NavTab.entries.filterNot { it == NavTab.CALENDAR || it == NavTab.LIVE } else NavTab.entries

internal fun visibleNavTab(requested: NavTab, kidsProfile: Boolean): NavTab =
    requested.takeIf { it in availableNavTabs(kidsProfile) } ?: NavTab.HOME

internal fun shouldRequestExit(categoryOpen: Boolean, navFocused: Boolean): Boolean = !categoryOpen && navFocused
