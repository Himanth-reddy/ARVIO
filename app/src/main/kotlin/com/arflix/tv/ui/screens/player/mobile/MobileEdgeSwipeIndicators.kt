package com.arflix.tv.ui.screens.player.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Vertical edge HUD indicator for Brightness (Left) and Volume (Right).
 */
@Composable
fun MobileEdgeIndicator(
    visible: Boolean,
    icon: ImageVector,
    levelPct: Float, // 0.0f .. 1.0f
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.zIndex(9f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MobilePlayerTokens.InkPrimary,
                modifier = Modifier.size(20.dp)
            )

            // Vertical slider bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(levelPct.coerceIn(0f, 1f))
                        .background(MobilePlayerTokens.InkPrimary, RoundedCornerShape(2.dp))
                )
            }

            // Percentage value label
            Text(
                text = "${(levelPct * 100).toInt()}",
                color = MobilePlayerTokens.InkPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = MobilePlayerTokens.TextShadow
                )
            )
        }
    }
}

/**
 * Transient center HUD indicator for Aspect Ratio cycling (Fit / Fill / Zoom).
 */
@Composable
fun MobileAspectIndicator(
    aspectText: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(MobilePlayerTokens.PanelBg, RoundedCornerShape(10.dp))
                .border(1.dp, MobilePlayerTokens.PanelBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(20f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = aspectText,
                color = MobilePlayerTokens.InkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
