package com.arflix.tv.data.repository

import com.arflix.tv.data.api.*
import com.arflix.tv.data.model.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionCardLoadingTest {
    @Test fun `public list uses supplied artwork without per-card requests and keeps order`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse(items = listOf(
            TmdbPublicListItem(3, "movie", title = "Third", posterPath = "/three.jpg", voteAverage = 7f),
            TmdbPublicListItem(1, "tv", name = "Series", posterPath = "/series.jpg"),
            TmdbPublicListItem(2, "movie", title = "Second", posterPath = "/two.jpg")
        ))
        val repository = repository(api)
        val updates = mutableListOf<List<Int>>()
        val page = repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8, MediaType.MOVIE) {
            updates += it.map { card -> card.id }
        }
        assertEquals(listOf(3, 2), page.items.map { it.id })
        assertTrue(page.items.first().image.endsWith("/three.jpg"))
        assertEquals(listOf(listOf(3, 2)), updates)
        assertFalse(page.hasMore)
        coVerify(exactly = 0) { api.getMovieDetails(any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.getMovieExternalIds(any(), any()) }
        coVerify(exactly = 0) { api.getTvDetails(any(), any(), any(), any()) }
    }

    @Test fun `discover reuses metadata across pages without losing results`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.discoverWithParams(any(), any(), any(), any(), any(), any()) } answers {
            val number = arg<Int>(5)
            TmdbListResponse(page = number, totalPages = 3,
                results = ((number - 1) * 20 + 1..number * 20).map {
                    TmdbMediaItem(id = it, title = "Film $it", posterPath = "/$it.jpg")
                })
        }
        val repository = repository(api)
        val catalog = catalog(CollectionSourceConfig(CollectionSourceKind.TMDB_DISCOVER, mediaType = "movie"))
        val ids = mutableListOf<Int>()
        var offset = 0
        var rounds = 0
        do {
            assertTrue("Pagination must terminate", rounds++ < 12)
            val page = repository.loadCollectionCatalogPage(catalog, offset, 8, MediaType.MOVIE)
            ids += page.items.map { it.id }
            if (page.hasMore || page.items.isNotEmpty()) assertTrue("Cursor must advance", page.nextOffset!! > offset)
            offset = page.nextOffset!!
        } while (page.hasMore)
        assertEquals((1..60).toList(), ids)
        val unfilteredIds = mutableListOf<Int>()
        offset = 0
        rounds = 0
        do {
            assertTrue("Unfiltered pagination must terminate", rounds++ < 12)
            val page = repository.loadCollectionCatalogPage(catalog, offset, 8)
            unfilteredIds += page.items.map { it.id }
            if (page.hasMore) assertTrue(page.nextOffset!! > offset)
            offset = page.nextOffset!!
        } while (page.hasMore)
        assertEquals((1..60).toList(), unfilteredIds)
        coVerify(exactly = 0) { api.getMovieDetails(any(), any(), any(), any()) }
    }

    @Test fun `id-only source renders stable prefix before slow metadata without fetching ratings`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Int>()
            delay(if (id == 2) 1000L else 100L)
            TmdbMovieDetails(id = id, title = "Film $id")
        }
        val repository = repository(api)
        val updates = mutableListOf<Pair<Long, List<Int>>>()
        val page = repository.loadCollectionCatalogPage(catalog(curatedSource()), 0, 3) {
            updates += testScheduler.currentTime to it.map { card -> card.id }
        }
        assertEquals(listOf(100L to listOf(1), 1000L to listOf(1, 2, 3)), updates)
        assertEquals(listOf(1, 2, 3), page.items.map { it.id })
        coVerify(exactly = 0) { api.getMovieExternalIds(any(), any()) }
    }

    @Test fun `detail cancellation is not converted to a missing card`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } throws CancellationException("left collection")
        try {
            repository(api).loadCollectionCatalogPage(catalog(curatedSource()), 0, 3)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `cached later cards do not jump ahead of unresolved cards`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            delay(100)
            TmdbMovieDetails(id = firstArg(), title = "Resolved")
        }
        val repository = repository(api)
        repository.cacheItem(MediaItem(id = 1, title = "Cached first"))
        repository.cacheItem(MediaItem(id = 3, title = "Cached third"))
        val updates = mutableListOf<List<Int>>()
        repository.loadCollectionCatalogPage(catalog(curatedSource()), 0, 3) {
            updates += it.map { card -> card.id }
        }
        assertEquals(listOf(listOf(1), listOf(1, 2, 3)), updates)
    }

    @Test fun `simultaneous collections share six metadata slots`() = runTest {
        val api = mockk<TmdbApi>()
        var active = 0
        var peak = 0
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            active++
            peak = maxOf(peak, active)
            try {
                delay(100)
                TmdbMovieDetails(id = firstArg(), title = "Resolved")
            } finally { active-- }
        }
        val repository = repository(api)
        (0..1).map { group -> async {
            val source = CollectionSourceConfig(CollectionSourceKind.CURATED_IDS,
                curatedRefs = (1..8).map { "movie:${group * 100 + it}" })
            repository.loadCollectionCatalogPage(catalog(source).copy(id = "group-$group"), 0, 8)
        } }.awaitAll().forEach { assertEquals(8, it.items.size) }
        assertEquals(6, peak)
    }

    @Test fun `failed detail does not stall progress or repeat the preceding card`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } answers {
            val id = firstArg<Int>()
            if (id == 2) throw java.io.IOException("unavailable")
            TmdbMovieDetails(id = id, title = "Film $id")
        }
        val page = repository(api).loadCollectionCatalogPage(catalog(curatedSource()), 0, 3)
        assertEquals(listOf(1, 3), page.items.map { it.id })
        assertEquals(3, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test fun `card previews are not treated as full detail cache entries`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse(items = listOf(
            TmdbPublicListItem(1, "movie", title = "Preview", posterPath = "/one.jpg")
        ))
        coEvery { api.getMovieDetails(1, any(), any(), any()) } returns TmdbMovieDetails(id = 1, title = "Full", runtime = 125)
        coEvery { api.getMovieExternalIds(1, any()) } returns TmdbExternalIds()
        val repository = repository(api)
        repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8)
        assertEquals("Full", repository.getMovieDetails(1).title)
        assertEquals("2h 5m", repository.getMovieDetails(1).duration)
        coVerify(exactly = 1) { api.getMovieDetails(1, any(), any(), any()) }
    }

    @Test fun `language changes refetch localized cards rather than retaining stale references`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } answers {
            TmdbPublicListResponse(items = listOf(TmdbPublicListItem(1, "movie", title = arg<String>(2))))
        }
        val repository = repository(api)
        assertEquals("en-US", repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8).items.single().title)
        repository.contentLanguage = "nl-NL"
        assertEquals("nl-NL", repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8).items.single().title)
        coVerify(exactly = 2) { api.getPublicList(1, any(), any(), 1) }
    }

    @Test fun `old language response cannot repopulate new language card cache`() = runTest {
        val api = mockk<TmdbApi>()
        val oldResponse = CompletableDeferred<Unit>()
        coEvery { api.getPublicList(1, any(), any(), 1) } coAnswers {
            val language = arg<String>(2)
            if (language == "en-US") oldResponse.await()
            TmdbPublicListResponse(items = listOf(TmdbPublicListItem(1, "movie", title = language)))
        }
        val repository = repository(api)
        val old = async { repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8) }
        runCurrent()
        repository.contentLanguage = "nl-NL"
        oldResponse.complete(Unit)
        try {
            old.await()
            fail("Old request must be cancelled")
        } catch (_: CancellationException) { }
        assertNull(repository.getCachedItem(MediaType.MOVIE, 1))
        assertEquals("nl-NL", repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8).items.single().title)
    }

    private fun repository(api: TmdbApi) = MediaRepository(
        mockk(relaxed = true), api, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
    )
    private fun listSource() = CollectionSourceConfig(CollectionSourceKind.TMDB_LIST, tmdbListId = 1)
    private fun curatedSource() = CollectionSourceConfig(CollectionSourceKind.CURATED_IDS,
        curatedRefs = listOf("movie:1", "movie:2", "movie:3"))
    private fun catalog(source: CollectionSourceConfig) = CatalogConfig(
        id = "test", title = "Test", sourceType = CatalogSourceType.PREINSTALLED,
        kind = CatalogKind.COLLECTION, collectionRailKey = "imported", collectionSources = listOf(source)
    )
}
