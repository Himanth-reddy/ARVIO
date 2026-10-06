package com.arflix.tv.ui.components

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import kotlinx.coroutines.delay

/** Documented YouTube autoplay constraints, in CSS pixels (Android dp). */
internal fun canAutoplayHomeTrailer(width: Float, height: Float, visibleArea: Float): Boolean =
    width >= 200f && height >= 200f && visibleArea > width * height * 0.5f

/**
 * One unobscured official embed in the active Home preview slot. Moving focus
 * removes the slot; backgrounding/scrolling it off screen releases the WebView.
 * Artwork is a replacement, never a layer drawn in front of the player.
 */
@Composable
internal fun HomeTrailerPreview(
    youtubeKey: String?,
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    volume: Float = 0f,
    enabled: Boolean = true,
    onStateChange: (PlayerConstants.PlayerState) -> Unit = {},
    fallback: @Composable () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val density = LocalDensity.current.density
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var visible by remember { mutableStateOf(false) }
    var settled by remember(youtubeKey, enabled) { mutableStateOf(false) }
    var finished by remember(youtubeKey) { mutableStateOf(false) }
    var started by remember(youtubeKey) { mutableStateOf(false) }
    val latestStateCallback by rememberUpdatedState(onStateChange)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(youtubeKey, enabled, foreground, visible) {
        settled = false
        if (!youtubeKey.isNullOrBlank() && enabled && foreground && visible) {
            delay(delayMs.coerceAtLeast(0L))
            settled = true
        }
    }
    val showPlayer = settled && enabled && foreground && visible && !finished && !youtubeKey.isNullOrBlank()
    LaunchedEffect(showPlayer, youtubeKey) {
        if (showPlayer) {
            started = false
            // An unavailable/blocked embed must not leave an empty preview forever.
            delay(20_000L)
            if (!started) finished = true
        }
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val window = Rect().also { view.getWindowVisibleDisplayFrame(it) }
        val intersectionWidth = (minOf(bounds.right, window.right.toFloat()) - maxOf(bounds.left, window.left.toFloat())).coerceAtLeast(0f)
        val intersectionHeight = (minOf(bounds.bottom, window.bottom.toFloat()) - maxOf(bounds.top, window.top.toFloat())).coerceAtLeast(0f)
        visible = canAutoplayHomeTrailer(
            coordinates.size.width / density, coordinates.size.height / density,
            intersectionWidth * intersectionHeight / (density * density)
        )
    }) {
        if (showPlayer) {
            key(youtubeKey) {
                TrailerPlayerSurface(
                    youtubeKey = youtubeKey!!,
                    modifier = Modifier.fillMaxSize(),
                    showControls = false,
                    onReady = { player ->
                        if (volume <= 0f) player.mute() else {
                            player.unMute()
                            player.setVolume((volume * 100).toInt().coerceIn(0, 100))
                        }
                        true
                    },
                    onStateChange = { state ->
                        if (state == PlayerConstants.PlayerState.PLAYING) started = true
                        if (state == PlayerConstants.PlayerState.ENDED) finished = true
                        latestStateCallback(state)
                    },
                    onError = { finished = true }
                )
            }
        } else {
            fallback()
        }
    }
}
