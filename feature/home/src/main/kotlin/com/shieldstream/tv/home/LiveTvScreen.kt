package com.shieldstream.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.LiveChannel
import com.shieldstream.tv.model.LiveChannelEntry
import com.shieldstream.tv.model.LiveTvSection
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.CardImage
import com.shieldstream.tv.ui.component.StatusPill
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.requestFocusWithRetry
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.AppShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun LiveTvScreen(
    onPlayLive: (List<LiveChannelEntry>, Int) -> Unit,
    consumeLastLiveChannelKey: () -> String? = { null },
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val m3uEditor by viewModel.m3uEditor.collectAsStateWithLifecycle()
    val customM3uUrl by viewModel.customM3uUrl.collectAsStateWithLifecycle()
    val cardRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var lastCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRestoreTick by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                consumeLastLiveChannelKey()?.let { key ->
                    lastCardKey = key
                    focusRestoreTick++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.activate()
        var ticks = 0
        while (true) {
            delay(PROGRESS_TICK_MS)
            nowMillis = System.currentTimeMillis()
            if (++ticks % REFRESH_TICKS == 0) {
                viewModel.refreshSilent()
            }
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_DURATION_MS)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(state is LiveTvUiState.Content, focusRestoreTick) {
        val content = state as? LiveTvUiState.Content ?: return@LaunchedEffect
        val key = lastCardKey ?: return@LaunchedEffect
        val activeId = selectedSectionId?.takeIf { id -> content.sections.any { it.id == id } }
        val filtered = if (activeId == null) {
            content.sections
        } else {
            content.sections.filter { it.id == activeId }
        }
        val effectiveSections = if (activeId != null && rowItemIndexForKey(filtered, key) == null) {
            selectedSectionId = null
            content.sections
        } else {
            filtered
        }
        rowItemIndexForKey(effectiveSections, key)?.let { itemIndex ->
            listState.scrollToItem(itemIndex)
        }
        cardRequester.requestFocusWithRetry()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 36.dp, top = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = localizedStringResource(StringsR.string.live_tv_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = localizedStringResource(
                        if (customM3uUrl.isNullOrBlank()) {
                            StringsR.string.live_tv_m3u_import
                        } else {
                            StringsR.string.live_tv_m3u_edit
                        },
                    ),
                    onClick = viewModel::openM3uEditor,
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.live_tv_refresh),
                    onClick = viewModel::refresh,
                )
            }
            Spacer(Modifier.height(16.dp))

            when (val current = state) {
                LiveTvUiState.Loading -> Text(
                    text = localizedStringResource(StringsR.string.live_tv_loading),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is LiveTvUiState.Error -> Column {
                    Text(
                        text = current.message,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    TvButton(
                        text = localizedStringResource(StringsR.string.home_retry),
                        onClick = viewModel::refresh,
                    )
                }
                is LiveTvUiState.Content -> {
                    if (current.sections.isEmpty()) {
                        Text(
                            text = localizedStringResource(StringsR.string.live_tv_empty),
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        val activeSectionId = selectedSectionId?.takeIf { id ->
                            current.sections.any { it.id == id }
                        }
                        val visibleSections = remember(current.sections, activeSectionId) {
                            if (activeSectionId == null) {
                                current.sections
                            } else {
                                current.sections.filter { it.id == activeSectionId }
                            }
                        }
                        if (current.sections.size > 1) {
                            val defaultSectionTitle = localizedStringResource(StringsR.string.live_tv_section_now)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 16.dp),
                            ) {
                                item(key = "filter_all") {
                                    LiveSectionChip(
                                        label = localizedStringResource(StringsR.string.live_tv_filter_all),
                                        selected = activeSectionId == null,
                                        onClick = {
                                            selectedSectionId = null
                                            coroutineScope.launch { listState.scrollToItem(0) }
                                        },
                                    )
                                }
                                items(current.sections, key = { "filter_${it.id}" }) { section ->
                                    LiveSectionChip(
                                        label = section.title.ifBlank { defaultSectionTitle },
                                        selected = activeSectionId == section.id,
                                        onClick = {
                                            selectedSectionId = section.id
                                            coroutineScope.launch { listState.scrollToItem(0) }
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 48.dp),
                        ) {
                            visibleSections.forEachIndexed { sectionIndex, section ->
                                item(key = "header_${section.id}") {
                                    if (sectionIndex > 0) Spacer(Modifier.height(12.dp))
                                    val sectionTitle = section.title.ifBlank {
                                        localizedStringResource(StringsR.string.live_tv_section_now)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = sectionTitle,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Text(
                                            text = section.channels.size.toString(),
                                            color = Color.White.copy(alpha = 0.65f),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.1f), AppShapes.Pill)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                                val rows = section.channels.chunked(GRID_COLUMNS)
                                itemsIndexed(
                                    items = rows,
                                    key = { index, row -> "${section.id}_row_${row.firstOrNull()?.key ?: index}" },
                                ) { _, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        row.forEach { channel ->
                                            LiveChannelCard(
                                                channel = channel,
                                                nowMillis = nowMillis,
                                                onClick = {
                                                    lastCardKey = channel.key
                                                    viewModel.buildPlaybackLineup(channel, visibleSections)?.let { lineup ->
                                                        onPlayLive(lineup.channels, lineup.index)
                                                    }
                                                },
                                                onFocused = { lastCardKey = channel.key },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .then(
                                                        if (channel.key == lastCardKey) {
                                                            Modifier.focusRequester(cardRequester)
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                            )
                                        }
                                        repeat(GRID_COLUMNS - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        notice?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(AppColors.PillScrim, AppShapes.Pill)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        m3uEditor?.let { editor ->
            M3uPlaylistDialog(
                editor = editor,
                onSave = viewModel::saveM3uUrl,
                onRemove = viewModel::removeM3uUrl,
                onClose = viewModel::closeM3uEditor,
            )
        }
    }
}

@Composable
private fun M3uPlaylistDialog(
    editor: M3uEditorState,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    var input by rememberSaveable(editor.initialUrl) { mutableStateOf(editor.initialUrl) }
    val fieldRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldRequester.requestFocus() }
    }
    LaunchedEffect(editor.busy, editor.error) {
        if (!editor.busy && editor.error != null) {
            withFrameNanos { }
            runCatching { saveRequester.requestFocus() }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .onBackKey(enabled = true) { onClose() },
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .background(AppColors.Surface, AppShapes.Panel)
                .border(1.dp, Color.White.copy(alpha = 0.12f), AppShapes.Panel)
                .padding(36.dp),
        ) {
            Text(
                text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(24.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                enabled = !editor.busy,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSave(input)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                if (event.type == KeyEventType.KeyUp) keyboard?.show()
                                true
                            }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown) runCatching { saveRequester.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Field, AppShapes.Control)
                            .border(
                                width = if (fieldFocused) 2.dp else 1.dp,
                                color = if (fieldFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = AppShapes.Control,
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_placeholder),
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.35f),
                            )
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    editor.busy -> localizedStringResource(StringsR.string.live_tv_m3u_dialog_checking)
                    editor.error != null -> editor.error
                    else -> localizedStringResource(StringsR.string.live_tv_m3u_dialog_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    editor.error != null -> AppColors.Error
                    else -> Color.White.copy(alpha = 0.6f)
                },
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_save),
                    onClick = { onSave(input) },
                    enabled = !editor.busy,
                    modifier = Modifier.focusRequester(saveRequester),
                )
                if (editor.hasSavedUrl) {
                    TvButton(
                        text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_remove),
                        onClick = onRemove,
                        enabled = !editor.busy,
                    )
                }
                TvButton(
                    text = localizedStringResource(StringsR.string.live_tv_m3u_dialog_cancel),
                    onClick = onClose,
                    enabled = !editor.busy,
                )
            }
        }
    }
}

@Composable
private fun LiveSectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                AppShapes.Pill,
            )
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveChannelCard(
    channel: LiveChannel,
    nowMillis: Long,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.04f else 1f
    val isUpcoming = channel.isUpcomingAt(nowMillis)
    val startLabel = remember(channel.startsAtMillis) {
        channel.startsAtMillis?.let { SimpleDateFormat("HH:mm", Lang.locale).format(Date(it)) }
    }
    val progress = if (isUpcoming) null else channel.progressAt(nowMillis)

    Column(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(AppShapes.Control)
                .background(AppColors.Placeholder)
                .then(
                    if (focused) {
                        Modifier.border(2.dp, Color.White.copy(alpha = 0.88f), AppShapes.Control)
                    } else {
                        Modifier
                    },
                ),
        ) {
            val imageUrl = channel.imageUrl
            val logoUrl = channel.logoUrl
            if (imageUrl != null) {
                CardImage(
                    url = imageUrl,
                    contentDescription = channel.title,
                    width = CARD_DECODE_WIDTH,
                    height = CARD_DECODE_HEIGHT,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (logoUrl != null) {
                CardImage(
                    url = logoUrl,
                    contentDescription = channel.title,
                    width = CENTER_LOGO_WIDTH,
                    height = CENTER_LOGO_HEIGHT,
                    fit = true,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(CENTER_LOGO_WIDTH, CENTER_LOGO_HEIGHT),
                )
            } else {
                Text(
                    text = channel.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }

            if (isUpcoming && startLabel != null) {
                StatusPill(
                    text = startLabel,
                    dot = false,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            } else {
                StatusPill(
                    text = localizedStringResource(StringsR.string.live_tv_badge_live),
                    dot = true,
                    dotColor = AppColors.Alert,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }

            if (imageUrl != null && logoUrl != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(AppColors.PillScrim, AppShapes.Badge)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    CardImage(
                        url = logoUrl,
                        contentDescription = null,
                        width = LOGO_WIDTH,
                        height = LOGO_HEIGHT,
                        fit = true,
                        modifier = Modifier.size(LOGO_WIDTH, LOGO_HEIGHT),
                    )
                }
            }

            if (progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(AppColors.Highlight),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = if (isUpcoming && startLabel != null) {
            localizedStringResource(StringsR.string.live_tv_starts_at, startLabel)
        } else {
            channel.category
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun rowItemIndexForKey(sections: List<LiveTvSection>, key: String): Int? {
    var itemIndex = 0
    for (section in sections) {
        itemIndex++ // section header
        val rows = section.channels.chunked(GRID_COLUMNS)
        for (row in rows) {
            if (row.any { it.key == key }) return itemIndex
            itemIndex++
        }
    }
    return null
}

private const val GRID_COLUMNS = 3
private const val PROGRESS_TICK_MS = 30_000L
private const val REFRESH_TICKS = 2
private const val NOTICE_DURATION_MS = 3_500L
private val CARD_DECODE_WIDTH = 265.dp
private val CARD_DECODE_HEIGHT = 149.dp
private val LOGO_WIDTH = 48.dp
private val LOGO_HEIGHT = 22.dp
private val CENTER_LOGO_WIDTH = 96.dp
private val CENTER_LOGO_HEIGHT = 64.dp
