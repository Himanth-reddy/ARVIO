package com.arflix.tv.ui.screens.watchlist.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.CalendarRelease
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.data.repository.CalendarWatchlists
import com.arflix.tv.data.repository.CalendarMonthPreview
import com.arflix.tv.data.repository.CalendarLoadProgress
import com.arflix.tv.data.repository.ReleaseCalendarCache
import com.arflix.tv.data.repository.calendarCacheDigest
import com.arflix.tv.data.repository.mergeCalendarPreview
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.ReleaseCalendarRepository
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.data.repository.mergeCalendarWatchlists
import com.arflix.tv.util.traktDataStore
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.resolveAppLanguage
import com.arflix.tv.util.ContentRating
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseCalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val sources: List<ReleaseCalendarSource> = listOf(ReleaseCalendarSource.ALL, ReleaseCalendarSource.ARVIO),
    val selectedSourceId: String = ReleaseCalendarSource.ALL.id,
    /** Complete month result. Use visibleEntries for the selected source. */
    val entries: List<CalendarRelease> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val timezone: ZoneId = ZoneId.systemDefault(),
    val warnings: List<String> = emptyList(),
    val watchlistCount: Int = 0,
    val region: String = Locale.getDefault().country.ifBlank { "US" }
) {
    val visibleEntries: List<CalendarRelease> get() = entries.filter {
        selectedSourceId == ReleaseCalendarSource.ALL.id || selectedSourceId in it.sourceIds
    }
    val releasesByDate: Map<LocalDate, List<CalendarRelease>> get() = visibleEntries.groupBy { it.date }
    val selectedDayReleases: List<CalendarRelease> get() = visibleEntries.filter { it.date == selectedDate }
    val selectedSource: ReleaseCalendarSource get() = sources.firstOrNull { it.id == selectedSourceId }
        ?: ReleaseCalendarSource.ALL
}

