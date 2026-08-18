package com.arflix.tv.ui.screens.player.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.arflix.tv.ui.screens.player.engine.exoplayer.FullViewportSubtitlePlayerView
import com.arflix.tv.ui.screens.player.engine.exoplayer.ExoPlayerEngine

@Composable
fun PlayerEngineSurface(
    engine: PlayerEngine,
    resizeMode: Int,
    subtitleSizePref: String,
    subtitleColorPref: String,
    subtitleStylePref: String,
    subtitleStylizedPref: Boolean,
    subtitleOffsetPref: String,
    isInPipMode: Boolean,
    modifier: Modifier = Modifier
) {
    when (engine) {
        is ExoPlayerEngine -> {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    FullViewportSubtitlePlayerView(ctx).apply {
                        keepScreenOn = true
                        player = engine.exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        this.resizeMode = resizeMode
                    }
                },
                update = { playerView ->
                    playerView.keepScreenOn = true
                    playerView.resizeMode = resizeMode
                    playerView.subtitleView?.apply {
                        val subSizeSp = when (subtitleSizePref) {
                            "Small" -> 18f; "Large" -> 30f; "Extra Large" -> 36f; else -> 24f
                        }
                        val subFgColor = when (subtitleColorPref) {
                            "Yellow" -> android.graphics.Color.YELLOW
                            "Green" -> android.graphics.Color.GREEN
                            "Cyan" -> android.graphics.Color.CYAN
                            else -> android.graphics.Color.WHITE
                        }
                        val subTypeface = android.graphics.Typeface.DEFAULT_BOLD
                        val subEdgeType = when (subtitleStylePref) {
                            "Normal" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                            else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        }
                        val subBgColor = when (subtitleStylePref) {
                            "Background" -> android.graphics.Color.argb(180, 0, 0, 0)
                            else -> android.graphics.Color.TRANSPARENT
                        }
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                subFgColor,
                                android.graphics.Color.TRANSPARENT,
                                subBgColor,
                                subEdgeType,
                                android.graphics.Color.BLACK,
                                subTypeface
                            )
                        )
                        val pipSubScale = if (isInPipMode) 0.4f else 1f
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subSizeSp * pipSubScale)
                        val bottomPaddingFraction = when (subtitleOffsetPref) {
                            "Bottom" -> 0.02f
                            "Low" -> 0.08f
                            "Medium" -> 0.15f
                            "High" -> 0.25f
                            else -> 0.02f
                        }
                        setBottomPaddingFraction(bottomPaddingFraction)
                        if (subtitleStylizedPref) {
                            setApplyEmbeddedStyles(true)
                            setApplyEmbeddedFontSizes(true)
                        } else {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                        }
                    }
                }
            )
        }
        else -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black))
        }
    }
}
