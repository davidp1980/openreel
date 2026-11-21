package com.shieldstream.tv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.labelResource
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.AppShapes

@OptIn(UnstableApi::class)
@Composable
internal fun LiveSubtitleEditor(
    player: Player,
    appearance: SubtitleAppearance,
    onChange: (SubtitleAppearance) -> Unit,
) {
    DisposableEffect(player) {
        val wasPlaying = player.isPlaying
        if (wasPlaying) player.pause()
        onDispose { if (wasPlaying) player.play() }
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
    val sample = localizedStringResource(StringsR.string.subtitle_live_sample)
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> SubtitleView(ctx) },
            update = { view ->
                view.applySubtitleAppearance(appearance.copy(applyEmbeddedStyles = false))
                view.setCues(listOf(Cue.Builder().setText(sample).build()))
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.85f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(start = 40.dp, end = 32.dp, top = 40.dp),
        ) {
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 14.dp),
            )
            Text(
                text = localizedStringResource(StringsR.string.subtitle_live_hint),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 12.dp),
            )
            val a = appearance
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_size),
                value = localizedStringResource(StringsR.string.subtitle_size_value, a.sizePercent),
                modifier = Modifier.focusRequester(initialFocus),
                onShift = { d ->
                    onChange(
                        a.copy(
                            sizePercent = (a.sizePercent + d * SubtitleAppearance.SIZE_STEP)
                                .coerceIn(SubtitleAppearance.MIN_SIZE, SubtitleAppearance.MAX_SIZE),
                        ),
                    )
                },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_position),
                value = localizedStringResource(StringsR.string.subtitle_position_value, a.verticalPositionPercent),
                onShift = { d ->
                    onChange(
                        a.copy(
                            verticalPositionPercent = (a.verticalPositionPercent + d * SubtitleAppearance.POSITION_STEP)
                                .coerceIn(SubtitleAppearance.MIN_POSITION, SubtitleAppearance.MAX_POSITION),
                        ),
                    )
                },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_text_color),
                value = localizedStringResource(a.textColor.labelResource),
                onShift = { d -> onChange(a.copy(textColor = cycleOption(a.textColor, SubtitleAppearance.textColors, d))) },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_background_color),
                value = localizedStringResource(a.backgroundColor.labelResource),
                onShift = { d -> onChange(a.copy(backgroundColor = cycleOption(a.backgroundColor, SubtitleAppearance.backgroundColors, d))) },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_outline_color),
                value = localizedStringResource(a.outlineColor.labelResource),
                onShift = { d -> onChange(a.copy(outlineColor = cycleOption(a.outlineColor, SubtitleAppearance.outlineColors, d))) },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_embedded_style),
                value = localizedStringResource(
                    if (a.applyEmbeddedStyles) StringsR.string.subtitle_style_original else StringsR.string.subtitle_style_custom,
                ),
                onShift = { onChange(a.copy(applyEmbeddedStyles = !a.applyEmbeddedStyles)) },
            )
            EditorControlRow(
                title = localizedStringResource(StringsR.string.subtitle_reset),
                value = "",
                onShift = { onChange(SubtitleAppearance()) },
            )
        }
    }
}

private fun <T> cycleOption(current: T, values: List<T>, delta: Int): T {
    val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
    return values[(index + delta + values.size) % values.size]
}

@Composable
private fun EditorControlRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onShift: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onShift(-1); true }
                    Key.DirectionRight -> { onShift(1); true }
                    else -> false
                }
            }
            .clip(AppShapes.Control)
            .background(if (focused) Color.White else Color.Transparent)
            .tvClickable(onClick = { onShift(1) })
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = foreground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(text = "‹", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                color = foreground,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(text = "›", color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.titleMedium)
        }
    }
}
