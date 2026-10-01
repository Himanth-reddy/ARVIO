package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbCombinedCredits
import com.arflix.tv.data.api.TmdbListResponse
import com.arflix.tv.data.api.TmdbMediaItem
import com.arflix.tv.data.api.TmdbPersonDetails
import com.arflix.tv.data.api.TmdbPublicListItem
import com.arflix.tv.data.api.TmdbPublicListResponse
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TmdbCatalogLoadingTest {
    @Test fun `paging reuses references and language changes invalidate them`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse(
            items = (1..4).map { TmdbPublicListItem(it, "movie") }
        )
        val repository = repository(api)
        assertEquals(listOf(1, 2), repository.loadCustomCatalogPage(catalog("list"), 0, 2).items.map { it.id })
        assertEquals(listOf(3, 4), repository.loadCustomCatalogPage(catalog("list"), 2, 2).items.map { it.id })
        coVerify(exactly = 1) { api.getPublicList(1, any(), "en-US", 1) }
        repository.contentLanguage = "nl-NL"
        repository.loadCustomCatalogPage(catalog("list"), 0, 2)
        coVerify(exactly = 1) { api.getPublicList(1, any(), "nl-NL", 1) }
    }

    @Test fun `scoped list filters before its item cap and accepts restored URL`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse(
            items = (1..205).map { TmdbPublicListItem(it, "movie") } + TmdbPublicListItem(501, "tv")
        )
        val catalog = catalog("list", "tv").copy(sourceRef = null, sourceUrl = "https://www.themoviedb.org/list/1/tv")
        assertEquals(listOf(501), repository(api).loadCustomCatalogPage(catalog, 0, 40).items.map { it.id })
    }

    @Test fun `person uses real credits for both movies and series`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPersonDetails(1, any(), any(), any()) } returns TmdbPersonDetails(
            combinedCredits = TmdbCombinedCredits(
                cast = listOf(TmdbMediaItem(id = 501, mediaType = "tv", name = "Series", popularity = 20f)),
                crew = listOf(TmdbMediaItem(id = 1, mediaType = "movie", title = "Film", popularity = 10f))
            )
        )
        val repository = repository(api)
        assertEquals(listOf(501, 1), repository.loadCustomCatalogPage(catalog("person"), 0, 40).items.map { it.id })
        assertEquals(listOf(501), repository.loadCustomCatalogPage(catalog("person", "tv"), 0, 40).items.map { it.id })
        coVerify(exactly = 0) { api.discoverWithParams(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `company interleaves scoped discover results and network is TV only`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.discoverWithParams("movie", any(), any(), any(), any(), 1) } returns TmdbListResponse(
            results = listOf(TmdbMediaItem(id = 1, title = "Movie"))
        )
        coEvery { api.discoverWithParams("tv", any(), any(), any(), any(), 1) } returns TmdbListResponse(
            results = listOf(TmdbMediaItem(id = 501, name = "Series"))
        )
        val repository = repository(api)
        assertEquals(listOf(1, 501), repository.loadCustomCatalogPage(catalog("company"), 0, 40).items.map { it.id })
        assertEquals(listOf(501), repository.loadCustomCatalogPage(catalog("network"), 0, 40).items.map { it.id })
        coVerify { api.discoverWithParams("tv", any(), mapOf("with_networks" to "1"), any(), any(), 1) }
        coVerify(exactly = 1) { api.discoverWithParams("movie", any(), any(), any(), any(), 1) }
    }

    @Test fun `failed page retries and cancellation is propagated`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } throws IOException("offline")
        val repository = repository(api)
        assertTrue(repository.loadCustomCatalogPage(catalog("list"), 0, 40).items.isEmpty())
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse(items = listOf(TmdbPublicListItem(1)))
        assertEquals(listOf(1), repository.loadCustomCatalogPage(catalog("list"), 0, 40).items.map { it.id })
        repository.clearMediaCache()
        coEvery { api.getPublicList(1, any(), any(), 1) } throws CancellationException("left screen")
        assertTrue(runCatching { repository.loadCustomCatalogPage(catalog("list"), 0, 40) }.exceptionOrNull() is CancellationException)
    }

    private fun catalog(kind: String, type: String = "") = CatalogConfig(
        id = "tmdb-test", title = "TMDB", sourceType = CatalogSourceType.TMDB, sourceRef = "tmdb:$kind:1:$type"
    )

    private fun repository(api: TmdbApi) = MediaRepository(
        mockk(relaxed = true), api, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
    ).also { repository ->
        (1..4).forEach { repository.cacheItem(MediaItem(id = it, title = "Movie $it")) }
        repository.cacheItem(MediaItem(id = 501, title = "Series", mediaType = MediaType.TV))
    }
}
