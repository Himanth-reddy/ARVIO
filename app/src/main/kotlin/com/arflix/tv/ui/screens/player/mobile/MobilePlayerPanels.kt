package com.arflix.tv.ui.screens.player.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.ui.screens.player.AudioTrackInfo

/**
 * Shared dim scrim backdrop. Tapping dismisses any open panel.
 */
@Composable
fun SharedScrimBackdrop(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(200)),
        modifier = modifier.zIndex(38f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MobilePlayerTokens.ScrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}

/**
 * Episodes Drawer (slides in from right side).
 */
@Composable
fun MobileEpisodesDrawer(
    visible: Boolean,
    episodes: List<Episode>,
    currentEpisodeNumber: Int?,
    onSelectEpisode: (Episode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(300)) { it },
        exit = slideOutHorizontally(tween(260)) { it },
        modifier = modifier.zIndex(40f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .background(MobilePlayerTokens.PanelBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume taps
                    )
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "This Season",
                        color = MobilePlayerTokens.InkPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    MobileIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onClose
                    )
                }

                // Episode List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        val isActive = ep.episodeNumber == currentEpisodeNumber
                        EpisodeRow(
                            episode = ep,
                            isActive = isActive,
                            onClick = {
                                onSelectEpisode(ep)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MobilePlayerTokens.ShapeCard)
            .background(if (isActive) MobilePlayerTokens.PanelBg2 else Color.Transparent)
            .then(
                if (isActive) Modifier.border(1.dp, MobilePlayerTokens.PanelBorder, MobilePlayerTokens.ShapeCard)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(36.dp)
                .clip(MobilePlayerTokens.ShapeThumb)
                .background(MobilePlayerTokens.PanelBg2)
                .then(
                    if (isActive) Modifier.border(1.5.dp, Color.White, MobilePlayerTokens.ShapeThumb)
                    else Modifier
                )
        ) {
            if (!episode.stillPath.isNullOrBlank()) {
                val fullUrl = if (episode.stillPath.startsWith("http")) episode.stillPath
                else "https://image.tmdb.org/t/p/w300${episode.stillPath}"
                AsyncImage(
                    model = fullUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Text (Title + Runtime)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.episodeNumber} — ${episode.name}",
                color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                fontSize = 12.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val durationLabel = if (episode.runtime > 0) "${episode.runtime}m" else ""
            if (durationLabel.isNotBlank()) {
                Text(
                    text = durationLabel,
                    color = MobilePlayerTokens.InkTertiary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // Checkmark
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MobilePlayerTokens.InkPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Sources Drawer (slides in from right side).
 */
@Composable
fun MobileSourcesDrawer(
    visible: Boolean,
    streams: List<StreamSource>,
    selectedStream: StreamSource?,
    onSelectSource: (StreamSource) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(300)) { it },
        exit = slideOutHorizontally(tween(260)) { it },
        modifier = modifier.zIndex(40f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .background(MobilePlayerTokens.PanelBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source",
                        color = MobilePlayerTokens.InkPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    MobileIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onClose
                    )
                }

                val effectiveStreams = remember(streams, selectedStream) {
                    if (streams.isEmpty() && selectedStream != null) listOf(selectedStream)
                    else streams
                }

                if (effectiveStreams.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sources available",
                            color = MobilePlayerTokens.InkTertiary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Sources list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(effectiveStreams, key = { it.url ?: it.hashCode().toString() }) { stream ->
                            val isActive = selectedStream?.url == stream.url
                            SourceRow(
                                stream = stream,
                                isActive = isActive,
                                onClick = {
                                    onSelectSource(stream)
                                    onClose()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    stream: StreamSource,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val qualityLabel = stream.quality.ifBlank { stream.source.ifBlank { "Source" } }
    val metaLabel = listOfNotNull(
        stream.addonName.takeIf { it.isNotBlank() },
        stream.size.takeIf { it.isNotBlank() } ?: stream.sizeBytes?.let { formatBytes(it) }
    ).joinToString(" — ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MobilePlayerTokens.ShapeCard)
            .background(if (isActive) MobilePlayerTokens.PanelBg2 else Color.Transparent)
            .then(
                if (isActive) Modifier.border(1.dp, MobilePlayerTokens.PanelBorder, MobilePlayerTokens.ShapeCard)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = qualityLabel,
                color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            if (metaLabel.isNotBlank()) {
                Text(
                    text = metaLabel,
                    color = MobilePlayerTokens.InkTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MobilePlayerTokens.InkPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Audio Track Bottom Sheet.
 */
@Composable
fun MobileAudioTrackSheet(
    visible: Boolean,
    audioTracks: List<AudioTrackInfo>,
    selectedAudioIndex: Int,
    onSelectAudio: (AudioTrackInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    MobileBottomSheetBase(
        visible = visible,
        title = "Audio Track",
        onClose = onClose,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(audioTracks) { index, track ->
                val isActive = index == selectedAudioIndex
                val trackTitle = track.label?.takeIf { it.isNotBlank() }
                    ?: track.language?.takeIf { it.isNotBlank() }
                    ?: "Audio ${index + 1}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MobilePlayerTokens.ShapeCard)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onSelectAudio(track)
                                onClose()
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = trackTitle,
                        color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )

                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MobilePlayerTokens.InkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Subtitles Bottom Sheet with 2-Stage Drilldown (Option 1):
 * Stage 1: Clean Languages Overview list with active indicators, track counts, and Off option.
 * Stage 2: Detailed Track List for the selected language with badges (Embedded, Provider, SDH, Forced).
 */
@Composable
fun MobileSubtitlesSheet(
    visible: Boolean,
    subtitles: List<Subtitle>,
    selectedSubtitle: Subtitle?,
    onSelectSubtitle: (Subtitle?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLanguageName by remember { mutableStateOf<String?>(null) }

    // Reset drilldown when sheet becomes visible (opening fresh), NOT while exiting
    LaunchedEffect(visible) {
        if (visible) {
            selectedLanguageName = null
        }
    }

    // Intercept hardware/gesture back when inside Stage 2
    BackHandler(enabled = visible && selectedLanguageName != null) {
        selectedLanguageName = null
    }

    val subtitleGroups = remember(subtitles) {
        subtitles
            .groupBy { getFullLanguageName(it.lang).ifBlank { "Unknown" } }
            .toList()
            .sortedBy { (lang, _) -> lang }
    }

    val activeLangName = selectedSubtitle?.let { getFullLanguageName(it.lang).ifBlank { "Unknown" } }

    val currentTitle = selectedLanguageName?.let { "$it Subtitles" } ?: "Subtitles"
    val showBack = selectedLanguageName != null

    MobileBottomSheetBase(
        visible = visible,
        title = currentTitle,
        showBackButton = showBack,
        onBack = { selectedLanguageName = null },
        onClose = onClose,
        modifier = modifier
    ) {
        val currentLang = selectedLanguageName
        if (currentLang == null) {
            // ── Stage 1: Languages Overview ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Off Option
                item {
                    val isOffSelected = selectedSubtitle == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isOffSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onSelectSubtitle(null)
                                    onClose()
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Off (Disable Subtitles)",
                            color = if (isOffSelected) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (isOffSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isOffSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MobilePlayerTokens.InkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Language Groups
                items(subtitleGroups, key = { it.first }) { (langName, tracks) ->
                    val isLangActive = langName == activeLangName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLangActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectedLanguageName = langName
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = langName,
                                color = if (isLangActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isLangActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isLangActive && selectedSubtitle != null) {
                                Text(
                                    text = "Active: ${selectedSubtitle.label.ifBlank { langName }}",
                                    color = MobilePlayerTokens.InkTertiary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isLangActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MobilePlayerTokens.InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${tracks.size}",
                                color = MobilePlayerTokens.InkTertiary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open $langName tracks",
                                tint = MobilePlayerTokens.InkTertiary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ── Stage 2: Detailed Tracks for Chosen Language ──
            val tracksForLang = subtitleGroups.firstOrNull { it.first == currentLang }?.second ?: emptyList()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(tracksForLang, key = { _, sub -> sub.id }) { index, sub ->
                    val isActive = selectedSubtitle?.id == sub.id
                    val trackTitle = sub.label.ifBlank { "$currentLang Track ${index + 1}" }
                    val isSdh = trackTitle.contains("sdh", ignoreCase = true) ||
                        trackTitle.contains("cc", ignoreCase = true) ||
                        trackTitle.contains("hearing", ignoreCase = true)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onSelectSubtitle(sub)
                                    selectedLanguageName = null
                                    onClose()
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trackTitle,
                                color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )

                            // Badges Row
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sub.isEmbedded) {
                                    SubtitleBadge(text = "Embedded")
                                } else if (sub.provider.isNotBlank()) {
                                    SubtitleBadge(text = sub.provider)
                                }
                                if (isSdh) {
                                    SubtitleBadge(text = "SDH")
                                }
                                if (sub.isForced) {
                                    SubtitleBadge(text = "Forced")
                                }
                            }
                        }

                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MobilePlayerTokens.InkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = MobilePlayerTokens.InkTertiary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getFullLanguageName(code: String?): String {
    if (code.isNullOrBlank()) return "Unknown"
    val raw = code.trim()
    val normalizedCode = raw.lowercase().replace('_', '-')

    val mapped = when {
        normalizedCode in listOf("en", "eng", "english") -> "English"
        normalizedCode in listOf("es", "spa", "spanish") -> "Spanish"
        normalizedCode in listOf("es-419", "es-la") -> "Spanish (Latin America)"
        normalizedCode in listOf("es-es") -> "Spanish (Spain)"
        normalizedCode in listOf("nl", "nld", "dut", "dutch") -> "Dutch"
        normalizedCode in listOf("de", "ger", "deu", "german") -> "German"
        normalizedCode in listOf("fr", "fra", "fre", "french") -> "French"
        normalizedCode in listOf("it", "ita", "italian") -> "Italian"
        normalizedCode in listOf("pt", "por", "portuguese") -> "Portuguese"
        normalizedCode in listOf("pt-br", "pob") -> "Portuguese (Brazil)"
        normalizedCode in listOf("pt-pt") -> "Portuguese (Portugal)"
        normalizedCode in listOf("ru", "rus", "russian") -> "Russian"
        normalizedCode in listOf("ja", "jpn", "japanese") -> "Japanese"
        normalizedCode in listOf("ko", "kor", "korean") -> "Korean"
        normalizedCode in listOf("zh", "chi", "zho", "chinese") -> "Chinese"
        normalizedCode in listOf("zh-cn", "zh-hans", "chs") -> "Chinese (Simplified)"
        normalizedCode in listOf("zh-tw", "zh-hk", "zh-hant", "cht") -> "Chinese (Traditional)"
        normalizedCode in listOf("ar", "ara", "arabic") -> "Arabic"
        normalizedCode in listOf("hi", "hin", "hindi") -> "Hindi"
        normalizedCode in listOf("te", "tel", "telugu") -> "Telugu"
        normalizedCode in listOf("ta", "tam", "tamil") -> "Tamil"
        normalizedCode in listOf("ml", "mal", "malayalam") -> "Malayalam"
        normalizedCode in listOf("kn", "kan", "kannada") -> "Kannada"
        normalizedCode in listOf("mr", "mar", "marathi") -> "Marathi"
        normalizedCode in listOf("bn", "ben", "bengali") -> "Bengali"
        normalizedCode in listOf("gu", "guj", "gujarati") -> "Gujarati"
        normalizedCode in listOf("pa", "pan", "punjabi") -> "Punjabi"
        normalizedCode in listOf("ur", "urd", "urdu") -> "Urdu"
        normalizedCode in listOf("tr", "tur", "turkish") -> "Turkish"
        normalizedCode in listOf("pl", "pol", "polish") -> "Polish"
        normalizedCode in listOf("sv", "swe", "swedish") -> "Swedish"
        normalizedCode in listOf("no", "nor", "nob", "norwegian") -> "Norwegian"
        normalizedCode in listOf("da", "dan", "danish") -> "Danish"
        normalizedCode in listOf("fi", "fin", "finnish") -> "Finnish"
        normalizedCode in listOf("el", "gre", "ell", "greek") -> "Greek"
        normalizedCode in listOf("he", "heb", "hebrew") -> "Hebrew"
        normalizedCode in listOf("id", "ind", "indonesian") -> "Indonesian"
        normalizedCode in listOf("vi", "vie", "vietnamese") -> "Vietnamese"
        normalizedCode in listOf("th", "tha", "thai") -> "Thai"
        normalizedCode in listOf("cs", "ces", "cze", "czech") -> "Czech"
        normalizedCode in listOf("hu", "hun", "hungarian") -> "Hungarian"
        normalizedCode in listOf("ro", "ron", "rum", "romanian") -> "Romanian"
        normalizedCode in listOf("uk", "ukr", "ukrainian") -> "Ukrainian"
        normalizedCode in listOf("ms", "msa", "may", "malay") -> "Malay"
        normalizedCode in listOf("fa", "fas", "per", "persian") -> "Persian"
        normalizedCode in listOf("tl", "tgl", "fil", "tagalog", "filipino") -> "Tagalog"
        normalizedCode in listOf("bg", "bul", "bulgarian") -> "Bulgarian"
        normalizedCode in listOf("sr", "srp", "serbian") -> "Serbian"
        normalizedCode in listOf("hr", "hrv", "croatian") -> "Croatian"
        normalizedCode in listOf("sk", "slk", "slo", "slovak") -> "Slovak"
        normalizedCode in listOf("sl", "slv", "slovenian") -> "Slovenian"
        normalizedCode in listOf("lt", "lit", "lithuanian") -> "Lithuanian"
        normalizedCode in listOf("lv", "lav", "latvian") -> "Latvian"
        normalizedCode in listOf("et", "est", "estonian") -> "Estonian"
        normalizedCode in listOf("is", "isl", "ice", "icelandic") -> "Icelandic"
        normalizedCode in listOf("ca", "cat", "catalan") -> "Catalan"
        normalizedCode in listOf("eu", "eus", "baq", "basque") -> "Basque"
        normalizedCode in listOf("gl", "glg", "galician") -> "Galician"
        normalizedCode in listOf("hy", "hye", "arm", "armenian") -> "Armenian"
        normalizedCode in listOf("ka", "kat", "geo", "georgian") -> "Georgian"
        normalizedCode in listOf("az", "aze", "azerbaijani") -> "Azerbaijani"
        normalizedCode in listOf("kk", "kaz", "kazakh") -> "Kazakh"
        normalizedCode in listOf("uz", "uzb", "uzbek") -> "Uzbek"
        normalizedCode in listOf("mn", "mon", "mongolian") -> "Mongolian"
        normalizedCode in listOf("ne", "nep", "nepali") -> "Nepali"
        normalizedCode in listOf("si", "sin", "sinhala") -> "Sinhala"
        normalizedCode in listOf("my", "mya", "bur", "burmese") -> "Burmese"
        normalizedCode in listOf("km", "khm", "khmer") -> "Khmer"
        normalizedCode in listOf("lo", "lao") -> "Lao"
        normalizedCode in listOf("am", "amh", "amharic") -> "Amharic"
        normalizedCode in listOf("sw", "swa", "swahili") -> "Swahili"
        normalizedCode in listOf("af", "afr", "afrikaans") -> "Afrikaans"
        normalizedCode in listOf("sq", "sqi", "alb", "albanian") -> "Albanian"
        normalizedCode in listOf("bs", "bos", "bosnian") -> "Bosnian"
        normalizedCode in listOf("mk", "mkd", "mac", "macedonian") -> "Macedonian"
        normalizedCode in listOf("cy", "cym", "wel", "welsh") -> "Welsh"
        normalizedCode in listOf("ga", "gle", "irish") -> "Irish"
        normalizedCode in listOf("gd", "gla", "scottish gaelic") -> "Scottish Gaelic"
        normalizedCode in listOf("la", "lat", "latin") -> "Latin"
        normalizedCode in listOf("eo", "epo", "esperanto") -> "Esperanto"
        normalizedCode in listOf("und", "undetermined", "unknown") -> "Unknown"
        else -> null
    }

    if (mapped != null) return mapped

    // Java Locale fallback for any ISO language code or locale tag
    return runCatching {
        val loc = if (normalizedCode.contains("-")) {
            java.util.Locale.forLanguageTag(normalizedCode)
        } else {
            java.util.Locale.Builder().setLanguage(normalizedCode).build()
        }
        val display = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
        if (display.isNotBlank() && !display.equals(normalizedCode, ignoreCase = true)) {
            display
        } else {
            null
        }
    }.getOrNull() ?: raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/**
 * Playback Speed Bottom Sheet.
 */
@Composable
fun MobilePlaybackSpeedSheet(
    visible: Boolean,
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
    MobileBottomSheetBase(
        visible = visible,
        title = "Playback Speed",
        onClose = onClose,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            speeds.forEach { sp ->
                val isActive = sp == currentSpeed
                val label = if (sp == 1.0f) "1x" else "${sp}x"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MobilePlayerTokens.ShapeCard)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onSelectSpeed(sp)
                                onClose()
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MobilePlayerTokens.InkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hierarchical More Settings Bottom Sheet.
 */
@Composable
fun MobileMoreSettingsSheet(
    visible: Boolean,
    autoplayNext: Boolean,
    autoSkipIntro: Boolean,
    autoSkipOutro: Boolean,
    aspectRatio: String,
    audioDelayMs: Long,
    volumeNormalization: Boolean,
    subtitleDelayMs: Long,
    subtitleSizePct: Int,
    subtitleColorHex: String,
    subtitlePosition: String,
    selectedSourceName: String,
    onToggleAutoplay: (Boolean) -> Unit,
    onToggleAutoSkipIntro: (Boolean) -> Unit,
    onToggleAutoSkipOutro: (Boolean) -> Unit,
    onSelectAspectRatio: (String) -> Unit,
    onUpdateAudioDelay: (Long) -> Unit,
    onToggleVolumeNorm: (Boolean) -> Unit,
    onUpdateSubtitleDelay: (Long) -> Unit,
    onUpdateSubtitleSize: (Int) -> Unit,
    onUpdateSubtitleColor: (String) -> Unit,
    onUpdateSubtitlePosition: (String) -> Unit,
    onOpenSourcesDrawer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewStack = remember { mutableStateListOf("root") }

    // Reset view stack when opening fresh, NOT during exit animation
    LaunchedEffect(visible) {
        if (visible) {
            viewStack.clear()
            viewStack.add("root")
        }
    }

    // Intercept hardware/gesture back when inside sub-pages
    BackHandler(enabled = visible && viewStack.size > 1) {
        viewStack.removeAt(viewStack.lastIndex)
    }

    val currentView = viewStack.lastOrNull() ?: "root"
    val title = when (currentView) {
        "aspect" -> "Aspect Ratio"
        "audiodelay" -> "Audio Delay"
        "subdelay" -> "Subtitle Delay"
        "substyle" -> "Subtitle Style"
        else -> "More Settings"
    }

    MobileBottomSheetBase(
        visible = visible,
        title = title,
        showBackButton = viewStack.size > 1,
        onBack = { if (viewStack.size > 1) viewStack.removeAt(viewStack.lastIndex) },
        onClose = onClose,
        modifier = modifier
    ) {
        when (currentView) {
            "root" -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Playback Section
                    item { SectionHeader("PLAYBACK") }
                    item {
                        ToggleRow(
                            label = "Autoplay next episode",
                            checked = autoplayNext,
                            onToggle = { onToggleAutoplay(!autoplayNext) }
                        )
                    }
                    item {
                        ToggleRow(
                            label = "Automatically skip intro",
                            checked = autoSkipIntro,
                            onToggle = { onToggleAutoSkipIntro(!autoSkipIntro) }
                        )
                    }
                    item {
                        ToggleRow(
                            label = "Automatically skip outro",
                            checked = autoSkipOutro,
                            onToggle = { onToggleAutoSkipOutro(!autoSkipOutro) }
                        )
                    }

                    // Video Section
                    item { SectionHeader("VIDEO") }
                    item {
                        NavRow(
                            label = "Quality",
                            meta = selectedSourceName.ifBlank { "Auto" },
                            onClick = {
                                onClose()
                                onOpenSourcesDrawer()
                            }
                        )
                    }
                    item {
                        NavRow(
                            label = "Aspect Ratio",
                            meta = aspectRatio,
                            onClick = { viewStack.add("aspect") }
                        )
                    }

                    // Audio Section
                    item { SectionHeader("AUDIO") }
                    item {
                        NavRow(
                            label = "Audio Delay",
                            meta = "$audioDelayMs ms",
                            onClick = { viewStack.add("audiodelay") }
                        )
                    }
                    item {
                        ToggleRow(
                            label = "Volume Normalization",
                            checked = volumeNormalization,
                            onToggle = { onToggleVolumeNorm(!volumeNormalization) }
                        )
                    }

                    // Subtitles Section
                    item { SectionHeader("SUBTITLES") }
                    item {
                        NavRow(
                            label = "Subtitle Delay",
                            meta = "$subtitleDelayMs ms",
                            onClick = { viewStack.add("subdelay") }
                        )
                    }
                    item {
                        NavRow(
                            label = "Subtitle Style",
                            meta = "Size, color, position",
                            onClick = { viewStack.add("substyle") }
                        )
                    }

                    // Advanced Section
                    item { SectionHeader("ADVANCED") }
                    item {
                        Text(
                            text = "No additional advanced options are available yet.",
                            color = MobilePlayerTokens.InkTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            "aspect" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Auto", "Fit to Screen", "Stretch", "Crop").forEach { mode ->
                        val isActive = mode.equals(aspectRatio, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MobilePlayerTokens.ShapeCard)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onSelectAspectRatio(mode)
                                        if (viewStack.size > 1) viewStack.removeAt(viewStack.lastIndex)
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = mode,
                                color = if (isActive) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkSecondary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MobilePlayerTokens.InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            "audiodelay" -> {
                StepperPanel(
                    label = "Audio offset",
                    valueText = "$audioDelayMs ms",
                    onDecrease = { onUpdateAudioDelay((audioDelayMs - 25L).coerceAtLeast(-500L)) },
                    onIncrease = { onUpdateAudioDelay((audioDelayMs + 25L).coerceAtMost(500L)) }
                )
            }
            "subdelay" -> {
                StepperPanel(
                    label = "Subtitle offset",
                    valueText = "$subtitleDelayMs ms",
                    onDecrease = { onUpdateSubtitleDelay((subtitleDelayMs - 25L).coerceAtLeast(-500L)) },
                    onIncrease = { onUpdateSubtitleDelay((subtitleDelayMs + 25L).coerceAtMost(500L)) }
                )
            }
            "substyle" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Size Stepper
                    Column {
                        SectionHeader("SIZE")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Text size",
                                color = MobilePlayerTokens.InkSecondary,
                                fontSize = 13.5.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "–",
                                    color = MobilePlayerTokens.InkPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onUpdateSubtitleSize((subtitleSizePct - 10).coerceAtLeast(70)) }
                                        )
                                        .padding(4.dp)
                                )
                                Text(
                                    text = "$subtitleSizePct%",
                                    color = MobilePlayerTokens.InkPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "+",
                                    color = MobilePlayerTokens.InkPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onUpdateSubtitleSize((subtitleSizePct + 10).coerceAtMost(150)) }
                                        )
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    // Color Swatches
                    Column {
                        SectionHeader("COLOR")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val swatches = listOf(
                                "#fff" to Color(0xFFFFFFFF),
                                "#ffe066" to Color(0xFFFFE066),
                                "#66d9ff" to Color(0xFF66D9FF),
                                "#ff6666" to Color(0xFFFF6666)
                            )
                            swatches.forEach { (hex, color) ->
                                val isSelected = hex.equals(subtitleColorHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (isSelected) Modifier.border(2.5.dp, Color(0xFF0B0C10), CircleShape)
                                                .border(4.dp, Color.White, CircleShape)
                                            else Modifier.border(1.dp, Color(0x40FFFFFF), CircleShape)
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onUpdateSubtitleColor(hex) }
                                        )
                                )
                            }
                        }
                    }

                    // Position Tabs
                    Column {
                        SectionHeader("POSITION")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("bottom" to "Bottom", "top" to "Top").forEach { (posKey, label) ->
                                val isSelected = posKey.equals(subtitlePosition, ignoreCase = true)
                                Column(
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onUpdateSubtitlePosition(posKey) }
                                        )
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MobilePlayerTokens.InkPrimary else MobilePlayerTokens.InkTertiary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(2.dp)
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MobilePlayerTokens.InkTertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MobilePlayerTokens.ShapeCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MobilePlayerTokens.InkPrimary,
            fontSize = 13.5.sp
        )
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MobilePlayerTokens.InkPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    meta: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MobilePlayerTokens.ShapeCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MobilePlayerTokens.InkPrimary,
            fontSize = 13.5.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = meta,
                color = MobilePlayerTokens.InkTertiary,
                fontSize = 12.sp
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MobilePlayerTokens.InkTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StepperPanel(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MobilePlayerTokens.InkSecondary,
            fontSize = 13.5.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "–",
                color = MobilePlayerTokens.InkPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDecrease
                    )
                    .padding(4.dp)
            )
            Text(
                text = valueText,
                color = MobilePlayerTokens.InkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(60.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "+",
                color = MobilePlayerTokens.InkPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIncrease
                    )
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Base bottom sheet container with drag handle, title, and close/back buttons.
 */
@Composable
private fun MobileBottomSheetBase(
    visible: Boolean,
    title: String,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(260)) { it },
        modifier = modifier.zIndex(40f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .clip(MobilePlayerTokens.ShapeSheet)
                    .background(MobilePlayerTokens.PanelBg)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume taps
                    )
                    .padding(bottom = 16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .align(Alignment.CenterHorizontally)
                )

                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showBackButton) {
                            MobileIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack
                            )
                        }
                        Text(
                            text = title,
                            color = MobilePlayerTokens.InkPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MobileIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onClose
                    )
                }

                // Content
                content()
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

internal fun formatLanguageDisplayName(code: String?): String {
    if (code.isNullOrBlank()) return "Unknown"
    val normalizedCode = code.lowercase().trim()
    return when {
        normalizedCode in listOf("en", "eng", "english") -> "English"
        normalizedCode in listOf("es", "spa", "spanish") -> "Spanish"
        normalizedCode in listOf("fr", "fra", "fre", "french") -> "French"
        normalizedCode in listOf("de", "ger", "deu", "german") -> "German"
        normalizedCode in listOf("it", "ita", "italian") -> "Italian"
        normalizedCode in listOf("pt", "por", "portuguese") -> "Portuguese"
        normalizedCode in listOf("pt-br", "pob") -> "Portuguese (Brazil)"
        normalizedCode in listOf("ja", "jpn", "japanese") -> "Japanese"
        normalizedCode in listOf("ko", "kor", "korean") -> "Korean"
        normalizedCode in listOf("zh", "chi", "zho", "chinese") -> "Chinese"
        normalizedCode in listOf("hi", "hin", "hindi") -> "Hindi"
        normalizedCode in listOf("ru", "rus", "russian") -> "Russian"
        normalizedCode in listOf("ar", "ara", "arabic") -> "Arabic"
        normalizedCode in listOf("nl", "nld", "dut", "dutch") -> "Dutch"
        normalizedCode in listOf("pl", "pol", "polish") -> "Polish"
        normalizedCode in listOf("tr", "tur", "turkish") -> "Turkish"
        normalizedCode in listOf("sv", "swe", "swedish") -> "Swedish"
        normalizedCode in listOf("no", "nor", "norwegian") -> "Norwegian"
        normalizedCode in listOf("da", "dan", "danish") -> "Danish"
        normalizedCode in listOf("fi", "fin", "finnish") -> "Finnish"
        normalizedCode in listOf("th", "tha", "thai") -> "Thai"
        normalizedCode in listOf("vi", "vie", "vietnamese") -> "Vietnamese"
        normalizedCode in listOf("id", "ind", "indonesian") -> "Indonesian"
        normalizedCode in listOf("und", "unknown") -> "Unknown"
        else -> code.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
}

internal fun formatAudioCodecDisplayName(codec: String?): String? {
    if (codec.isNullOrBlank()) return null
    return when {
        codec.contains("eac3-joc", ignoreCase = true) || codec.contains("atmos", ignoreCase = true) -> "Dolby Atmos"
        codec.contains("eac3", ignoreCase = true) || codec.contains("ec-3", ignoreCase = true) -> "E-AC-3"
        codec.contains("ac3", ignoreCase = true) || codec.contains("ac-3", ignoreCase = true) -> "Dolby Digital"
        codec.contains("dts-hd", ignoreCase = true) -> "DTS-HD"
        codec.contains("dts", ignoreCase = true) -> "DTS"
        codec.contains("truehd", ignoreCase = true) -> "TrueHD"
        codec.contains("flac", ignoreCase = true) -> "FLAC"
        codec.contains("opus", ignoreCase = true) -> "Opus"
        codec.contains("mp4a", ignoreCase = true) || codec.contains("aac", ignoreCase = true) -> "AAC"
        codec.contains("mp3", ignoreCase = true) -> "MP3"
        else -> codec.substringAfterLast('/').uppercase()
    }
}
