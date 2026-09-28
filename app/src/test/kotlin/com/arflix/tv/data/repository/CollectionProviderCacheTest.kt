package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbWatchProvidersResponse
import com.arflix.tv.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CollectionProviderCacheTest {
    @Test fun cancelledProviderLookupDoesNotCacheAnEmptyResult() = runTest {
        val api = mockk<TmdbApi>()
        val repository = repository(api)
        coEvery { api.getMovieWatchProviders(1, any()) } throws CancellationException("focus moved")
        assertTrue(runCatching { repository.getStreamingServices(MediaType.MOVIE, 1, "US") }
            .exceptionOrNull() is CancellationException)
        coEvery { api.getMovieWatchProviders(1, any()) } returns TmdbWatchProvidersResponse()
        assertNull(repository.getStreamingServices(MediaType.MOVIE, 1, "US"))
        assertNull(repository.getStreamingServices(MediaType.MOVIE, 1, "US"))
        coVerify(exactly = 2) { api.getMovieWatchProviders(1, any()) }
    }

    @Test fun transientProviderFailureCanRetryButSuccessfulEmptyResultIsCached() = runTest {
        val api = mockk<TmdbApi>()
        val repository = repository(api)
        coEvery { api.getTvWatchProviders(2, any()) } throws IOException("offline")
        assertNull(repository.getStreamingServices(MediaType.TV, 2, "NL"))
        coEvery { api.getTvWatchProviders(2, any()) } returns TmdbWatchProvidersResponse()
        assertNull(repository.getStreamingServices(MediaType.TV, 2, "NL"))
        assertNull(repository.getStreamingServices(MediaType.TV, 2, "NL"))
        coVerify(exactly = 2) { api.getTvWatchProviders(2, any()) }
    }

    private fun repository(api: TmdbApi) = MediaRepository(mockk(relaxed = true), api,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true))
}
