package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StremioCatalogResponse
import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.CollectionSourceConfig
import com.arflix.tv.data.model.CollectionSourceKind
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * Collection tabs page one media type through sources that deliver mixed lists (MDBList, Trakt,
 * TMDB lists, curated lists). The tab must not end where the fetched window runs out of its type.
 */
class CollectionTabPagingTest {
    private val streams = mockk<StreamRepository>()
    private val media = spyk(MediaRepository(mockk(relaxed = true), mockk(), mockk(), mockk(), mockk(), streams, mockk()))

    init {
        coEvery { media.getMovieDetails(any()) } answers { MediaItem(firstArg(), "Movie ${firstArg<Int>()}") }
        coEvery { media.getTvDetails(any()) } answers {
            MediaItem(firstArg(), "Show ${firstArg<Int>()}", mediaType = MediaType.TV)
        }
    }

    private fun collection(vararg sources: CollectionSourceConfig) = CatalogConfig(
        id = "collection-test", title = "Test", sourceType = CatalogSourceType.PREINSTALLED,
        kind = CatalogKind.COLLECTION, collectionSources = sources.toList()
    )

    /** Untyped curated list: [movies] movie ids first, then [shows] series ids (ids 1000+). */
    private fun mixedList(movies: Int, shows: Int) = CollectionSourceConfig(
        kind = CollectionSourceKind.CURATED_IDS,
        curatedRefs = (1..movies).map { "movie:$it" } + (1..shows).map { "tv:${1000 + it}" }
    )

    private fun addonSource() = CollectionSourceConfig(
        kind = CollectionSourceKind.ADDON_CATALOG, addonId = "addon", addonCatalogType = "movie", addonCatalogId = "list"
    )

    /** Pages the tab like the detail screen does: first page 8, then steps of 12. */
    private suspend fun pageThrough(catalog: CatalogConfig, type: MediaType): List<Int> {
        val ids = mutableListOf<Int>()
        var page = media.loadCollectionCatalogPage(catalog, 0, 8, type)
        ids += page.items.map { it.id }
        var rounds = 0
        while (page.hasMore && rounds++ < 50) {
            page = media.loadCollectionCatalogPage(catalog, page.nextOffset!!, 12, type)
            ids += page.items.map { it.id }
        }
        assertTrue("paging must terminate", rounds < 50)
        return ids
    }

    @Test fun `series listed after entry 30 still fill the series tab`() = runBlocking {
        val catalog = collection(mixedList(movies = 40, shows = 10))
        assertEquals((1001..1010).toList(), pageThrough(catalog, MediaType.TV))
    }

    @Test fun `movie tab pages past the first fetch window`() = runBlocking {
        val catalog = collection(mixedList(movies = 60, shows = 10))
        assertEquals((1..60).toList(), pageThrough(catalog, MediaType.MOVIE))
    }

    @Test fun `movie tab stops at the source ceiling, not at the first window`() = runBlocking {
        // Curated sources are read up to 72 entries; everything within that stays reachable.
        val catalog = collection(mixedList(movies = 90, shows = 0))
        assertEquals((1..72).toList(), pageThrough(catalog, MediaType.MOVIE))
    }

    @Test fun `failed source result is not cached`() = runBlocking {
        coEvery { streams.findInstalledAddonIdForCatalog(any(), any(), any()) } returns "addon"
        coEvery { streams.getAddonCatalogPage(any(), any(), any(), any(), any()) } throws IOException("offline")
        val catalog = collection(mixedList(movies = 5, shows = 0), addonSource())

        val offline = media.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
        assertEquals((1..5).toList(), offline.items.map { it.id })

        coEvery { streams.getAddonCatalogPage(any(), any(), any(), any(), any()) } answers {
            val skip = arg<Int>(3)
            StremioCatalogResponse(metas = if (skip > 0) emptyList() else (501..503).map {
                StremioMetaPreview(id = "tmdb:$it", name = "Addon $it")
            })
        }
        val online = media.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
        assertEquals((1..5).toList() + (501..503).toList(), online.items.map { it.id })
    }

    @Test fun `genuinely empty source keeps the collection uncached but loading`() = runBlocking {
        // An empty source cannot be told apart from a failed one, so nothing is cached and each
        // open asks the sources again; the other source's titles still show every time.
        coEvery { streams.findInstalledAddonIdForCatalog(any(), any(), any()) } returns "addon"
        coEvery { streams.getAddonCatalogPage(any(), any(), any(), any(), any()) } returns
            StremioCatalogResponse(metas = emptyList())
        val catalog = collection(mixedList(movies = 5, shows = 0), addonSource())

        repeat(2) {
            val page = media.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
            assertEquals((1..5).toList(), page.items.map { it.id })
            assertFalse(page.hasMore)
        }
        coVerify(exactly = 2) { streams.getAddonCatalogPage(any(), any(), any(), any(), any()) }
    }
}
