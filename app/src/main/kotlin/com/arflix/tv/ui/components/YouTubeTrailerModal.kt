package com.arflix.tv.ui.components

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arflix.tv.R
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.util.LocalDeviceType
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
                    var css = '.ytp-chrome-top, .ytp-chrome-bottom, .ytp-watermark, .ytp-youtube-button, .ytp-pause-overlay, .ytp-pause-overlay-container, .ytp-ce-element, .ytp-ce-covering-overlay, .ytp-ce-expanding-overlay, .ytp-subtitles-player-content, .caption-window, .ytp-caption-window-bottom, .ytp-spinner, .ytp-gradient-top, .ytp-gradient-bottom, .ytp-impression-link, .ytp-title, .ytp-title-channel, .ytp-share-button, .ytp-share-panel, .ytp-menuitem, .ytp-contextmenu { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; }';
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

private fun formatTime(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSec / 60
    val remSec = totalSec % 60
    return "%02d:%02d".format(minutes, remSec)
}

private enum class IndicatorType {
    PLAY, PAUSE, SEEK_BACK, SEEK_FORWARD
}

private data class TransientIndicator(
    val type: IndicatorType,
    val text: String,
    val icon: ImageVector,
    val timestamp: Long = SystemClock.uptimeMillis()
)

/**
 * Clean cinema-grade YouTube trailer modal for TV and Mobile.
 *
 * Highlights:
 * - Pure video canvas: strips YouTube's web title bar, share button, central pause overlay, and watermarks.
 * - TV remote controls:
 *   - [DPAD_CENTER] / [ENTER]: Play / Pause toggle
 *   - [DPAD_LEFT] / [MediaRewind]: Seek backward 10s
 *   - [DPAD_RIGHT] / [MediaFastForward]: Seek forward 10s
 *   - [DPAD_UP]: Focus top action buttons (Close ✕ & Watch in YouTube)
 *   - [DPAD_DOWN]: Focus video player canvas
 *   - [BACK]: Dismiss modal cleanly and restore focus to Details
 * - Auto-hiding HUD: fades out after 3.5s of playback, reappears on any remote key press or tap.
 */
