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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import coil.imageLoader
import com.arflix.tv.R
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.theme.appBackgroundDark
import com.arflix.tv.ui.components.ImdbSvgRatingBadge
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
@OptIn(ExperimentalLayoutApi::class)
internal fun CollectionSpotlight(
    catalog: CatalogConfig?,
    item: MediaItem?,
    isMobile: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    providerLogoUrl: String? = item?.primaryNetworkLogo
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
        compact -> 184.dp
        isMobile -> 272.dp
        else -> 206.dp
    }
    Box(Modifier.fillMaxWidth().height(height).testTag("collection_spotlight")) {
        if (backdrop != null) {
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to background, 0.30f to background.copy(alpha = 0.97f),
            0.62f to background.copy(alpha = 0.76f), 1f to background.copy(alpha = 0.08f)
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Transparent, background.copy(alpha = 0.15f), background
        ))))
        Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMobile) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                }
                Text(catalog?.title.orEmpty(), color = Color(0xFFB6BBC2), fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val textWidth = if (isMobile) 1f else 0.62f
            Text(item?.title ?: catalog?.title.orEmpty(),
                modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_title"),
                color = Color.White, fontSize = if (isMobile || compact) 23.sp else 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 32.sp,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            val facts = listOfNotNull(
                item?.year?.takeIf(String::isNotBlank),
                item?.duration?.takeIf(String::isNotBlank)
            ).joinToString("  ·  ")
            Text(facts, modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_facts"),
                color = Color(0xFFD7DBDF), fontSize = 12.sp, letterSpacing = 0.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rating = collectionRating(item)
            val budget = remember(item?.budget, item?.mediaType) { collectionBudget(item) }
            FlowRow(Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_brands"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!providerLogoUrl.isNullOrBlank()) {
                    val logo = remember(providerLogoUrl, context) {
                        ImageRequest.Builder(context).data(providerLogoUrl)
                            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                            .allowRgb565(false).size(156, 54).build()
                    }
                    AsyncImage(logo, stringResource(R.string.home_cd_primary_provider),
                        contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.width(52.dp).height(18.dp).testTag("collection_provider_logo"))
                }
                if (rating?.startsWith("IMDb ") == true) {
                    ImdbSvgRatingBadge(rating.removePrefix("IMDb "), context.imageLoader,
                        ratingFontSize = 13, logoWidth = 30.dp, logoHeight = 16.dp)
                } else if (rating != null) {
                    Text(rating, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (budget != null) {
                    Text("${stringResource(R.string.budget)} $budget", color = Color(0xFFB6BBC2),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(item?.overview?.takeIf(String::isNotBlank) ?: catalog?.collectionDescription.orEmpty(),
                modifier = Modifier.fillMaxWidth(textWidth).testTag("collection_spotlight_overview"),
                color = Color(0xFFC4C8CE), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
                maxLines = if (isMobile && !compact) 3 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
