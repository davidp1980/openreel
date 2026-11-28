package com.shieldstream.tv.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

/** Height occupied by the top navigation bar. */
val TopNavHeight: Dp = 64.dp

enum class NavTab(val icon: ImageVector) {
    SEARCH(LineIcons.Search),
    HOME(LineIcons.Home),
    MOVIES(LineIcons.Movie),
    TV(LineIcons.Tv),
    DOCUMENTARIES(LineIcons.Documentary),
    LIVE(LineIcons.LiveTv),
    EPISODES(LineIcons.Episodes),
    CALENDAR(LineIcons.Calendar),
    MY_LIST(LineIcons.Bookmark),
}

@get:StringRes
private val NavTab.labelResource: Int
    get() = when (this) {
        NavTab.SEARCH -> StringsR.string.common_search
        NavTab.HOME -> StringsR.string.common_home
        NavTab.MOVIES -> StringsR.string.common_movies
        NavTab.TV -> StringsR.string.common_tv_shows
        NavTab.DOCUMENTARIES -> StringsR.string.common_documentaries
        NavTab.LIVE -> StringsR.string.common_live_tv
        NavTab.EPISODES -> StringsR.string.common_episodes
        NavTab.CALENDAR -> StringsR.string.calendar_title
        NavTab.MY_LIST -> StringsR.string.common_my_list
    }

/**
 * Horizontal top navigation bar for Android TV.
 *
 * Left: profile switcher. Center: primary destination text pills (all tabs except [NavTab.SEARCH]).
 * Right: Search and Settings icon buttons. Entering from below with Up focuses the currently
 * selected tab; pressing Down returns focus to [contentRequester].
 */
@Composable
fun TopNavBar(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    onSettings: () -> Unit,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
    contentRequester: FocusRequester = FocusRequester.Default,
    focusable: Boolean = true,
    showSettings: Boolean = true,
    solidBackground: Boolean = true,
    profileInitial: String? = null,
    profileAvatar: Int = 0,
    onProfile: () -> Unit = {},
    newEpisodeCount: Int = 0,
    tabs: List<NavTab> = NavTab.entries,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val centerTabs = remember(tabs) { tabs.filterNot { it == NavTab.SEARCH } }
    val showSearch = NavTab.SEARCH in tabs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopNavHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(if (solidBackground) AppColors.Background else Color.Transparent)
            .padding(horizontal = 48.dp, vertical = 10.dp)
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up) {
                        selectedRequester.requestFocus()
                    }
                }
            }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profileInitial != null) {
            TopNavProfileButton(
                profileInitial = profileInitial,
                profileAvatar = profileAvatar,
                onClick = onProfile,
                contentRequester = contentRequester,
                focusable = focusable,
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (solidBackground) {
                    Modifier
                } else {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.42f), AppShapes.Pill)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                },
            ) {
                centerTabs.forEach { tab ->
                    val label = localizedStringResource(tab.labelResource)
                    TopNavTabPill(
                        label = label,
                        selected = tab == selected,
                        badgeCount = if (tab == NavTab.EPISODES) newEpisodeCount else 0,
                        onClick = {
                            onSelect(tab)
                            runCatching { contentRequester.requestFocus() }
                        },
                        onFocused = { onSelect(tab) },
                        contentRequester = contentRequester,
                        focusable = focusable,
                        modifier = if (tab == selected) Modifier.focusRequester(selectedRequester) else Modifier,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSearch) {
                TopNavIconButton(
                    icon = LineIcons.Search,
                    contentDescription = localizedStringResource(StringsR.string.common_search),
                    selected = selected == NavTab.SEARCH,
                    solidBackground = solidBackground,
                    onClick = {
                        onSelect(NavTab.SEARCH)
                        runCatching { contentRequester.requestFocus() }
                    },
                    onFocused = { onSelect(NavTab.SEARCH) },
                    contentRequester = contentRequester,
                    focusable = focusable,
                    modifier = if (selected == NavTab.SEARCH) Modifier.focusRequester(selectedRequester) else Modifier,
                )
            }
            if (showSettings) {
                TopNavIconButton(
                    icon = LineIcons.Settings,
                    contentDescription = localizedStringResource(StringsR.string.common_settings),
                    selected = false,
                    solidBackground = solidBackground,
                    onClick = onSettings,
                    contentRequester = contentRequester,
                    focusable = focusable,
                )
            }
        }
    }
}

@Composable
private fun TopNavProfileButton(
    profileInitial: String,
    profileAvatar: Int,
    onClick: () -> Unit,
    contentRequester: FocusRequester,
    focusable: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .background(
                if (focused) Color.White else Color.Transparent,
                CircleShape,
            )
            .focusProperties {
                canFocus = focusable
                down = contentRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        ProfileAvatar(
            initial = profileInitial,
            avatar = profileAvatar,
            size = 28.dp,
            shape = CircleShape,
        )
    }
}

@Composable
private fun TopNavTabPill(
    label: String,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    contentRequester: FocusRequester,
    focusable: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val tint = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val showIndicator = selected && !focused
    Row(
        modifier = modifier
            .height(38.dp)
            .background(background, AppShapes.Pill)
            .drawBehind {
                if (showIndicator) {
                    val width = IndicatorWidth.toPx()
                    val height = IndicatorHeight.toPx()
                    drawRoundRect(
                        color = AppColors.Highlight,
                        topLeft = Offset((size.width - width) / 2f, size.height - height - 2.dp.toPx()),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2f),
                    )
                }
            }
            .focusProperties {
                canFocus = focusable
                down = contentRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
            softWrap = false,
        )
        if (badgeCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                color = if (focused) Color.White else Color.Black,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .background(if (focused) Color.Black else AppColors.Highlight, AppShapes.Pill)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TopNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    solidBackground: Boolean,
    onClick: () -> Unit,
    contentRequester: FocusRequester,
    focusable: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.16f)
        solidBackground -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.42f)
    }
    val tint = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.8f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .background(background, CircleShape)
            .focusProperties {
                canFocus = focusable
                down = contentRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val IndicatorWidth = 18.dp
private val IndicatorHeight = 2.5.dp
