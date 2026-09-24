package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.arflix.tv.data.api.*
import com.arflix.tv.data.repository.sync.SyncProvider
import com.arflix.tv.data.repository.sync.SyncProviderStore
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.traktDataStore
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Provider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class TraktContinueWatchingRefreshTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private val api = mockk<TraktApi>(relaxed = true)
    private lateinit var repository: TraktRepository
    private val stamp = "2026-09-23T10:00:00Z"
    private val show = TraktShowInfo("Show", 2026, TraktIds(trakt = 10, tmdb = 10))

    @Before fun setUp() {
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        val store = MemoryStore(preferencesOf(stringPreferencesKey("trakt_access_token") to "test-token"))
        val settings = MemoryStore(emptyPreferences())
        every { any<Context>().traktDataStore } returns store
        every { any<Context>().settingsDataStore } returns settings
        val profiles = mockk<ProfileManager>(relaxed = true) {
            every { getProfileIdSync() } returns "p"
            every { activeProfileId } returns MutableStateFlow("p")
            every { profileStringKey(any()) } answers { stringPreferencesKey(firstArg()) }
            every { profileLongKey(any()) } answers { longPreferencesKey(firstArg()) }
            every { profileBooleanKey(any()) } answers { booleanPreferencesKey(firstArg()) }
        }
        val providers = mockk<SyncProviderStore>(relaxed = true) {
            coEvery { readProviders(any()) } returns emptySet()
            coEvery { getProvider() } returns SyncProvider.TRAKT
        }
        val tmdb = mockk<TmdbApi>()
        coEvery { tmdb.getTvDetails(any(), any(), any(), any()) } throws IllegalStateException("No artwork in fixture")
        repository = TraktRepository(RuntimeEnvironment.getApplication(), api, tmdb, OkHttpClient(),
            Provider { mockk(relaxed = true) }, profiles, mockk(relaxed = true), providers,
            mockk(relaxed = true), ContinueWatchingUpdates())
        coEvery { api.getLastActivities(any(), any(), any()) } returns mockk<TraktLastActivities>(relaxed = true) {
            every { episodes } returns TraktActivityTimestamps(watchedAt = stamp)
            every { shows } returns null
        }
        coEvery { api.getWatchingNow(any(), any(), any(), any()) } returns Response.success(null)
        coEvery { api.getWatchedShows(any(), any(), any(), any(), any(), any()) } answers {
            if (arg<Int?>(3) == 1) listOf(TraktWatchedShow(1, stamp, stamp, show,
                listOf(TraktWatchedSeason(1, listOf(TraktWatchedEpisode(1, 1, stamp)))))) else emptyList()
        }
        coEvery { api.getShowProgress(any(), any(), any(), any(), any(), any(), any()) } returns
            TraktShowProgress(3, 1, stamp, null, TraktNextEpisode(1, 2, "Next", TraktIds()), emptyList())
    }

    @After fun tearDown() {
        unmockkStatic("com.arflix.tv.util.DataStoresKt")
    }

    @Test fun refreshReadsActivePlaybackEvenWhenActivityDidNotChange() = runBlocking {
        assertEquals(2, repository.getContinueWatching(true).single().episode)
        coEvery { api.getWatchingNow(any(), any(), any(), any()) } returns Response.success(
            TraktWatchingItem("2026-09-23T11:00:00Z", stamp, "scrobble", "episode", null,
                TraktEpisodeInfo(1, 3, "Playing", TraktIds(), 60), show))
        assertEquals(3, repository.getContinueWatching(true).single().episode)
        coVerify(exactly = 2) { api.getWatchingNow(any(), any(), any(), any()) }
        coVerify(exactly = 1) { api.getShowProgress(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun pausedShowReturnsToUpNextWithoutAnotherShowProgressRead() = runBlocking {
        val paused = TraktPlaybackItem(1L, 20f, stamp, "episode", null,
            TraktEpisodeInfo(1, 2, "Paused", TraktIds(), 45), show)
        coEvery { api.getPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } answers {
            if (arg<Int?>(4) == 1) listOf(paused) else emptyList()
        }
        assertFalse(repository.getContinueWatching(true).single().isUpNext)
        coEvery { api.getPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val next = repository.getContinueWatching(true).single()
        assertTrue(next.isUpNext)
        assertEquals(2, next.episode)
        coVerify(exactly = 1) { api.getShowProgress(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun firstLoginRateLimitStopsQueuedReadsAndSubsequentRefreshWithoutCache() = runBlocking {
        coEvery { api.getLastActivities(any(), any(), any()) } throws
            HttpException(Response.error<Any>(429, "rate limited".toResponseBody()))
        assertTrue(repository.getContinueWatching(true).isEmpty())
        assertTrue(repository.getContinueWatching(true).isEmpty())
        coVerify(exactly = 1) { api.getLastActivities(any(), any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.getWatchingNow(any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.getShowProgress(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun watchingEndpointRateLimitAlsoStopsTheNextRefresh() = runBlocking {
        coEvery { api.getWatchingNow(any(), any(), any(), any()) } returns
            Response.error(429, "rate limited".toResponseBody())
        repository.getContinueWatching(true)
        repository.getContinueWatching(true)
        coVerify(exactly = 1) { api.getLastActivities(any(), any(), any()) }
        coVerify(exactly = 1) { api.getWatchingNow(any(), any(), any(), any()) }
    }
}
