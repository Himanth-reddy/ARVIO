package com.arflix.tv.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arflix.tv.R
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.theme.appBackgroundDark
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal fun collectionBudget(item: MediaItem?, locale: Locale = Locale.getDefault()): String? {
    val budget = item?.budget?.takeIf { it > 0 && item.mediaType == MediaType.MOVIE } ?: return null
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("USD")
        maximumFractionDigits = 0
    }.format(budget)
}

internal fun collectionRating(item: MediaItem?): String? {
    if (item == null) return null
    fun valid(value: String) = value.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 10 } == true
    return when {
        valid(item.imdbRating) -> "IMDb ${item.imdbRating}"
        valid(item.tmdbRating) -> "TMDB ${item.tmdbRating}"
        else -> null
    }
}

/** Fixed-size, non-interactive preview: changing focus never moves the grid below it. */
@Composable
internal fun CollectionSpotlight(
    catalog: CatalogConfig?,
    item: MediaItem?,
    isMobile: Boolean,
    compact: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val backdrop = item?.backdrop?.takeIf(String::isNotBlank)
        ?: catalog?.collectionHeroImageUrl?.takeIf(String::isNotBlank)
    val request = remember(backdrop, context) {
        ImageRequest.Builder(context).data(backdrop).size(1280, 540).crossfade(160).build()
    }
    val background = appBackgroundDark()
    val inset = if (isMobile) 20.dp else 42.dp
    val height = when {
        compact -> 156.dp
        isMobile -> 240.dp
        else -> 180.dp
    }
    Box(Modifier.fillMaxWidth().height(height).testTag("collection_spotlight")) {
        if (backdrop != null) {
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            background, background.copy(alpha = 0.94f), background.copy(alpha = 0.35f)
        ))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, background.copy(alpha = 0.15f), background
        ))))
        Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMobile) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                }
                Text(catalog?.title.orEmpty(), color = Color(0xFFB6BBC2), fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val textWidth = if (isMobile) 1f else 0.76f
            Text(item?.title ?: catalog?.title.orEmpty(),
                modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_title"),
                color = Color.White, fontSize = if (isMobile || compact) 24.sp else 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 32.sp,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            val budget = remember(item?.budget, item?.mediaType) { collectionBudget(item) }
            val facts = listOfNotNull(
                item?.year?.takeIf(String::isNotBlank),
                item?.duration?.takeIf(String::isNotBlank),
                collectionRating(item),
                budget?.let { "${stringResource(R.string.budget)} $it" }
            ).joinToString("  ·  ")
            Text(facts, modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_facts"),
                color = Color(0xFFD7DBDF), fontSize = 12.sp, letterSpacing = 0.sp,
                maxLines = if (isMobile && !compact) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(item?.overview?.takeIf(String::isNotBlank) ?: catalog?.collectionDescription.orEmpty(),
                modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_overview"),
                color = Color(0xFFC4C8CE), fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp,
                maxLines = if (isMobile && !compact) 3 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
