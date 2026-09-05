package com.arflix.tv.ui.screens.tv.live

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.R
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.ui.focus.mirrorHorizontalForRtl

/**
 * Channel column row — spec §3.4, mockup layout:
 *
 *   ┌─ [number mono] ─ [logo 44] ─ [name / program / progress / time] ─ [HD/HI] ─┐
 *
 * Active channel: 3dp cyan left indicator, accent bg tint, CH number cyan.
 * Focused: full row sits on PanelRaised so the selection is obvious.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    channel: EnrichedChannel,
    clockTickMillis: Long,
    nowNext: IptvNowNext?,
    isActive: Boolean,
    isFavorite: Boolean,
    stripe: Boolean = false,
    onClick: () -> Unit,
    /**
     * Long-press / MENU: opens the channel menu (favourite, reorder, variants).
     * [fromKeyHold] is true when this came from a held OK on the D-pad, which keeps
     * auto-repeating afterwards and so needs the rest of the press suppressed.
     */
    onLongPress: (fromKeyHold: Boolean) -> Unit = {},
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Boolean = { false },
    onMoveUp: () -> Boolean = { false },
    onMoveDown: () -> Boolean = { false },
    onFocused: () -> Unit = {},
    variantCount: Int = 1,
    rowHeight: androidx.compose.ui.unit.Dp = LiveDims.EpgRowHeight,
    forceFocused: Boolean = false,
    displayQuality: Quality = channel.quality,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val visuallyFocused = focused || forceFocused
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Latches for the duration of one long press so the trailing repeats and the KeyUp
    // can be swallowed before combinedClickable turns them into a click.
    var longPressConsumed by remember { mutableStateOf(false) }
    val bg = when {
        visuallyFocused -> LiveColors.PanelRaised
        isActive -> LiveColors.FocusBg
        stripe -> LiveColors.RowStripe
        else -> Color.Transparent
    }
    val now = nowNext?.now
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (visuallyFocused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 70),
        label = "channel-row-border",
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (visuallyFocused) 1.004f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "channel-row-scale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            .drawWithContent {
                drawContent()
                // Read animation state in drawing, not composition: channel
                // text and logo layout should not rebuild for each border frame.
                val stroke = animatedBorderWidth.toPx()
                if (visuallyFocused && stroke > 0f) {
                    drawRect(
                        color = LiveColors.FocusRing,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                        style = Stroke(stroke),
                    )
                }
            }
            .background(if (visuallyFocused) LiveColors.PanelRaised else bg)
            .focusable()
            // Long-press / MENU opens the channel menu. This has to live in the PREVIEW
            // phase, ahead of combinedClickable: combinedClickable arms a click on the
            // initial KeyDown and completes it on KeyUp, so handling the long press in a
            // bubble-phase onKeyEvent (as this used to) left the KeyUp untouched and the
            // channel opened *as well as* the long-press action firing. Swallowing the
            // whole press — repeats and release — is what keeps the two apart.
            .onPreviewKeyEvent { ev ->
                // OK/Enter is owned entirely here rather than shared with
                // combinedClickable. combinedClickable arms on KeyDown and fires on KeyUp
                // with its own long-press timer, so splitting the gesture across the two
                // raced: the hold opened the menu *and* the release still counted as a
                // click, tuning the channel. Every select key is consumed below, and the
                // short-press click is dispatched explicitly on release. combinedClickable
                // keeps handling pointer input (mouse / touch long-press), which is a
                // separate path and unaffected.
                val isSelectKey = ev.key == Key.DirectionCenter || ev.key == Key.Enter
                if (isSelectKey) {
                    if (ev.type == KeyEventType.KeyDown) {
                        if (ev.nativeKeyEvent.repeatCount == 0) {
                            // Start of a fresh press. Clear the latch here rather than on
                            // release: once the long press opens the menu, the menu's own
                            // handler swallows the release, so a latch cleared only on
                            // KeyUp would stay set and silently eat the next short press.
                            longPressConsumed = false
                        } else if (!longPressConsumed) {
                            // repeatCount >= 1 is the platform auto-repeat a held OK
                            // produces on a real remote — that is the "long press".
                            longPressConsumed = true
                            onLongPress(true)
                        }
                    } else if (ev.type == KeyEventType.KeyUp && !longPressConsumed) {
                        onClick()
                    }
                    return@onPreviewKeyEvent true
                }
                if (ev.key == Key.Menu) {
                    if (ev.type == KeyEventType.KeyDown) onLongPress(true)
                    return@onPreviewKeyEvent true
                }
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key.mirrorHorizontalForRtl(isRtl)) {
                        Key.DirectionLeft -> { onMoveLeft(); return@onPreviewKeyEvent true }
                        Key.DirectionRight -> if (onMoveRight()) return@onPreviewKeyEvent true
                        Key.DirectionUp -> if (onMoveUp()) return@onPreviewKeyEvent true
                        Key.DirectionDown -> if (onMoveDown()) return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .combinedClickable(
                onClick = onClick,
                // Touch devices never reach the key handler above.
                onLongClick = { onLongPress(false) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ─ active left indicator ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(LiveDims.ActiveIndicator)
                .background(if (isActive) LiveColors.Accent else Color.Transparent),
        )

        // ─ channel number ────────────────────────────────────
        Box(
            modifier = Modifier
                .width(48.dp)
                .padding(start = 10.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = channel.number.toString(),
                style = LiveType.NumberMono.copy(
                    color = if (isActive) LiveColors.Accent else LiveColors.FgMute,
                ),
            )
        }

        // ─ logo ──────────────────────────────────────────────
        ChannelLogo(channel = channel, size = 36.dp)

        Spacer(Modifier.width(10.dp))

        // ─ name / program / progress / time ──────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = LiveType.CellTitle.copy(
                        color = if (isActive) LiveColors.Accent else LiveColors.Fg,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFavorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC04A), // Golden star
                        modifier = Modifier.size(11.dp),
                    )
                }
                if (channel.catchupDays > 0) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(R.string.live_cd_catchup_available),
                        tint = LiveColors.Accent.copy(alpha = 0.8f),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            // Only the thin progress underline stays here — programme info
            // itself is shown exclusively in the time-aligned grid cells to
            // the right, not smeared across the channel name column.
            val progress = remember(now, clockTickMillis) { progressOf(now) }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(80.dp).height(2.dp),
                    color = LiveColors.Accent,
                    trackColor = LiveColors.Divider,
                )
            }
        }

        // ─ stacked badges (quality + lang) ───────────────────
        Column(
            modifier = Modifier.padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (displayQuality != Quality.UNKNOWN) {
                SmallPillBadge(if (variantCount > 1) stringResource(R.string.live_label_quality_variants, displayQuality.label, variantCount) else displayQuality.label)
            } else if (variantCount > 1) {
                SmallPillBadge(stringResource(R.string.live_label_sources, variantCount))
            }
            SmallPillBadge(channel.lang)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SmallPillBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(LiveColors.Panel)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text.uppercase(), style = LiveType.Badge.copy(color = LiveColors.FgDim))
    }
}
