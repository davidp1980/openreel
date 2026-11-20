package com.shieldstream.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.LiveChannelEntry
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.AppIcons
import com.shieldstream.tv.ui.component.CardImage
import com.shieldstream.tv.ui.component.LineIcons
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes

@Composable
internal fun LiveActionsBar(
    isAtLiveEdge: Boolean,
    channelCount: Int,
    onOpenChannels: () -> Unit,
    onOpenTracks: () -> Unit,
    onJumpToLive: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocusWithRetry() }

    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveActionChip(
            icon = AppIcons.List,
            label = localizedStringResource(StringsR.string.player_live_channels_action, channelCount),
            onClick = onOpenChannels,
            onFocused = onInteraction,
            modifier = Modifier.focusRequester(initialFocus),
        )
        LiveActionChip(
            icon = AppIcons.Volume,
            label = localizedStringResource(StringsR.string.player_live_tracks_action),
            onClick = onOpenTracks,
            onFocused = onInteraction,
        )
        if (!isAtLiveEdge) {
            LiveActionChip(
                icon = LineIcons.LiveTv,
                label = localizedStringResource(StringsR.string.player_live_jump_to_live),
                onClick = onJumpToLive,
                onFocused = onInteraction,
            )
        }
    }
}

@Composable
private fun LiveActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color.White else Color.White.copy(alpha = 0.14f)
    val fg = if (focused) Color.Black else Color.White

    Row(
        modifier = modifier
            .background(bg, AppShapes.Pill)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
internal fun LiveChannelListOverlay(
    channels: List<LiveChannelEntry>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val activeRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    LaunchedEffect(Unit) { activeRequester.requestFocusWithRetry() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .onBackKey(enabled = true, onBack = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(420.dp)
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .focusGroup(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = localizedStringResource(StringsR.string.player_live_channels_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = channels.size.toString(),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), AppShapes.Pill)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.key }) { index, channel ->
                    LiveChannelDrawerRow(
                        number = index + 1,
                        channel = channel,
                        selected = index == currentIndex,
                        onClick = { onSelect(index) },
                        modifier = if (index == currentIndex) {
                            Modifier.focusRequester(activeRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = localizedStringResource(StringsR.string.player_live_channels_hint),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LiveChannelDrawerRow(
    number: Int,
    channel: LiveChannelEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val primaryColor = if (focused) Color.Black else Color.White
    val secondaryColor = if (focused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.65f)
    val primaryTitle = channel.channelName ?: channel.title
    val programTitle = channel.title.takeIf { channel.channelName != null && it != channel.channelName }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, AppShapes.Control)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = number.toString(),
            color = secondaryColor,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(28.dp),
        )
        channel.logoUrl?.let { logo ->
            Box(
                modifier = Modifier
                    .background(AppColors.PillScrim, AppShapes.Badge)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                CardImage(
                    url = logo,
                    contentDescription = null,
                    width = 40.dp,
                    height = 22.dp,
                    fit = true,
                    modifier = Modifier.size(40.dp, 22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = primaryTitle,
                color = primaryColor,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!programTitle.isNullOrBlank()) {
                Text(
                    text = programTitle,
                    color = secondaryColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(AppColors.Alert, CircleShape),
            )
        }
    }
}