@HiltViewModel
class ReleaseCalendarViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ReleaseCalendarRepository,
    private val watchlistRepository: WatchlistRepository,
    private val profileManager: ProfileManager,
    private val cache: ReleaseCalendarCache
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReleaseCalendarUiState())
    val uiState: StateFlow<ReleaseCalendarUiState> = _uiState.asStateFlow()
    private var sourceSnapshot: CalendarWatchlists? = null
    private var sourceLoadedAt = 0L
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeProfileId: String? = null
    private var contentLanguage = "en-US"
    private var privateCacheIdentity = ""
    private var previewSourceCounts = emptyMap<String, Int>()

    init {
        viewModelScope.launch {
            profileManager.activeProfileId.distinctUntilChanged().collectLatest { profileId ->
                activeProfileId = profileId
                privateCacheIdentity = ""
                sourceSnapshot = null
                sourceLoadedAt = 0L
                loadJob?.cancel()
                requestId++
                previewSourceCounts = emptyMap()
                _uiState.update { old -> ReleaseCalendarUiState(month = old.month, selectedDate = old.selectedDate) }
                // Populate the local StateFlow before deriving a private preview key. Its initial
                // empty value must not briefly resurrect an older, now-removed watchlist.
                watchlistRepository.getLocalWatchlistItems()
                // Observe only this profile's connection identity. Tokens are never sent to UI or logs.
                val credentialKeys = setOf("trakt_access_token", "simkl_access_token", "mdblist_api_key", "mdblist_access_token")
                    .map { "profile_${profileId}_$it" }.toSet()
                val connections = context.traktDataStore.data.map { preferences ->
                    preferences.asMap().entries.filter { it.key.name in credentialKeys }
                        .map { it.key.name to it.value.toString() }.sortedBy { it.first }
                        .let { calendarCacheDigest(it.toString()) }
                }.distinctUntilChanged()
                val language = context.settingsDataStore.data.map { resolveAppLanguage(it, profileId) }.distinctUntilChanged()
                combine(watchlistRepository.watchlistItems, connections, language) { items, connectionsKey, languageTag ->
                    // Artwork/progress enrichment does not change calendar membership and must
                    // not cancel and restart every title request while the list fills in.
                    Triple(items.map { "${it.mediaType}:${it.id}" }.distinct().sorted(), connectionsKey, languageTag)
                }.distinctUntilChanged().collectLatest { (items, connectionsKey, languageTag) ->
                    contentLanguage = languageTag
                    privateCacheIdentity = calendarCacheDigest("$profileId|$connectionsKey|$items|$languageTag")
                    sourceSnapshot = null
                    previewSourceCounts = emptyMap()
                    _uiState.update { it.copy(entries = emptyList(), warnings = emptyList(), region = ContentRating.regionOf(languageTag)) }
                    reload(forceSources = true)
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val changedMonth = YearMonth.from(date) != _uiState.value.month
        _uiState.update { it.copy(selectedDate = date, month = YearMonth.from(date),
            entries = if (changedMonth) emptyList() else it.entries,
            warnings = if (changedMonth) emptyList() else it.warnings) }
        if (changedMonth) reload()
    }

    fun changeMonth(offset: Long) = selectMonth(_uiState.value.month.plusMonths(offset))

    fun selectMonth(month: YearMonth) {
        if (month == _uiState.value.month) return
        _uiState.update { state ->
            state.copy(month = month, selectedDate = month.atDay(state.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())),
                entries = emptyList(), warnings = emptyList(), error = null)
        }
        reload()
    }

    fun selectSource(id: String) {
        if (_uiState.value.sources.none { it.id == id }) return
        _uiState.update { it.copy(selectedSourceId = id, watchlistCount = countForSource(id)) }
    }

    fun refresh() {
        sourceSnapshot = null
        _uiState.update { it.copy(timezone = ZoneId.systemDefault()) }
        reload(forceSources = true, forceRefresh = true)
    }

    /** Safe to call on tab entry and ON_RESUME; fresh/in-flight results are reused. */
    fun onVisible() {
        val timezone = ZoneId.systemDefault()
        val timezoneChanged = timezone != _uiState.value.timezone
        if (timezoneChanged) _uiState.update { it.copy(timezone = timezone, entries = emptyList()) }
        if (!timezoneChanged && loadJob?.isActive == true) return
        if (timezoneChanged || sourceSnapshot == null || System.currentTimeMillis() - sourceLoadedAt >= 2 * 60_000L) {
            reload()
        }
    }

    private fun countForSource(id: String): Int {
        val items = sourceSnapshot?.items ?: return previewSourceCounts[id] ?: 0
        return if (id == ReleaseCalendarSource.ALL.id) mergeCalendarWatchlists(items).size
        else items.entries.firstOrNull { it.key.id == id }?.value.orEmpty().distinctBy { it.mediaType to it.id }.size
    }

    private fun updateSources(snapshot: CalendarWatchlists, complete: Boolean) {
        sourceSnapshot = snapshot
        // A partial source result must never be reused as a complete private snapshot.
        if (!complete) sourceLoadedAt = 0L
        else if (sourceLoadedAt == 0L) sourceLoadedAt = System.currentTimeMillis()
        _uiState.update { state ->
            val available = listOf(ReleaseCalendarSource.ALL, ReleaseCalendarSource.ARVIO) + snapshot.items.keys
            val sources = (available + if (complete) emptyList() else state.sources).distinct()
            val selectedId = state.selectedSourceId.takeIf { id -> sources.any { it.id == id } }
                ?: ReleaseCalendarSource.ALL.id
            state.copy(sources = sources, selectedSourceId = selectedId, watchlistCount = countForSource(selectedId))
        }
    }

    private fun reload(forceSources: Boolean = false, forceRefresh: Boolean = false) {
        val profileId = activeProfileId ?: return
        if (privateCacheIdentity.isBlank()) return
        val sequence = ++requestId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val current = _uiState.value
                val language = contentLanguage
                val cacheKey = "$privateCacheIdentity|${current.month}|${current.timezone.id}|${current.region}"
                val preview = if (!forceRefresh) cache.readMonth(cacheKey) else null
                var lastPreviewWrite = 0L
                var latestProgress: CalendarLoadProgress? = null
                if (sequence != requestId || profileManager.getProfileIdSync() != profileId) return@launch
                if (preview != null) {
                    previewSourceCounts = preview.sourceCounts
                    _uiState.update { state -> state.copy(
                        entries = preview.entries,
                        sources = ReleaseCalendarSource.entries.filter { it.id in preview.sourceCounts || it == ReleaseCalendarSource.ALL || it == ReleaseCalendarSource.ARVIO },
                        watchlistCount = preview.sourceCounts[state.selectedSourceId] ?: 0
                    ) }
                }
                if (forceRefresh) repository.invalidateMetadata()
                val snapshot = sourceSnapshot?.takeIf {
                    !forceSources && it.profileId == profileId && System.currentTimeMillis() - sourceLoadedAt < 2 * 60_000L
                }
                val result = if (snapshot != null) {
                    updateSources(snapshot, complete = true)
                    repository.loadMonth(snapshot, current.month, current.timezone, current.region, language) { partial ->
                        if (sequence == requestId && profileManager.getProfileIdSync() == profileId) {
                            // Cached source snapshots need the same failure/removal reconciliation
                            // as fresh providers, including when changing back to an earlier month.
                            val progress = CalendarLoadProgress(snapshot, partial, true, partial.completedTitles)
                            latestProgress = progress
                            _uiState.update { it.copy(entries = mergeCalendarPreview(preview?.entries.orEmpty(), progress), warnings = partial.warnings) }
                        }
                    }.also { completed -> latestProgress = CalendarLoadProgress(snapshot, completed, true, completed.completedTitles) }
                } else {
                    repository.loadCalendar(profileId, current.month, current.timezone, current.region, language, forceRefresh) { progress ->
                        if (sequence == requestId && profileManager.getProfileIdSync() == profileId) {
                            latestProgress = progress
                            updateSources(progress.watchlists, progress.watchlistsComplete)
                            _uiState.update { it.copy(entries = mergeCalendarPreview(preview?.entries.orEmpty(), progress), warnings = progress.month.warnings) }
                            // The first usable dates also survive leaving the app while a slower
                            // provider is still busy. Never replace a full preview with a partial one.
                            val now = System.currentTimeMillis()
                            if (preview == null && progress.month.entries.isNotEmpty() && now - lastPreviewWrite >= 2_000L) {
                                lastPreviewWrite = now
                                cache.writeMonth(cacheKey, CalendarMonthPreview(progress.month.entries,
                                    _uiState.value.sources.associate { it.id to countForSource(it.id) }))
                            }
                        }
                    }
                }
                if (sequence != requestId || profileManager.getProfileIdSync() != profileId) return@launch
                val finalEntries = latestProgress?.let { mergeCalendarPreview(preview?.entries.orEmpty(), it) } ?: result.entries
                _uiState.update { it.copy(entries = finalEntries, warnings = result.warnings, isLoading = false) }
                // Successful removals are persisted even if another provider failed. Retained
                // fallback dates keep the original expiry, so outages cannot preserve them forever.
                val failedSources = latestProgress?.watchlists?.failedSources.orEmpty().map { it.id }.toSet()
                val counts = _uiState.value.sources.associate { source -> source.id to
                    (if (source.id in failedSources) preview?.sourceCounts?.get(source.id) ?: countForSource(source.id) else countForSource(source.id)) }
                val refreshedAt = if (result.warnings.isNotEmpty() && preview != null) preview.refreshedAt else System.currentTimeMillis()
                cache.writeMonth(cacheKey, CalendarMonthPreview(finalEntries, counts, refreshedAt))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (sequence == requestId && profileManager.getProfileIdSync() == profileId) {
                    _uiState.update { it.copy(isLoading = false, error = "Release calendar could not be loaded. Please try again.") }
                }
            }
        }
    }
}
