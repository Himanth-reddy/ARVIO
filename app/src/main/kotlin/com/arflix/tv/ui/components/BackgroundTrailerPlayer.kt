package com.arflix.tv.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

private fun findWebView(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findWebView(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

private fun disableCaptionsAndOverlays(view: View) {
    findWebView(view)?.let { webView ->
        val js = """
            (function() {
                try {
                    if (typeof player !== 'undefined') {
                        if (typeof player.unloadModule === 'function') {
                            player.unloadModule('captions');
                            player.unloadModule('cc');
                        }
                        if (typeof player.setOption === 'function') {
                            player.setOption('captions', 'track', {});
                            player.setOption('cc', 'track', {});
                        }
                    }
                    var css = '.ytp-chrome-top, .ytp-chrome-bottom, .ytp-watermark, .ytp-youtube-button, .ytp-pause-overlay, .ytp-ce-element, .ytp-subtitles-player-content, .caption-window, .ytp-caption-window-bottom, .ytp-spinner { display: none !important; opacity: 0 !important; visibility: hidden !important; }';
                    var head = document.head || document.getElementsByTagName('head')[0];
                    if (head) {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(css));
                        head.appendChild(style);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}

/**
 * Clean background YouTube trailer player for the Home screen hero backdrop.
 *
 * Key features for TV:
 * - Completely unfocusable (FOCUS_BLOCK_DESCENDANTS) so TV remote D-pad navigation never gets trapped.
 * - controls(0): pure video playback with NO time bar, play/pause buttons, or web overlays.
 * - Delayed start: waits [delayMs] so fast scrolling past items does not trigger unnecessary video loads.
 * - Smooth fade-in: stays transparent while buffering and fades in once PLAYING, preventing black flashes over backdrop art.
 * - Lifecycle-aware: pauses and releases properly.
 */
@Composable
fun BackgroundTrailerPlayer(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    delayMs: Long = 2000L,
    soundEnabled: Boolean = false,
    onPlayingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var shouldLoad by remember(youtubeKey) { mutableStateOf(false) }
    var isPlaying by remember(youtubeKey) { mutableStateOf(false) }
    var activePlayer by remember(youtubeKey) { mutableStateOf<YouTubePlayer?>(null) }

    // Settle delay before initiating playback
    LaunchedEffect(youtubeKey) {
        shouldLoad = false
        isPlaying = false
        if (delayMs > 0L) {
            delay(delayMs)
        }
        shouldLoad = true
    }

    // Sound control
    LaunchedEffect(soundEnabled, activePlayer) {
        activePlayer?.let { player ->
            if (soundEnabled) {
                player.unMute()
            } else {
                player.mute()
            }
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "trailerFadeIn"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .alpha(animatedAlpha)
    ) {
        if (shouldLoad) {
            AndroidView(
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        enableAutomaticInitialization = false

                        // Completely replace default UI (play/pause overlay, seekbar, etc.) with empty view
                        setCustomPlayerUi(android.view.View(ctx).apply { visibility = android.view.View.GONE })

                        // CRITICAL FOR TV: Block all focus so D-pad cannot focus or get trapped in the WebView
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        isClickable = false
                        setOnTouchListener { _, _ -> true }

                        val iFramePlayerOptions = IFramePlayerOptions.Builder(ctx)
                            .controls(0)      // Hide all time bars and buttons
                            .rel(0)           // Only related videos from same channel
                            .ivLoadPolicy(3)  // Disable video annotations
                            .ccLoadPolicy(0)  // Disable closed captions
                            .fullscreen(0)
                            .build()

                        initialize(
                            object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    activePlayer = youTubePlayer
                                    if (!soundEnabled) {
                                        youTubePlayer.mute()
                                    } else {
                                        youTubePlayer.unMute()
                                    }
                                    disableCaptionsAndOverlays(this@apply)
                                    youTubePlayer.loadVideo(youtubeKey, 0f)
                                }

                                override fun onStateChange(
                                    youTubePlayer: YouTubePlayer,
                                    state: PlayerConstants.PlayerState
                                ) {
                                    when (state) {
                                        PlayerConstants.PlayerState.PLAYING -> {
                                            disableCaptionsAndOverlays(this@apply)
                                            isPlaying = true
                                            onPlayingChanged(true)
                                        }
                                        PlayerConstants.PlayerState.ENDED,
                                        PlayerConstants.PlayerState.PAUSED -> {
                                            if (state == PlayerConstants.PlayerState.ENDED) {
                                                isPlaying = false
                                                onPlayingChanged(false)
                                            }
                                        }
                                        else -> {}
                                    }
                                }

                                override fun onError(
                                    youTubePlayer: YouTubePlayer,
                                    error: PlayerConstants.PlayerError
                                ) {
                                    isPlaying = false
                                    onPlayingChanged(false)
                                }
                            },
                            iFramePlayerOptions
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { playerView ->
                    playerView.release()
                    activePlayer = null
                    isPlaying = false
                    onPlayingChanged(false)
                }
            )
        }
    }

    // Lifecycle observer to pause/resume playback when activity is backgrounded
    DisposableEffect(lifecycleOwner, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> activePlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying) activePlayer?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onPlayingChanged(false)
        }
    }
}
