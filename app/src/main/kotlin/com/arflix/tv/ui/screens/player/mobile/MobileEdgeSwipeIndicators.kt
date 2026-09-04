package com.arflix.tv.ui.screens.player.mobile

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Vertical edge HUD indicator for Brightness (Left) and Volume (Right).
 * Styled as a frosted glass capsule with stroke-rounded Hugeicons and animated level fill.
 */
@Composable
fun MobileEdgeIndicator(
    visible: Boolean,
    levelPct: Float, // 0.0f .. 1.0f
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    icon: ImageVector? = null,
    isAuto: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        val capsuleShape = RoundedCornerShape(18.dp)
        val trackShape = RoundedCornerShape(3.dp)

        Box(
            modifier = Modifier
                .zIndex(9f)
                .shadow(12.dp, capsuleShape, clip = false)
                .width(36.dp)
                .height(156.dp)
                .background(Color(0xB3121316), capsuleShape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), capsuleShape)
                .padding(vertical = 10.dp, horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Icon
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Center: Vertical slider track
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(6.dp)
                        .clip(trackShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (isAuto) 0f else levelPct.coerceIn(0f, 1f))
                            .background(Color.White, trackShape)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom: Percentage or "Auto" text
                Text(
                    text = if (isAuto) "Auto" else "${(levelPct * 100).toInt()}",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
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