@Composable
fun YouTubeTrailerModal(
    youtubeKey: String,
    title: String = "",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMobile = LocalDeviceType.current.isTouchDevice()

    val playerFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val extFocusRequester = remember { FocusRequester() }

    var isPlayerFocused by remember { mutableStateOf(false) }
    var isCloseFocused by remember { mutableStateOf(false) }
    var isExtFocused by remember { mutableStateOf(false) }

    var activePlayer by remember(youtubeKey) { mutableStateOf<YouTubePlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<YouTubePlayerView?>(null) }
    var playbackError by remember(youtubeKey) { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentSecond by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }

    var showHud by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var transientIndicator by remember { mutableStateOf<TransientIndicator?>(null) }

    // Intercept back key on TV and mobile exclusively
    BackHandler(enabled = true) {
        onClose()
    }

    // Auto-focus player container for direct remote interaction on start
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { playerFocusRequester.requestFocus() }
    }

    // Periodic check to strip late-injected YouTube web UI
    LaunchedEffect(activePlayer) {
        if (activePlayer != null) {
            delay(500)
            playerViewRef?.let { disableCaptionsAndOverlays(it) }
            delay(1200)
            playerViewRef?.let { disableCaptionsAndOverlays(it) }
            delay(2500)
            playerViewRef?.let { disableCaptionsAndOverlays(it) }
        }
    }

    // Auto-hide HUD after 3.5s when playing and no buttons are focused
    LaunchedEffect(showHud, isPlaying, lastInteractionTime, isCloseFocused, isExtFocused) {
        if (showHud && isPlaying && !isCloseFocused && !isExtFocused) {
            delay(3500)
            showHud = false
        }
    }

    // Transient indicator auto-clear
    LaunchedEffect(transientIndicator) {
        if (transientIndicator != null) {
            delay(900)
            transientIndicator = null
        }
    }

    fun triggerIndicator(type: IndicatorType, text: String, icon: ImageVector) {
        transientIndicator = TransientIndicator(type, text, icon)
    }

    fun openExternalYouTube() {
        try {
            val url = "https://www.youtube.com/watch?v=$youtubeKey"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            onClose()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.trailer_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .zIndex(100f)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyUp) {
                        onClose()
                    }
                    true
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isMobile) onClose()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isMobile) 0.96f else 0.88f)
                .widthIn(max = 1000.dp)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 16:9 Cinema Video Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(
                        width = if (isPlayerFocused) 2.dp else 1.dp,
                        color = if (isPlayerFocused) ArvioSkin.colors.focusOutline else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .focusRequester(playerFocusRequester)
                    .onFocusChanged { isPlayerFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                                    if (isPlaying) {
                                        activePlayer?.pause()
                                        isPlaying = false
                                        triggerIndicator(IndicatorType.PAUSE, "Paused", Icons.Default.Pause)
                                    } else {
                                        activePlayer?.play()
                                        isPlaying = true
                                        triggerIndicator(IndicatorType.PLAY, "Playing", Icons.Default.PlayArrow)
                                    }
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.MediaPlay -> {
                                    activePlayer?.play()
                                    isPlaying = true
                                    triggerIndicator(IndicatorType.PLAY, "Playing", Icons.Default.PlayArrow)
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.MediaPause -> {
                                    activePlayer?.pause()
                                    isPlaying = false
                                    triggerIndicator(IndicatorType.PAUSE, "Paused", Icons.Default.Pause)
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.DirectionLeft, Key.MediaRewind -> {
                                    val target = (currentSecond - 10f).coerceAtLeast(0f)
                                    activePlayer?.seekTo(target)
                                    currentSecond = target
                                    triggerIndicator(IndicatorType.SEEK_BACK, "-10s", Icons.Default.FastRewind)
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.DirectionRight, Key.MediaFastForward -> {
                                    val target = (currentSecond + 10f).coerceAtMost(duration)
                                    activePlayer?.seekTo(target)
                                    currentSecond = target
                                    triggerIndicator(IndicatorType.SEEK_FORWARD, "+10s", Icons.Default.FastForward)
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.DirectionUp -> {
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    closeFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionDown -> {
                                    showHud = true
                                    lastInteractionTime = SystemClock.uptimeMillis()
                                    true
                                }
                                Key.Back, Key.Escape -> {
                                    onClose()
                                    true
                                }
                                else -> false
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                                Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                                Key.MediaFastForward, Key.MediaRewind,
                                Key.Back, Key.Escape -> true
                                else -> false
                            }
                        } else false
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showHud = !showHud
                        lastInteractionTime = SystemClock.uptimeMillis()
                    }
            ) {
                if (!playbackError) {
                    AndroidView(
                        factory = { ctx ->
                            YouTubePlayerView(ctx).apply {
                                enableAutomaticInitialization = false

                                // Replace default UI (huge play/pause button, etc.) with empty view
                                setCustomPlayerUi(android.view.View(ctx).apply { visibility = android.view.View.GONE })

                                // Block touch and remote focus on WebView so Compose manages it
                                isFocusable = false
                                isFocusableInTouchMode = false
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setOnTouchListener { _, _ -> true }

                                val iFrameOptions = IFramePlayerOptions.Builder(ctx)
                                    .controls(0) // Completely disable YouTube web controls
                                    .rel(0)
                                    .ivLoadPolicy(3)
                                    .fullscreen(0)
                                    .build()

                                initialize(
                                    object : AbstractYouTubePlayerListener() {
                                        override fun onReady(youTubePlayer: YouTubePlayer) {
                                            activePlayer = youTubePlayer
                                            youTubePlayer.unMute()
                                            youTubePlayer.loadVideo(youtubeKey, 0f)
                                            disableCaptionsAndOverlays(this@apply)
                                        }

                                        override fun onStateChange(
                                            youTubePlayer: YouTubePlayer,
                                            state: PlayerConstants.PlayerState
                                        ) {
                                            when (state) {
                                                PlayerConstants.PlayerState.PLAYING -> {
                                                    isPlaying = true
                                                    disableCaptionsAndOverlays(this@apply)
                                                }
                                                PlayerConstants.PlayerState.PAUSED -> {
                                                    isPlaying = false
                                                    showHud = true
                                                }
                                                PlayerConstants.PlayerState.ENDED -> {
                                                    isPlaying = false
                                                    showHud = true
                                                }
                                                else -> {}
                                            }
                                        }

                                        override fun onCurrentSecond(
                                            youTubePlayer: YouTubePlayer,
                                            second: Float
                                        ) {
                                            currentSecond = second
                                        }

                                        override fun onVideoDuration(
                                            youTubePlayer: YouTubePlayer,
                                            durationSec: Float
                                        ) {
                                            duration = durationSec
                                        }

                                        override fun onError(
                                            youTubePlayer: YouTubePlayer,
                                            error: PlayerConstants.PlayerError
                                        ) {
                                            playbackError = true
                                        }
                                    },
                                    iFrameOptions
                                )
                            }
                        },
                        update = { playerView ->
                            playerViewRef = playerView
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { playerView ->
                            playerView.release()
                            activePlayer = null
                            playerViewRef = null
                        }
                    )
                } else {
                    // Fallback UI when video cannot be embedded
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "This trailer cannot be embedded directly by YouTube.",
                            style = ArvioSkin.typography.body.copy(color = Color.White.copy(alpha = 0.8f)),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        var fallbackBtnFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (fallbackBtnFocused) ArvioSkin.colors.focusOutline else Color(0xFFCC0000))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .onFocusChanged { fallbackBtnFocused = it.isFocused }
                                .clickable { openExternalYouTube() }
                        ) {
                            Text(
                                text = "Watch on YouTube",
                                style = ArvioSkin.typography.button.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (fallbackBtnFocused) Color.Black else Color.White
                                )
                            )
                        }
                    }
                }

                // Transient Feedback Badge (Play / Pause / Seek ±10s)
                androidx.compose.animation.AnimatedVisibility(
                    visible = transientIndicator != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    transientIndicator?.let { ind ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = ind.icon,
                                    contentDescription = null,
                                    tint = ArvioSkin.colors.focusOutline,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = ind.text,
                                    style = ArvioSkin.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Native TV & Cinema HUD (Top Header & Bottom Progress Controls)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHud || !isPlaying,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top Header with Title and Action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.85f),
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (title.isNotBlank()) "$title - ${stringResource(R.string.trailer)}" else stringResource(R.string.trailer),
                                style = ArvioSkin.typography.body.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Watch externally in YouTube App
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isExtFocused) ArvioSkin.colors.focusOutline else Color.White.copy(alpha = 0.15f))
                                        .border(
                                            width = if (isExtFocused) 2.dp else 1.dp,
                                            color = if (isExtFocused) ArvioSkin.colors.focusOutline else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .focusRequester(extFocusRequester)
                                        .onFocusChanged { isExtFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionDown -> {
                                                        playerFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        closeFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                        openExternalYouTube()
                                                        true
                                                    }
                                                    Key.Back, Key.Escape -> {
                                                        onClose()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .clickable { openExternalYouTube() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Watch on YouTube",
                                        tint = if (isExtFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close Button (✕)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isCloseFocused) ArvioSkin.colors.focusOutline else Color.White.copy(alpha = 0.18f))
                                        .border(
                                            width = if (isCloseFocused) 2.dp else 1.dp,
                                            color = if (isCloseFocused) ArvioSkin.colors.focusOutline else Color.White.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .focusRequester(closeFocusRequester)
                                        .onFocusChanged { isCloseFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionDown -> {
                                                        playerFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionLeft -> {
                                                        extFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                        onClose()
                                                        true
                                                    }
                                                    Key.Back, Key.Escape -> {
                                                        onClose()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .clickable { onClose() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = if (isCloseFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Scrim & Progress Bar with Time and TV Remote Hints
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Black.copy(alpha = 0.90f)
                                        )
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Progress bar
                            val progress = if (duration > 0f) (currentSecond / duration).coerceIn(0f, 1f) else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = progress)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ArvioSkin.colors.focusOutline)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Time and TV Remote Navigation Hints
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatTime(currentSecond)} / ${formatTime(duration)}",
                                    style = ArvioSkin.typography.caption.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )

                                Text(
                                    text = if (isMobile) "Tap to show/hide controls" else "[OK] Play/Pause   ◄/► Seek 10s   ▲ Menu   Back Exit",
                                    style = ArvioSkin.typography.caption.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> activePlayer?.pause()
                Lifecycle.Event.ON_RESUME -> activePlayer?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activePlayer?.pause()
        }
    }
}
