package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TraktApi
import com.arflix.tv.data.api.TraktCalendarEpisode
import com.arflix.tv.data.model.CalendarRelease
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.data.repository.simkl.SimklAuthManager
import com.arflix.tv.data.repository.simkl.SimklSyncService
import com.arflix.tv.util.Constants
import java.io.IOException
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal data class CalendarWatchlists(
    val profileId: String,
    val items: Map<ReleaseCalendarSource, List<MediaItem>>,
    val warnings: List<String> = emptyList()
)

internal data class CalendarMonthResult(val entries: List<CalendarRelease>, val warnings: List<String>)

internal data class CalendarLoadProgress(
    val watchlists: CalendarWatchlists,
    val month: CalendarMonthResult,
    val watchlistsComplete: Boolean
)

/** Read-only calendar metadata. Public title metadata is cached; private lists stay in the ViewModel. */
@Singleton
class ReleaseCalendarRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val traktApi: TraktApi,
    private val watchlistRepository: WatchlistRepository,
    private val traktRepository: TraktRepository,
    private val simklAuthManager: SimklAuthManager,
    private val simklSyncService: SimklSyncService,
    private val mdbListRepository: MdbListRepository,
    private val mediaRepository: MediaRepository,
    private val profileManager: ProfileManager
) {
    private val permits = Semaphore(5)
    // Optional times/logos must not occupy the slots used to find confirmed dates.
    private val timePermits = Semaphore(2)
    private val artworkPermits = Semaphore(2)
    private data class Cached(val value: Any, val at: Long)
    private val metadataCache = linkedMapOf<String, Cached>()

    internal suspend fun loadWatchlists(
        profileId: String,
        forceRefresh: Boolean = false,
        onProgress: suspend (CalendarWatchlists) -> Unit = {}
    ): CalendarWatchlists = coroutineScope {
        ensureProfile(profileId)
        val providers = listOf(ReleaseCalendarSource.ARVIO, ReleaseCalendarSource.TRAKT,
            ReleaseCalendarSource.SIMKL, ReleaseCalendarSource.MDBLIST)
        val completed = linkedMapOf<ReleaseCalendarSource, SourceRead>()
        val progressMutex = Mutex()
        fun snapshot() = CalendarWatchlists(
            profileId,
            providers.mapNotNull { source -> completed[source]?.items?.let { source to it } }.toMap(),
            providers.mapNotNull { completed[it]?.warning }
        )
        providers.map { source -> async {
            val result = try {
                withTimeoutOrNull(45_000) {
                    var warning: String? = null
                    when (source) {
                        ReleaseCalendarSource.ARVIO -> watchlistRepository.getLocalWatchlistItems()
                        ReleaseCalendarSource.TRAKT -> if (traktRepository.isAuthenticated.first()) {
                            traktRepository.getWatchlistSyncResultWithAuthState().second?.items
                                ?: throw IOException("Trakt watchlist unavailable")
                        } else null
                        ReleaseCalendarSource.SIMKL -> if (simklAuthManager.isConnected()) {
                            val refreshed = simklSyncService.syncIfNeeded(forceRefresh)
                            // The sync status also includes playback. A playback outage must
                            // not discard a usable library snapshot needed by the calendar.
                            if (!refreshed) warning = "SIMKL could not be fully refreshed. Showing available titles."
                            simklSyncService.getWatchlistItems() + simklSyncService.getLibraryItems("watching")
                        } else null
                        ReleaseCalendarSource.MDBLIST -> {
                            val result = mdbListRepository.getWatchlist()
                            if (result.connected) result.items ?: throw IOException("MDBList watchlist unavailable") else null
                        }
                        ReleaseCalendarSource.ALL -> null
                    }.let { SourceRead(it, warning) }
                } ?: throw IOException("Watchlist request timed out")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SourceRead(emptyList(), "${source.label} could not be refreshed.")
            }
            progressMutex.withLock {
                ensureProfile(profileId)
                completed[source] = result
                onProgress(snapshot())
            }
        } }.awaitAll()
        ensureProfile(profileId)
        snapshot()
    }

    private data class SourceRead(val items: List<MediaItem>?, val warning: String? = null)

    /** Start each title once as its provider arrives; a slow provider cannot hold up another one. */
    internal suspend fun loadCalendar(
        profileId: String,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String = "en-US",
        forceRefresh: Boolean = false,
        onProgress: suspend (CalendarLoadProgress) -> Unit = {}
    ): CalendarMonthResult = coroutineScope {
        ensureProfile(profileId)
        val progressMutex = Mutex()
        var watchlists = CalendarWatchlists(profileId, emptyMap())
        var watchlistsComplete = false
        val titles = linkedMapOf<Pair<MediaType, Int>, CalendarWatchlistTitle>()
        val results = linkedMapOf<Pair<MediaType, Int>, CalendarMonthResult>()
        val jobs = mutableListOf<Job>()
        fun currentResult() = combineResults(results.map { (key, result) ->
            result.copy(entries = result.entries.map { it.copy(sourceIds = titles.getValue(key).sourceIds) })
        }, watchlists.warnings)
        suspend fun publish() {
            ensureProfile(profileId)
            onProgress(CalendarLoadProgress(watchlists, currentResult(), watchlistsComplete))
        }
        loadWatchlists(profileId, forceRefresh) { snapshot ->
            progressMutex.withLock {
                ensureProfile(profileId)
                watchlists = snapshot
                mergeCalendarWatchlists(snapshot.items).forEach { title ->
                    val key = title.media.mediaType to title.media.id
                    val isNew = key !in titles
                    titles[key] = title
                    if (isNew) jobs += launch {
                        titleReleases(title, month, timezone, region, language) { result ->
                            progressMutex.withLock {
                                results[key] = result
                                publish()
                            }
                        }
                    }
                }
                // Existing releases immediately acquire any newly discovered source membership.
                publish()
            }
        }
        progressMutex.withLock {
            watchlistsComplete = true
            publish()
        }
        jobs.joinAll()
        ensureProfile(profileId)
        currentResult()
    }

    internal suspend fun loadMonth(
        watchlists: CalendarWatchlists,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String = "en-US",
        onProgress: suspend (CalendarMonthResult) -> Unit = {}
    ): CalendarMonthResult = coroutineScope {
        ensureProfile(watchlists.profileId)
        val progressMutex = Mutex()
        val partial = linkedMapOf<Pair<MediaType, Int>, CalendarMonthResult>()
        suspend fun publish(title: CalendarWatchlistTitle, result: CalendarMonthResult) {
            progressMutex.withLock {
                ensureProfile(watchlists.profileId)
                partial[title.media.mediaType to title.media.id] = result
                onProgress(combineResults(partial.values, watchlists.warnings))
            }
        }
        val results = mergeCalendarWatchlists(watchlists.items).map { title -> async {
            titleReleases(title, month, timezone, region, language) { publish(title, it) }
        } }.awaitAll()
        ensureProfile(watchlists.profileId)
        combineResults(results, watchlists.warnings)
    }

    private suspend fun titleReleases(
        title: CalendarWatchlistTitle,
        month: YearMonth,
        timezone: ZoneId,
        region: String,
        language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult {
        var lastPublished: CalendarMonthResult? = null
        suspend fun publish(result: CalendarMonthResult) {
            if (result != lastPublished) {
                lastPublished = result
                onProgress(result)
            }
        }
        return try {
            val result = if (title.media.mediaType == MediaType.TV) {
                televisionReleases(title, month, timezone, language, ::publish)
            } else {
                val details = cached<TmdbMovieDetails>("movie:${title.media.id}:$language") {
                    tmdbApi.getMovieDetails(title.media.id, Constants.TMDB_API_KEY, language = language)
                }
                val enriched = title.copy(media = enrich(title.media, details.title, details.posterPath, details.backdropPath))
                CalendarMonthResult(calendarMovieReleases(enriched, details, month, region), emptyList())
            }
            publish(result)
            val logo = if (result.entries.isNotEmpty()) {
                try {
                    // Include queue time in the optional-artwork budget.
                    withTimeoutOrNull(3_000) {
                        artworkPermits.withPermit { mediaRepository.getLogoUrl(title.media.mediaType, title.media.id) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            } else null
            result.copy(entries = result.entries.map { it.copy(logoUrl = logo) }).also { publish(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A later enrichment failure must not remove already confirmed dates.
            val fallback = lastPublished ?: CalendarMonthResult(emptyList(),
                listOf("Release dates for ${title.media.title} are unavailable."))
            fallback.also { publish(it) }
        }
    }

    private fun combineResults(results: Collection<CalendarMonthResult>, warnings: List<String>) = CalendarMonthResult(
            entries = results.flatMap { it.entries }.distinctBy { it.id }
                .sortedWith(compareBy<CalendarRelease> { it.date }.thenBy { it.releaseInstant }.thenBy { it.media.title }
                    .thenBy { it.seasonNumber }.thenBy { it.episodeNumber }),
            warnings = warnings + results.flatMap { it.warnings }
        )

    private suspend fun televisionReleases(
        original: CalendarWatchlistTitle,
        month: YearMonth,
        timezone: ZoneId,
        language: String,
        onProgress: suspend (CalendarMonthResult) -> Unit
    ): CalendarMonthResult = coroutineScope {
        val details = cached<TmdbTvDetails>("tv:${original.media.id}:$language") {
            tmdbApi.getTvDetails(original.media.id, Constants.TMDB_API_KEY, language = language)
        }
        val first = month.atDay(1).minusDays(1)
        val last = month.atEndOfMonth().plusDays(1)
        val ended = details.status in setOf("Ended", "Canceled")
        val lastAired = calendarDate(details.lastEpisodeToAir?.airDate)
        if (ended && lastAired != null && lastAired < first) {
            return@coroutineScope CalendarMonthResult(emptyList(), emptyList())
        }
        val title = original.copy(media = enrich(original.media, details.name, details.posterPath, details.backdropPath))
        // Read every potentially relevant season, including specials and unknown season dates.
        // A next-season premiere does not prove the previous season has finished airing.
        val seasons = details.seasons.filter { calendarDate(it.airDate)?.let { date -> date <= last } != false }
        val episodeMutex = Mutex()
        val knownEpisodes = linkedMapOf<Pair<Int, Int>, TmdbEpisode>()
        listOfNotNull(details.nextEpisodeToAir, details.lastEpisodeToAir).forEach {
            knownEpisodes[it.seasonNumber to it.episodeNumber] = it
        }
        var incompleteSeasons = false
        fun warnings() = if (incompleteSeasons) listOf("Some episode dates for ${title.media.title} are unavailable.") else emptyList()
        suspend fun publishDates() {
            onProgress(CalendarMonthResult(calendarEpisodeReleases(title, knownEpisodes.values.toList(), emptyList(), month, timezone), warnings()))
        }
        // TV details can already contain the next release; render it while seasons fill in.
        publishDates()
        seasons.map { season -> async {
            val (episodes, failed) = try {
                val data = cached<TmdbSeasonDetails>("season:${title.media.id}:${season.seasonNumber}:$language") {
                    tmdbApi.getTvSeason(title.media.id, season.seasonNumber, Constants.TMDB_API_KEY, language = language)
                }
                data.episodes to false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList<TmdbEpisode>() to true
            }
            episodeMutex.withLock {
                episodes.forEach { knownEpisodes[it.seasonNumber to it.episodeNumber] = it }
                incompleteSeasons = incompleteSeasons || failed
                publishDates()
            }
        } }.awaitAll()
        val episodes = knownEpisodes.values.toList()
        val relevantSeasons = episodes.filter { calendarDate(it.airDate)?.let { date -> date >= first && date <= last } == true }
            .map { it.seasonNumber }.distinct()
        val timed = authoritativeTimes(title.media, relevantSeasons)
        CalendarMonthResult(
            calendarEpisodeReleases(title, episodes, timed, month, timezone),
            warnings()
        )
    }

    private suspend fun authoritativeTimes(media: MediaItem, seasons: List<Int>): List<TraktCalendarEpisode> = coroutineScope {
        if (seasons.isEmpty() || Constants.TRAKT_CLIENT_ID.isBlank()) return@coroutineScope emptyList()
        val found = mutableListOf<TraktCalendarEpisode>()
        // This is a total enrichment budget, including mapping, queueing and all seasons.
        // Date-only releases have already been published and do not wait for this work.
        withTimeoutOrNull(4_000) {
            try {
                val traktId = media.traktId ?: cached<Int>("trakt-id:${media.id}", timePermits) {
                    traktApi.searchByTmdb(Constants.TRAKT_CLIENT_ID, media.id, "show")
                        .firstOrNull { it.show?.ids?.tmdb == media.id }?.show?.ids?.trakt
                        ?: throw IOException("No Trakt mapping")
                }
                seasons.map { season -> async {
                    try {
                        val episodes = cached<List<TraktCalendarEpisode>>("trakt-season:$traktId:$season", timePermits) {
                            traktApi.getCalendarSeasonEpisodes(Constants.TRAKT_CLIENT_ID, traktId, season)
                        }
                        synchronized(found) { found.addAll(episodes) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Other completed seasons can still contribute authoritative times.
                    }
                } }.awaitAll()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Public Trakt metadata is optional, including when no account is connected.
            }
        }
        synchronized(found) { found.toList() }
    }

    private fun enrich(media: MediaItem, title: String, poster: String?, backdrop: String?): MediaItem = media.copy(
        title = title.ifBlank { media.title },
        image = normalizeWatchlistArtworkUrl(poster, false) ?: media.image,
        backdrop = normalizeWatchlistArtworkUrl(backdrop, true) ?: media.backdrop
    )

    private fun ensureProfile(profileId: String) {
        if (profileManager.getProfileIdSync() != profileId) throw CancellationException("Calendar profile changed")
    }

    internal fun invalidateMetadata() = synchronized(metadataCache) { metadataCache.clear() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, requestPermits: Semaphore = permits, block: suspend () -> T): T {
        synchronized(metadataCache) {
            metadataCache[key]?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000L }?.let { return it.value as T }
        }
        val value = requestPermits.withPermit {
            withTimeoutOrNull(20_000) { block() } ?: throw IOException("Metadata request timed out")
        }
        synchronized(metadataCache) {
            metadataCache[key] = Cached(value, System.currentTimeMillis())
            while (metadataCache.size > 512) metadataCache.remove(metadataCache.keys.first())
        }
        return value
    }
}
