package com.arflix.tv.data.repository

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.CollectionSourceConfig
import com.arflix.tv.data.model.CollectionSourceKind
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.di.RepositoryAccessEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live metadata/artwork check. Does not change profiles or start playback. */
@RunWith(AndroidJUnit4::class)
class CollectionLoadingDeviceTest {
    @Test fun builtInCollectionsAndCoversAreAvailable() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("collectionLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = EntryPointAccessors.fromApplication(context,
            RepositoryAccessEntryPoint::class.java).mediaRepository()
        val imageLoader = ImageLoader.Builder(context).build()
        val catalogs = repository.getDefaultCatalogConfigs().filter { it.kind == CatalogKind.COLLECTION }
        val timings = mutableListOf<Long>()
        try {
            for (catalog in catalogs) {
                val start = SystemClock.elapsedRealtime()
                val first = repository.loadCollectionCatalogPage(catalog, 0, 4)
                timings += SystemClock.elapsedRealtime() - start
                assertTrue("${catalog.title}: must resolve real titles", first.items.isNotEmpty())
                val cover = imageLoader.execute(ImageRequest.Builder(context).data(catalog.collectionCoverImageUrl)
                    .size(400, 240).allowHardware(false).build())
                assertTrue("${catalog.title}: cover must decode", cover is SuccessResult)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "BUILTIN_COLLECTIONS count=${catalogs.size} allTitles=true allCovers=true " +
                    "medianMetadata=${timings.sorted()[timings.size / 2]}ms slowestMetadata=${timings.maxOrNull()}ms\n")
            })
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test fun importedListCardsAndArtworkLoadAndReopen() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("collectionLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = EntryPointAccessors.fromApplication(context,
            RepositoryAccessEntryPoint::class.java).mediaRepository()
        val imageLoader = ImageLoader.Builder(context).memoryCache(null).diskCache(null).build()
        try {
            for (listId in listOf(8687275, 8687276)) {
                repository.clearMediaCache()
                val catalog = CatalogConfig(
                    id = "collection-live-$listId", title = "Kaptain collection",
                    sourceType = CatalogSourceType.PREINSTALLED, kind = CatalogKind.COLLECTION,
                    collectionRailKey = "live-test",
                    collectionSources = listOf(CollectionSourceConfig(
                        kind = CollectionSourceKind.TMDB_LIST, tmdbListId = listId, mediaType = "movie"
                    ))
                )
                val start = SystemClock.elapsedRealtime()
                val page = repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
                val cardsMs = SystemClock.elapsedRealtime() - start
                assertEquals(8, page.items.size)
                assertTrue(page.items.all { it.title.isNotBlank() && it.image.isNotBlank() })
                val images = page.items.map { item -> async {
                    imageLoader.execute(ImageRequest.Builder(context).data(item.image)
                        .size(240, 360).allowHardware(false).build()) is SuccessResult
                } }.awaitAll()
                assertTrue("Every card poster must download and decode", images.all { it })
                val artworkMs = SystemClock.elapsedRealtime() - start
                val warmStart = SystemClock.elapsedRealtime()
                val warm = repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
                val warmMs = SystemClock.elapsedRealtime() - warmStart
                assertEquals(page.items.map { it.id }, warm.items.map { it.id })
                val next = repository.loadCollectionCatalogPage(catalog, page.nextOffset!!, 12, MediaType.MOVIE)
                assertTrue("Next page must not repeat cards", next.items.none { nextItem ->
                    page.items.any { it.id == nextItem.id }
                })
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "COLLECTION list=$listId cards=${cardsMs}ms artwork=${artworkMs}ms warm=${warmMs}ms count=${page.items.size} next=${next.items.size}\n")
                })
            }
        } finally {
            imageLoader.shutdown()
        }
    }
}
