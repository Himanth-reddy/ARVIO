package com.arflix.tv.ui.screens.player.preview

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeekPreviewCard(
    frame: SeekPreviewFrame,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    timestamp: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(16.dp, shape, clip = false)
            .aspectRatio(16f / 9f)
            .background(Color.Black, shape)
            .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
            .clip(shape)
    ) {
        Crossfade(
            targetState = frame,
            animationSpec = tween(100),
            label = "seekPreviewFrame",
        ) { displayedFrame ->
            Image(
                bitmap = displayedFrame.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!timestamp.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = timestamp,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SeekPreviewPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    timestamp: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(16.dp, shape, clip = false)
            .aspectRatio(16f / 9f)
            .background(Color(0xFF141414), shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = androidx.compose.ui.Modifier.size(22.dp),
            color = Color.White.copy(alpha = 0.7f),
            strokeWidth = 2.dp
        )
        if (!timestamp.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = timestamp,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
