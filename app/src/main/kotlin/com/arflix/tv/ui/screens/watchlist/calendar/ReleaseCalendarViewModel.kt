package com.arflix.tv.ui.screens.watchlist.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.CalendarRelease
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.data.repository.CalendarWatchlists
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
    private val profileManager: ProfileManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReleaseCalendarUiState())
    val uiState: StateFlow<ReleaseCalendarUiState> = _uiState.asStateFlow()
    private var sourceSnapshot: CalendarWatchlists? = null
    private var sourceLoadedAt = 0L
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeProfileId: String? = null
    private var contentLanguage = "en-US"

    init {
        viewModelScope.launch {
            profileManager.activeProfileId.distinctUntilChanged().collectLatest { profileId ->
                activeProfileId = profileId
                sourceSnapshot = null
                sourceLoadedAt = 0L
                loadJob?.cancel()
                requestId++
                _uiState.update { old -> ReleaseCalendarUiState(month = old.month, selectedDate = old.selectedDate) }
                // Observe only this profile's connection identity. Tokens are never sent to UI or logs.
                val credentialKeys = setOf("trakt_access_token", "simkl_access_token", "mdblist_api_key", "mdblist_access_token")
                    .map { "profile_${profileId}_$it" }.toSet()
                val connections = context.traktDataStore.data.map { preferences ->
                    preferences.asMap().entries.filter { it.key.name in credentialKeys }
                        .map { it.key.name to it.value.hashCode() }.sortedBy { it.first }
                }.distinctUntilChanged()
                val language = context.settingsDataStore.data.map { resolveAppLanguage(it, profileId) }.distinctUntilChanged()
                combine(watchlistRepository.watchlistItems, connections, language) { items, connectionsKey, languageTag ->
                    Triple(items, connectionsKey, languageTag)
                }.distinctUntilChanged().collectLatest { (_, _, languageTag) ->
                    contentLanguage = languageTag
                    sourceSnapshot = null
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
        repository.invalidateMetadata()
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
        val items = sourceSnapshot?.items.orEmpty()
        return if (id == ReleaseCalendarSource.ALL.id) mergeCalendarWatchlists(items).size
        else items.entries.firstOrNull { it.key.id == id }?.value.orEmpty().distinctBy { it.mediaType to it.id }.size
    }

    private fun reload(forceSources: Boolean = false, forceRefresh: Boolean = false) {
        val profileId = activeProfileId ?: return
        val sequence = ++requestId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val current = _uiState.value
                val snapshot = sourceSnapshot?.takeIf {
                    !forceSources && it.profileId == profileId && System.currentTimeMillis() - sourceLoadedAt < 2 * 60_000L
                } ?: repository.loadWatchlists(profileId, forceRefresh).also {
                    if (sequence == requestId && profileManager.getProfileIdSync() == profileId) {
                        sourceSnapshot = it
                        sourceLoadedAt = System.currentTimeMillis()
                    }
                }
                if (sequence != requestId || profileManager.getProfileIdSync() != profileId) return@launch
                val sources = listOf(ReleaseCalendarSource.ALL, ReleaseCalendarSource.ARVIO) +
                    snapshot.items.keys.filter { it != ReleaseCalendarSource.ARVIO }
                _uiState.update { state ->
                    val selectedId = state.selectedSourceId.takeIf { id -> sources.any { it.id == id } }
                        ?: ReleaseCalendarSource.ALL.id
                    state.copy(sources = sources.distinct(), selectedSourceId = selectedId, watchlistCount = countForSource(selectedId))
                }
                val result = repository.loadMonth(snapshot, current.month, current.timezone, current.region, contentLanguage) { partial ->
                    if (sequence == requestId && profileManager.getProfileIdSync() == profileId) {
                        _uiState.update { it.copy(entries = partial.entries, warnings = partial.warnings) }
                    }
                }
                if (sequence != requestId || profileManager.getProfileIdSync() != profileId) return@launch
                _uiState.update { it.copy(entries = result.entries, warnings = result.warnings, isLoading = false) }
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
