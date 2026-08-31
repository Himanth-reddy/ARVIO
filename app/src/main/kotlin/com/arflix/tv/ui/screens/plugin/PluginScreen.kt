package com.arflix.tv.ui.screens.plugin

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.arflix.tv.R
import com.arflix.tv.domain.model.PluginRepository
import com.arflix.tv.domain.model.ScraperInfo
import com.arflix.tv.ui.components.MobileSettingsCategory
import com.arflix.tv.ui.components.MobileSettingsRow
import com.arflix.tv.ui.components.SettingsRow
import com.arflix.tv.ui.components.SettingsToggleRow
import com.arflix.tv.ui.screens.settings.settingsFocusSlot
import com.arflix.tv.ui.skin.resolveAccentColor
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundElevated
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.SuccessGreen
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.util.findActivity
import com.arflix.tv.util.tr

data class PluginInputField(
    val label: String,
    val value: String,
    val placeholder: String = "",
    val helper: String = "",
    val isSecret: Boolean = false,
    val singleLine: Boolean = true,
    val onValueChange: (String) -> Unit
)

@Composable
private fun ScraperLogo(
    logoUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            tint = TextSecondary,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginScreen(
    viewModel: PluginViewModel = hiltViewModel(),
    focusedIndex: Int = -1,
    focusedActionIndex: Int = 0,
    onFocusedIndexChanged: (Int) -> Unit = {},
    onMaxIndexChanged: (Int) -> Unit = {},
    enterTrigger: Int = -1,
    onEnterTriggerHandled: () -> Unit = {},
    onModalStateChanged: (Boolean) -> Unit = {},
    onBackPressed: () -> Unit,
    onNavigateToSection: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<PluginRepository?>(null) }
    var customRepoUrl by remember { mutableStateOf("") }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sectionNavKey = if (isRtl) Key.DirectionRight else Key.DirectionLeft
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val accentColor = resolveAccentColor(fallback = Pink)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val repositories = uiState.repositories
    val scrapers = uiState.scrapers

    // Dynamic index mapping for TV:
    // Slot 0: Add Repository button
    // Slot 1 .. repos.size: Repositories
    // Slot (1 + repos.size) .. (repos.size + scrapersCount): Scrapers
    // Slot (1 + repos.size + scrapersCount): Reset button
    val scrapersCount = if (scrapers.isEmpty()) 1 else scrapers.size
    val totalItems = 1 + repositories.size + scrapersCount + 1

    LaunchedEffect(totalItems) {
        onMaxIndexChanged((totalItems - 1).coerceAtLeast(0))
        if (focusedIndex >= totalItems) {
            onFocusedIndexChanged((totalItems - 1).coerceAtLeast(0))
        }
    }

    val modalOpen = showAddDialog || showResetDialog || (repoToDelete != null)
    LaunchedEffect(modalOpen) {
        onModalStateChanged(modalOpen)
    }

    LaunchedEffect(enterTrigger, repositories, scrapersCount, totalItems, focusedActionIndex) {
        if (enterTrigger >= 0) {
            when (enterTrigger) {
                0 -> { showAddDialog = true }
                in 1..repositories.size -> {
                    val repo = repositories[enterTrigger - 1]
                    if (focusedActionIndex == 1) {
                        repoToDelete = repo
                    } else {
                        viewModel.onEvent(PluginUiEvent.RefreshRepository(repo.id))
                    }
                }
                in (1 + repositories.size)..(repositories.size + scrapersCount) -> {
                    if (scrapers.isNotEmpty()) {
                        val scraper = scrapers[enterTrigger - 1 - repositories.size]
                        val hasSettings = scraper.id in uiState.scrapersWithSettings
                        if (hasSettings && focusedActionIndex == 1) {
                            if (activity != null) {
                                viewModel.onEvent(PluginUiEvent.OpenPluginSettings(scraper.id, activity))
                            }
                        } else {
                            viewModel.onEvent(PluginUiEvent.ToggleScraper(scraper.id, !scraper.enabled))
                        }
                    }
                }
                totalItems - 1 -> { showResetDialog = true }
            }
            onEnterTriggerHandled()
        }
    }

    BackHandler(enabled = modalOpen) {
        if (showAddDialog) showAddDialog = false
        else if (showResetDialog) showResetDialog = false
        else if (repoToDelete != null) repoToDelete = null
    }

    BackHandler(enabled = !isMobile && !modalOpen) {
        onBackPressed()
    }

    if (isMobile) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Add Repository
            MobileSettingsCategory(title = stringResource(R.string.plugin_screen_add_repo).uppercase()) {
                MobileSettingsRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.plugin_screen_add_repo),
                    subtitle = stringResource(R.string.plugin_screen_add_repo_desc),
                    value = "",
                    isFocused = false,
                    showDivider = false,
                    onClick = { showAddDialog = true }
                )
            }

            // Section 2: Installed Repositories
            MobileSettingsCategory(title = stringResource(R.string.plugin_screen_installed_repos).uppercase()) {
                if (repositories.isEmpty()) {
                    MobileSettingsRow(
                        icon = Icons.Default.Extension,
                        title = stringResource(R.string.plugin_screen_no_repos),
                        value = "",
                        isFocused = false,
                        showDivider = false,
                        onClick = {}
                    )
                } else {
                    repositories.forEachIndexed { idx, repo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScraperLogo(
                                logoUrl = repo.iconUrl,
                                name = repo.name,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = repo.name,
                                    style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = repo.url,
                                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            // Refresh button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { viewModel.onEvent(PluginUiEvent.RefreshRepository(repo.id)) }
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh_addons),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Delete button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { repoToDelete = repo }
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (idx < repositories.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(Color.White.copy(alpha = 0.05f))
                            )
                        }
                    }
                }
            }

            // Section 3: Installed Scrapers
            MobileSettingsCategory(title = stringResource(R.string.plugin_screen_installed_scrapers).uppercase()) {
                if (scrapers.isEmpty()) {
                    MobileSettingsRow(
                        icon = Icons.Default.Extension,
                        title = stringResource(R.string.plugin_screen_no_scrapers),
                        value = "",
                        isFocused = false,
                        showDivider = false,
                        onClick = {}
                    )
                } else {
                    scrapers.forEachIndexed { idx, scraper ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onEvent(
                                        PluginUiEvent.ToggleScraper(scraper.id, !scraper.enabled)
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScraperLogo(
                                logoUrl = scraper.logo,
                                name = scraper.name,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scraper.name,
                                    style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (scraper.description.isNotBlank()) scraper.description else scraper.id,
                                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val hasSettings = scraper.id in uiState.scrapersWithSettings
                            if (hasSettings) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            if (activity != null) {
                                                viewModel.onEvent(
                                                    PluginUiEvent.OpenPluginSettings(scraper.id, activity)
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.plugin_settings),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            // Toggle switch
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(24.dp)
                                    .background(
                                        color = if (scraper.enabled) SuccessGreen else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    .padding(3.dp),
                                contentAlignment = if (scraper.enabled) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(color = Color.White, shape = RoundedCornerShape(10.dp))
                                )
                            }
                        }
                        if (idx < scrapers.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(Color.White.copy(alpha = 0.05f))
                            )
                        }
                    }
                }
            }

            // Section 4: Reset Plugins & Extensions
            MobileSettingsCategory(title = "") {
                MobileSettingsRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.plugin_screen_reset_title),
                    subtitle = stringResource(R.string.plugin_screen_reset_desc),
                    value = "",
                    isFocused = false,
                    showDivider = false,
                    onClick = { showResetDialog = true }
                )
            }
        }
    } else {
        // TV UI
        Column(
            modifier = Modifier
                .padding(bottom = 80.dp)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            sectionNavKey -> {
                                onNavigateToSection?.invoke()
                                return@onPreviewKeyEvent onNavigateToSection != null
                            }
                            Key.Back, Key.Escape -> {
                                onBackPressed()
                                return@onPreviewKeyEvent true
                            }
                            else -> {}
                        }
                    }
                    false
                }
        ) {
            // TV Add Button
            val isAddRowFocused = (focusedIndex == 0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsFocusSlot(0)
                    .focusProperties { canFocus = false }
                    .clickable { showAddDialog = true }
                    .background(
                        if (isAddRowFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = if (isAddRowFocused) 2.dp else 0.dp,
                        color = if (isAddRowFocused) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.plugin_screen_add_repo),
                    style = ArflixTypography.button,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // TV Installed Repositories
            if (repositories.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.plugin_screen_installed_repos).uppercase(),
                    style = ArflixTypography.caption.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                repositories.forEachIndexed { idx, repo ->
                    val slotIndex = 1 + idx
                    val isFocused = (focusedIndex == slotIndex)
                    val isRefreshFocused = isFocused && (focusedActionIndex == 0)
                    val isDeleteFocused = isFocused && (focusedActionIndex == 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsFocusSlot(slotIndex)
                            .focusProperties { canFocus = false }
                            .clickable {
                                viewModel.onEvent(PluginUiEvent.RefreshRepository(repo.id))
                            }
                            .background(
                                if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ScraperLogo(
                                logoUrl = repo.iconUrl,
                                name = repo.name,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = repo.name,
                                    style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = repo.url,
                                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { viewModel.onEvent(PluginUiEvent.RefreshRepository(repo.id)) }
                                    .background(
                                        if (isRefreshFocused) accentColor else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isRefreshFocused) 2.dp else 0.dp,
                                        color = if (isRefreshFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh_addons),
                                    tint = if (isRefreshFocused) Color.Black else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { repoToDelete = repo }
                                    .background(
                                        if (isDeleteFocused) Color(0xFFDC2626) else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isDeleteFocused) 2.dp else 0.dp,
                                        color = if (isDeleteFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = if (isDeleteFocused) Color.White else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // TV Installed Scrapers
            Text(
                text = stringResource(R.string.plugin_screen_installed_scrapers).uppercase(),
                style = ArflixTypography.caption.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val scraperStartIdx = 1 + repositories.size
            if (scrapers.isEmpty()) {
                val slotIndex = scraperStartIdx
                Text(
                    text = stringResource(R.string.plugin_screen_no_scrapers),
                    style = ArflixTypography.body,
                    color = TextSecondary,
                    modifier = Modifier.settingsFocusSlot(slotIndex)
                )
            } else {
                scrapers.forEachIndexed { idx, scraper ->
                    val slotIndex = scraperStartIdx + idx
                    val isFocused = (focusedIndex == slotIndex)
                    val hasSettings = scraper.id in uiState.scrapersWithSettings
                    val isToggleFocused = isFocused && (focusedActionIndex == 0 || !hasSettings)
                    val isSettingsFocused = isFocused && hasSettings && (focusedActionIndex == 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsFocusSlot(slotIndex)
                            .focusProperties { canFocus = false }
                            .clickable {
                                viewModel.onEvent(
                                    PluginUiEvent.ToggleScraper(scraper.id, !scraper.enabled)
                                )
                            }
                            .background(
                                if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ScraperLogo(
                                logoUrl = scraper.logo,
                                name = scraper.name,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = scraper.name,
                                    style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (scraper.description.isNotBlank()) scraper.description else scraper.id,
                                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Toggle switch
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = if (isToggleFocused) 2.dp else 0.dp,
                                        color = if (isToggleFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(24.dp)
                                        .background(
                                            color = if (scraper.enabled) SuccessGreen else Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(13.dp)
                                        )
                                        .padding(3.dp),
                                    contentAlignment = if (scraper.enabled) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(color = Color.White, shape = RoundedCornerShape(10.dp))
                                    )
                                }
                            }

                            if (hasSettings) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable {
                                            if (activity != null) {
                                                viewModel.onEvent(
                                                    PluginUiEvent.OpenPluginSettings(scraper.id, activity)
                                                )
                                            }
                                        }
                                        .background(
                                            if (isSettingsFocused) accentColor else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = if (isSettingsFocused) 2.dp else 0.dp,
                                            color = if (isSettingsFocused) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.plugin_settings),
                                        tint = if (isSettingsFocused) Color.Black else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TV Reset Button
            val resetIndex = totalItems - 1
            val isResetRowFocused = (focusedIndex == resetIndex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsFocusSlot(resetIndex)
                    .focusProperties { canFocus = false }
                    .clickable { showResetDialog = true }
                    .background(
                        if (isResetRowFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = if (isResetRowFocused) 2.dp else 0.dp,
                        color = if (isResetRowFocused) Color.Red else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.plugin_screen_reset_title),
                    style = ArflixTypography.button,
                    color = Color.Red
                )
            }
        }
    }

    // Modern ARVIO Input Modal (identical to Add Addon / Add Catalog)
    if (showAddDialog) {
        PluginInputModal(
            title = stringResource(R.string.plugin_screen_add_repo_dialog_title),
            fields = listOf(
                PluginInputField(
                    label = stringResource(R.string.settings_label_url),
                    value = customRepoUrl,
                    placeholder = stringResource(R.string.plugin_screen_repo_url_hint),
                    onValueChange = { customRepoUrl = it }
                )
            ),
            confirmText = tr("Confirm"),
            onConfirm = {
                if (customRepoUrl.isNotBlank()) {
                    viewModel.onEvent(PluginUiEvent.AddRepository(customRepoUrl.trim()))
                    customRepoUrl = ""
                    showAddDialog = false
                }
            },
            onDismiss = {
                customRepoUrl = ""
                showAddDialog = false
            }
        )
    }

    if (showResetDialog) {
        WarningDialog(
            title = stringResource(R.string.plugin_screen_reset_title),
            message = stringResource(R.string.plugin_screen_reset_confirm),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                viewModel.onEvent(PluginUiEvent.ResetAllPlugins)
                onFocusedIndexChanged(0)
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    repoToDelete?.let { repo ->
        WarningDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.plugin_screen_delete_repo_confirm, repo.name),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                viewModel.onEvent(PluginUiEvent.RemoveRepository(repo.id))
                repoToDelete = null
            },
            onDismiss = { repoToDelete = null }
        )
    }
}

@Composable
private fun ModalScrim(
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = contentInteraction,
                indication = null,
                onClick = {}
            ),
            content = content
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginInputModal(
    title: String,
    supportingText: String? = null,
    fields: List<PluginInputField>,
    confirmText: String = stringResource(R.string.add),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var focusedIndex by remember(title, fields.size) { mutableIntStateOf(0) }
    var lastFocusedFieldIndex by remember(title, fields.size) { mutableStateOf<Int?>(null) }
    val totalItems = fields.size + 3 // inputs + paste + cancel + confirm
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxDialogHeight = (screenHeightDp * 0.88f).coerceAtMost(if (isTouchDevice) 620.dp else 660.dp)

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val modalFocusRequester = remember { FocusRequester() }
    val formScrollState = rememberScrollState()

    val editTextRefs = remember { MutableList<EditText?>(fields.size) { null } }

    fun anyEditTextFocused(): Boolean = editTextRefs.any { it?.hasFocus() == true }

    fun pasteTargetIndex(): Int {
        if (focusedIndex in 0 until fields.size) return focusedIndex
        lastFocusedFieldIndex?.takeIf { it in 0 until fields.size }?.let { return it }
        return fields.indexOfFirst { field ->
            field.label.contains("url", ignoreCase = true) ||
                field.label.contains("server", ignoreCase = true) ||
                field.label.contains("host", ignoreCase = true)
        }.takeIf { it >= 0 } ?: if (fields.size > 1) 1 else 0
    }

    fun pasteClipboardIntoTarget() {
        val clipboardText = clipboardManager.getText()?.text ?: return
        val targetIndex = pasteTargetIndex()
        val target = fields.getOrNull(targetIndex) ?: return
        target.onValueChange(clipboardText)
        editTextRefs.getOrNull(targetIndex)?.let { edit ->
            edit.setText(clipboardText)
            edit.setSelection(edit.text?.length ?: 0)
            edit.clearFocus()
        }
        modalFocusRequester.requestFocus()
    }

    fun hideKeyboardAll() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        editTextRefs.forEach { edit ->
            if (edit != null) {
                imm?.hideSoftInputFromWindow(edit.windowToken, 0)
                edit.clearFocus()
                runCatching { imm?.restartInput(edit) }
            }
        }
        view.requestFocus()
    }

    fun showKeyboardFor(index: Int) {
        val edit = editTextRefs.getOrNull(index) ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        edit.post {
            edit.requestFocus()
            val shown = imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) ?: false
            if (!shown) imm?.showSoftInput(edit, InputMethodManager.SHOW_FORCED)
        }
    }

    LaunchedEffect(title, fields.size) {
        modalFocusRequester.requestFocus()
    }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex in 0 until fields.size) {
            lastFocusedFieldIndex = focusedIndex
            val maxScroll = formScrollState.maxValue
            if (maxScroll > 0 && fields.size > 1) {
                val targetScroll = (focusedIndex.toFloat() / (fields.size - 1) * maxScroll).toInt()
                runCatching { formScrollState.animateScrollTo(targetScroll) }
            }
        } else if (focusedIndex >= fields.size) {
            runCatching { formScrollState.animateScrollTo(formScrollState.maxValue) }
        }
    }

    Dialog(
        onDismissRequest = {
            hideKeyboardAll()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            hideKeyboardAll()
            onDismiss()
        }
        ModalScrim(
            onDismiss = {
                hideKeyboardAll()
                onDismiss()
            }
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (isTouchDevice) Modifier.fillMaxWidth(0.86f).widthIn(max = 520.dp)
                        else Modifier.width(520.dp)
                    )
                    .navigationBarsPadding()
                    .imePadding()
                    .heightIn(max = maxDialogHeight)
                    .background(BackgroundElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = if (isTouchDevice) 18.dp else 20.dp, vertical = 18.dp)
                    .focusRequester(modalFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    if (anyEditTextFocused()) {
                                        hideKeyboardAll()
                                    } else {
                                        hideKeyboardAll()
                                        onDismiss()
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (focusedIndex > 0) {
                                        if (focusedIndex < fields.size) hideKeyboardAll()
                                        focusedIndex--
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (focusedIndex < totalItems - 1) {
                                        if (focusedIndex < fields.size) hideKeyboardAll()
                                        focusedIndex++
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex == fields.size + 2) focusedIndex = fields.size + 1
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex == fields.size + 1) focusedIndex = fields.size + 2
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    when {
                                        focusedIndex in 0 until fields.size -> {
                                            showKeyboardFor(focusedIndex)
                                            true
                                        }
                                        focusedIndex == fields.size -> {
                                            pasteClipboardIntoTarget()
                                            true
                                        }
                                        focusedIndex == fields.size + 1 -> {
                                            hideKeyboardAll()
                                            onDismiss()
                                            true
                                        }
                                        focusedIndex == fields.size + 2 -> {
                                            hideKeyboardAll()
                                            onConfirm()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                else -> false
                            }
                        } else false
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = ArflixTypography.sectionTitle,
                    color = TextPrimary
                )
                Text(
                    text = if (isTouchDevice) {
                        stringResource(R.string.settings_modal_hint_touch)
                    } else {
                        stringResource(R.string.settings_modal_hint_tv)
                    },
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp, bottom = if (supportingText == null) 12.dp else 4.dp)
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = ArflixTypography.caption,
                        color = TextSecondary.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(formScrollState)
                ) {
                    fields.forEachIndexed { index, field ->
                        val isFocused = focusedIndex == index

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            if (isFocused) Pink.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(11.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isFocused) Pink else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(11.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = ArflixTypography.caption,
                                        color = if (isFocused) Pink else TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = field.label,
                                    style = ArflixTypography.caption,
                                    color = if (isFocused) Pink else TextSecondary
                                )
                            }
                            if (field.helper.isNotBlank()) {
                                Text(
                                    text = field.helper,
                                    style = ArflixTypography.caption.copy(fontSize = 12.sp),
                                    color = TextSecondary.copy(alpha = 0.68f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 30.dp, bottom = 6.dp)
                                )
                            }

                            val regexFieldFocusColor = resolveAccentColor(fallback = Pink)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = if (isFocused) 0.12f else 0.05f), RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) regexFieldFocusColor else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        EditText(ctx).apply {
                                            editTextRefs[index] = this
                                            setText(field.value)
                                            setTextColor(android.graphics.Color.WHITE)
                                            setHintTextColor(android.graphics.Color.GRAY)
                                            hint = field.placeholder.ifBlank { "Enter ${field.label.lowercase()}..." }
                                            textSize = 16f
                                            background = null
                                            setPadding(20, 14, 20, 14)
                                            isSingleLine = field.singleLine
                                            setHorizontallyScrolling(field.singleLine)
                                            minLines = if (field.singleLine) 1 else 3
                                            maxLines = if (field.singleLine) 1 else 5
                                            isFocusable = true
                                            isFocusableInTouchMode = true

                                            val isPasswordField = field.isSecret || field.label.contains("password", ignoreCase = true)
                                            val isLikelyUrlField =
                                                field.label.contains("url", ignoreCase = true) ||
                                                    field.label.contains("m3u", ignoreCase = true) ||
                                                    field.label.contains("epg", ignoreCase = true) ||
                                                    field.label.contains("server", ignoreCase = true)
                                            inputType = if (isPasswordField) {
                                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                                            } else if (isLikelyUrlField) {
                                                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) or
                                                    if (field.singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                            } else {
                                                InputType.TYPE_CLASS_TEXT or
                                                    if (field.singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                            }
                                            if (isPasswordField) {
                                                transformationMethod = PasswordTransformationMethod.getInstance()
                                            }

                                            doAfterTextChanged { editable ->
                                                field.onValueChange(editable?.toString() ?: "")
                                            }

                                            setOnFocusChangeListener { _, hasFocus ->
                                                if (hasFocus && focusedIndex != index) {
                                                    focusedIndex = index
                                                }
                                                if (hasFocus) {
                                                    lastFocusedFieldIndex = index
                                                }
                                            }

                                            setOnKeyListener { _, keyCode, event ->
                                                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    when (keyCode) {
                                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                            imm?.hideSoftInputFromWindow(windowToken, 0)
                                                            clearFocus()
                                                            focusedIndex = (index + 1).coerceAtMost(totalItems - 1)
                                                            modalFocusRequester.requestFocus()
                                                            true
                                                        }
                                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                            if (index > 0) {
                                                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                                imm?.hideSoftInputFromWindow(windowToken, 0)
                                                                clearFocus()
                                                                focusedIndex = index - 1
                                                                modalFocusRequester.requestFocus()
                                                            }
                                                            true
                                                        }
                                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                            imm?.hideSoftInputFromWindow(windowToken, 0)
                                                            clearFocus()
                                                            modalFocusRequester.requestFocus()
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }

                                            setOnEditorActionListener { _, actionId, _ ->
                                                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                    imm?.hideSoftInputFromWindow(windowToken, 0)
                                                    clearFocus()
                                                    modalFocusRequester.requestFocus()
                                                    true
                                                } else false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    update = { editText ->
                                        val current = editText.text?.toString().orEmpty()
                                        if (current != field.value) {
                                            editText.setText(field.value)
                                            editText.setSelection(field.value.length)
                                        }
                                    }
                                )
                            }

                            if (index < fields.size - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val isPasteFocused = focusedIndex == fields.size
                val fieldFallbackLabel = stringResource(R.string.settings_field_fallback)
                val pasteTargetLabel = fields.getOrNull(pasteTargetIndex())
                    ?.label
                    ?.substringBefore("(")
                    ?.trim()
                    ?.ifBlank { fieldFallbackLabel }
                    ?: fieldFallbackLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color = if (isPasteFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPasteFocused) Color.White else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            pasteClipboardIntoTarget()
                        }
                        .padding(vertical = 11.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.settings_cd_paste),
                        tint = if (isPasteFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_paste_into, pasteTargetLabel),
                        style = ArflixTypography.button,
                        color = if (isPasteFocused) Color.Black else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isCancelFocused = focusedIndex == fields.size + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isCancelFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCancelFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hideKeyboardAll()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Cancel"),
                            style = ArflixTypography.button,
                            color = if (isCancelFocused) Color.Black else Color.White
                        )
                    }

                    val isConfirmFocused = focusedIndex == fields.size + 2
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isConfirmFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isConfirmFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hideKeyboardAll()
                                onConfirm()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            style = ArflixTypography.button,
                            color = if (isConfirmFocused) Color.Black else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isTouchDevice) stringResource(R.string.settings_modal_footer_touch) else stringResource(R.string.settings_modal_footer_tv),
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.56f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WarningDialog(
    title: String,
    message: String,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) } // 0 = Cancel (Left), 1 = Confirm (Right)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .then(
                        if (isTouchDevice) Modifier.fillMaxWidth(0.86f).widthIn(max = 400.dp)
                        else Modifier.width(400.dp)
                    )
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(if (isTouchDevice) 20.dp else 28.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex > 0) focusedIndex = 0
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex < 1) focusedIndex = 1
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (focusedIndex == 0) onDismiss() else onConfirm()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = title,
                    style = ArflixTypography.sectionTitle,
                    color = Color(0xFFDC2626)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = ArflixTypography.body,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isCancelFocused = (focusedIndex == 0)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isCancelFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCancelFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cancelText,
                            style = ArflixTypography.button,
                            color = if (isCancelFocused) Color.White else TextSecondary
                        )
                    }

                    val isConfirmFocused = (focusedIndex == 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isConfirmFocused) Color(0xFFDC2626) else Color(0xFFDC2626).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isConfirmFocused) Color(0xFFEF4444) else Color(0xFFDC2626).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onConfirm() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            style = ArflixTypography.button,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
