package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TmdbTvSeason
import com.arflix.tv.data.api.TraktApi
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.data.repository.simkl.SimklAuthManager
import com.arflix.tv.data.repository.simkl.SimklSyncService
import com.arflix.tv.data.repository.sync.RemoteWatchlistResult
import com.arflix.tv.util.Constants
import io.mockk.*
import java.io.IOException
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReleaseCalendarRepositoryTest {
    private val tmdb = mockk<TmdbApi>()
    private val traktApi = mockk<TraktApi>()
    private val own = mockk<WatchlistRepository>()
    private val trakt = mockk<TraktRepository>()
    private val simklAuth = mockk<SimklAuthManager>()
    private val simkl = mockk<SimklSyncService>()
    private val mdb = mockk<MdbListRepository>()
    private val media = mockk<MediaRepository>()
    private val profiles = mockk<ProfileManager>()
    private lateinit var repository: ReleaseCalendarRepository
    private val month = YearMonth.of(2026, 10)

    @Before fun setup() {
        mockkObject(Constants)
        every { Constants.TMDB_API_KEY } returns "fixture-key"
        every { Constants.TRAKT_CLIENT_ID } returns ""
        every { profiles.getProfileIdSync() } returns "fixture-profile"
        every { trakt.isAuthenticated } returns flowOf(false)
        coEvery { own.getLocalWatchlistItems() } returns listOf(MediaItem(1, "Own movie"))
        coEvery { simklAuth.isConnected() } returns false
        coEvery { mdb.getWatchlist() } returns RemoteWatchlistResult(false, null, 0)
        coEvery { media.getLogoUrl(any<MediaType>(), any()) } returns null
        repository = ReleaseCalendarRepository(tmdb, traktApi, own, trakt, simklAuth, simkl, mdb, media, profiles)
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `own cloud mirrored watchlist works with no external account`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } returns TmdbMovieDetails(1, "Own movie", releaseDate = "2026-10-04")
        val lists = repository.loadWatchlists("fixture-profile")
        val result = repository.loadMonth(lists, month, ZoneId.of("UTC"), "NL")
        assertEquals(setOf(ReleaseCalendarSource.ARVIO), lists.items.keys)
        assertEquals("Own movie", result.entries.single().media.title)
        assertNull(result.entries.single().releaseInstant)
        coVerify(exactly = 0) { trakt.getWatchlistSyncResultWithAuthState() }
        coVerify(exactly = 0) { simkl.getWatchlistItems() }
        confirmVerified(traktApi)
    }

    @Test fun `one failed provider does not discard own releases`() = runTest {
        every { trakt.isAuthenticated } returns flowOf(true)
        coEvery { trakt.getWatchlistSyncResultWithAuthState() } throws IOException("Fixture outage")
        val lists = repository.loadWatchlists("fixture-profile")
        assertEquals(1, lists.items[ReleaseCalendarSource.ARVIO]?.size)
        assertEquals(listOf("Trakt could not be refreshed."), lists.warnings)
    }

    @Test fun `logo failure cannot remove confirmed releases`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } returns TmdbMovieDetails(1, "Own movie", releaseDate = "2026-10-04")
        coEvery { media.getLogoUrl(any<MediaType>(), any()) } throws IOException("Fixture logo 404")
        val result = repository.loadMonth(repository.loadWatchlists("fixture-profile"), month, ZoneId.of("UTC"), "NL")
        assertEquals(1, result.entries.size)
        assertNull(result.entries.single().logoUrl)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun `incomplete simkl playback sync preserves usable library titles`() = runTest {
        coEvery { simklAuth.isConnected() } returns true
        coEvery { simkl.syncIfNeeded(any()) } returns false
        coEvery { simkl.getWatchlistItems() } returns listOf(MediaItem(8, "Planned show", mediaType = MediaType.TV))
        coEvery { simkl.getLibraryItems("watching", any()) } returns listOf(MediaItem(9, "Current show", mediaType = MediaType.TV))
        val lists = repository.loadWatchlists("fixture-profile")
        assertEquals(setOf(8, 9), lists.items[ReleaseCalendarSource.SIMKL].orEmpty().map { it.id }.toSet())
        assertTrue(lists.warnings.any { it.contains("SIMKL") })
    }

    @Test fun `profile change rejects private watchlist result`() = runTest {
        every { profiles.getProfileIdSync() } returns "other-profile"
        try {
            repository.loadWatchlists("fixture-profile")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            coVerify(exactly = 0) { own.getLocalWatchlistItems() }
        }
    }

    @Test fun `overlapping seasons and specials are not silently excluded`() = runTest {
        val show = MediaItem(5, "Show", mediaType = MediaType.TV)
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Show", seasons = listOf(
            TmdbTvSeason(seasonNumber = 0, airDate = "2020-01-01"),
            TmdbTvSeason(seasonNumber = 1, airDate = "2026-01-01"),
            TmdbTvSeason(seasonNumber = 2, airDate = "2026-09-01")
        ))
        coEvery { tmdb.getTvSeason(5, any(), any(), any()) } answers {
            val season = secondArg<Int>()
            TmdbSeasonDetails(seasonNumber = season, episodes = listOf(TmdbEpisode(seasonNumber = season, episodeNumber = 1, airDate = "2026-10-04")))
        }
        val result = repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show))), month, ZoneId.of("UTC"), "US")
        assertEquals(setOf(0, 1, 2), result.entries.map { it.seasonNumber }.toSet())
    }

    @Test fun `large watchlist uses at most five parallel metadata requests and reuses cache`() = runTest {
        var inFlight = 0
        var peak = 0
        coEvery { tmdb.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            inFlight++
            peak = maxOf(peak, inFlight)
            delay(10)
            inFlight--
            TmdbMovieDetails(firstArg(), "Movie", releaseDate = "2026-10-04")
        }
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to (1..18).map { MediaItem(it, "Movie $it") }))
        val partialSizes = mutableListOf<Int>()
        assertEquals(18, repository.loadMonth(lists, month, ZoneId.of("UTC"), "US", onProgress = { partialSizes += it.entries.size }).entries.size)
        assertTrue(partialSizes.any { it in 1..17 })
        assertEquals(5, peak)
        repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US")
        coVerify(exactly = 18) { tmdb.getMovieDetails(any(), any(), any(), any()) }
    }

    @Test fun `localized metadata cache cannot leak the previous language`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), "en-US") } returns TmdbMovieDetails(1, "English title", releaseDate = "2026-10-04")
        coEvery { tmdb.getMovieDetails(1, any(), any(), "nl-NL") } returns TmdbMovieDetails(1, "Nederlandse titel", releaseDate = "2026-10-04")
        val lists = repository.loadWatchlists("fixture-profile")
        assertEquals("English title", repository.loadMonth(lists, month, ZoneId.of("UTC"), "US", "en-US").entries.single().media.title)
        assertEquals("Nederlandse titel", repository.loadMonth(lists, month, ZoneId.of("UTC"), "NL", "nl-NL").entries.single().media.title)
        coVerify(exactly = 2) { tmdb.getMovieDetails(any(), any(), any(), any()) }
    }
}
