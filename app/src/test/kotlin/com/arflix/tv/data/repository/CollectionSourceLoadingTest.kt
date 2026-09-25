package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbPublicListItem
import com.arflix.tv.data.api.TmdbPublicListResponse
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.CollectionSourceConfig
import com.arflix.tv.data.model.CollectionSourceKind
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class CollectionSourceLoadingTest {
    @Test fun `tmdb series after seven movie pages are found without refetching pages`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), any()) } answers {
            val number = arg<Int>(3)
            if (number <= 7) page(number, 8, ((number - 1) * 20 + 1)..(number * 20))
            else TmdbPublicListResponse(page = 8, totalPages = 8, items = listOf(TmdbPublicListItem(501, "tv")))
        }
        val repository = repository(api)
        val catalog = catalog(listSource())
        repeat(2) {
            assertEquals(listOf(501), repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.TV).items.map { it.id })
        }
        (1..8).forEach { number -> coVerify(exactly = 1) { api.getPublicList(1, any(), any(), number) } }
    }

    @Test fun `type filter precedes even a capped collection budget`() = runTest {
        val repository = repository(mockk())
        val catalog = catalog(CollectionSourceConfig(
            kind = CollectionSourceKind.CURATED_IDS,
            curatedRefs = (1..140).map { "movie:$it" } + "tv:501"
        )).copy(collectionRailKey = null)
        assertEquals(listOf(501), repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.TV).items.map { it.id })
    }

    @Test fun `entirely empty successful collection is cached`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse()
        val repository = repository(api)
        repeat(2) { assertEquals(emptyList<MediaItem>(), repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8).items) }
        coVerify(exactly = 1) { api.getPublicList(1, any(), any(), 1) }
    }

    @Test fun `cancelled collection request propagates cancellation and can retry`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } throws CancellationException("cancelled")
        val repository = repository(api)
        try {
            repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        coEvery { api.getPublicList(1, any(), any(), 1) } returns page(1, 1, 1..2)
        assertEquals(listOf(1, 2), repository.loadCollectionCatalogPage(catalog(listSource()), 0, 8).items.map { it.id })
    }
    @Test fun `series beyond expanded window remain reachable`() = runTest {
        val repository = repository(mockk())
        val catalog = catalog(CollectionSourceConfig(
            kind = CollectionSourceKind.CURATED_IDS,
            curatedRefs = (1..140).map { "movie:$it" } + "tv:501"
        ))
        val page = repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.TV)
        assertEquals(listOf(501), page.items.map { it.id })
    }

    @Test fun `series beyond first forty movies remain reachable`() = runTest {
        val fixture = mixedList()
        val page = fixture.repository.loadCollectionCatalogPage(fixture.catalog, 0, 8, MediaType.TV)
        assertEquals(listOf(501), page.items.map { it.id })
    }

    @Test fun `movie tab cache does not hide later series`() = runTest {
        val fixture = mixedList()
        assertEquals(8, fixture.repository.loadCollectionCatalogPage(fixture.catalog, 0, 8, MediaType.MOVIE).items.size)
        val page = fixture.repository.loadCollectionCatalogPage(fixture.catalog, 0, 8, MediaType.TV)
        assertEquals(listOf(501), page.items.map { it.id })
    }

    @Test fun `partially failed source is retried on reopening`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns page(1, 2, 1..20)
        coEvery { api.getPublicList(1, any(), any(), 2) } throws IOException("temporary error")
        val repository = repository(api)
        val catalog = catalog(listSource())
        assertEquals(8, repository.loadCollectionCatalogPage(catalog, 0, 8).items.size)
        coEvery { api.getPublicList(1, any(), any(), 2) } returns page(2, 2, 21..40)
        repository.loadCollectionCatalogPage(catalog, 0, 8)
        coVerify(exactly = 2) { api.getPublicList(1, any(), any(), 2) }
    }

    @Test fun `successful empty source does not disable collection caching`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns TmdbPublicListResponse()
        val repository = repository(api)
        val catalog = catalog(
            listSource(),
            CollectionSourceConfig(kind = CollectionSourceKind.CURATED_IDS, curatedRefs = (1..20).map { "movie:$it" })
        )
        repeat(2) { assertEquals(8, repository.loadCollectionCatalogPage(catalog, 0, 8).items.size) }
        coVerify(exactly = 1) { api.getPublicList(1, any(), any(), 1) }
    }

    @Test fun `short mixed source pages by type with distinct offsets`() = runTest {
        val api = mockk<TmdbApi>()
        val repository = repository(api)
        val catalog = catalog(CollectionSourceConfig(
            kind = CollectionSourceKind.CURATED_IDS,
            curatedRefs = listOf("movie:1", "tv:501", "movie:2", "tv:502")
        ))
        val first = repository.loadCollectionCatalogPage(catalog, 0, 1, MediaType.TV)
        val second = repository.loadCollectionCatalogPage(catalog, first.nextOffset!!, 1, MediaType.TV)
        assertEquals(listOf(501), first.items.map { it.id })
        assertEquals(listOf(502), second.items.map { it.id })
        assertEquals(2, second.nextOffset)
        assertFalse(second.hasMore)
    }

    @Test fun `missing addon helper identifies unavailable sources`() = runTest {
        val stream = mockk<StreamRepository>()
        coEvery { stream.findInstalledAddonIdForCatalog("movie", "missing", "provider") } returns null
        coEvery { stream.findInstalledAddonIdForCatalog("series", "available", "installed") } returns "installed"
        val repository = repository(mockk(), stream)
        val catalog = catalog(
            CollectionSourceConfig(CollectionSourceKind.ADDON_CATALOG, addonCatalogType = "movie", addonCatalogId = "missing", addonId = "provider"),
            CollectionSourceConfig(CollectionSourceKind.ADDON_CATALOG, addonCatalogType = "series", addonCatalogId = "available", addonId = "installed")
        )
        assertEquals(listOf("provider"), repository.missingCollectionAddons(catalog))
    }

    private data class Fixture(val repository: MediaRepository, val catalog: CatalogConfig)

    private fun mixedList(): Fixture {
        val api = mockk<TmdbApi>()
        coEvery { api.getPublicList(1, any(), any(), 1) } returns page(1, 3, 1..20)
        coEvery { api.getPublicList(1, any(), any(), 2) } returns page(2, 3, 21..40)
        coEvery { api.getPublicList(1, any(), any(), 3) } returns TmdbPublicListResponse(
            page = 3, totalPages = 3, items = listOf(TmdbPublicListItem(501, "tv"))
        )
        return Fixture(repository(api), catalog(listSource()))
    }

    private fun page(number: Int, total: Int, ids: IntRange) = TmdbPublicListResponse(
        page = number, totalPages = total, items = ids.map { TmdbPublicListItem(it, "movie") }
    )

    private fun listSource() = CollectionSourceConfig(CollectionSourceKind.TMDB_LIST, tmdbListId = 1)

    private fun catalog(vararg sources: CollectionSourceConfig) = CatalogConfig(
        id = "review", title = "Review", sourceType = CatalogSourceType.PREINSTALLED,
        kind = CatalogKind.COLLECTION, collectionRailKey = "imported", collectionSources = sources.toList()
    )

    private fun repository(api: TmdbApi, stream: StreamRepository = mockk(relaxed = true)) = MediaRepository(
        mockk(relaxed = true), api, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), stream, mockk(relaxed = true)
    ).also { repository ->
        (1..40).forEach { repository.cacheItem(MediaItem(id = it, title = "Movie $it")) }
        (501..502).forEach { repository.cacheItem(MediaItem(id = it, title = "Series $it", mediaType = MediaType.TV)) }
    }
}
