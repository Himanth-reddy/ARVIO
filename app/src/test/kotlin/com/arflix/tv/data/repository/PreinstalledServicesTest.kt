package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CollectionGroupKind
import com.arflix.tv.data.model.CollectionSourceKind
import org.junit.Assert.*
import org.junit.Test

class PreinstalledServicesTest {
    private fun services() = MediaRepository.buildPreinstalledDefaults()
        .filter { it.kind == CatalogKind.COLLECTION && it.collectionGroup == CollectionGroupKind.SERVICE }

    @Test fun `service identities and order survive the defaults replacement`() {
        val slugs = listOf("netflix", "disneyplus", "apple_tvplus", "prime_video", "hbo_max", "hulu",
            "paramountplus", "peacock", "starz", "shudder", "mgmplus", "discoveryplus", "crunchyroll")
        assertEquals(slugs.map { "collection_service_$it" }, services().map { it.id })
    }

    @Test fun `services use static TMDB logos and no borrowed video backgrounds`() {
        services().forEach {
            assertTrue(it.collectionCoverImageUrl!!.startsWith("https://image.tmdb.org/"))
            assertEquals(it.collectionCoverImageUrl, it.collectionFocusGifUrl)
            assertNull(it.collectionHeroVideoUrl)
            assertNull(it.collectionClearLogoUrl)
            assertFalse(it.collectionHideTitle)
        }
    }

    @Test fun `every service can load both tabs without an addon or a curated third party list`() {
        services().forEach {
            assertEquals(setOf("movie", "series"), it.collectionSources.map { s -> s.mediaType }.toSet())
            assertTrue(it.collectionSources.all { s -> s.kind == CollectionSourceKind.TMDB_WATCH_PROVIDER })
            assertTrue(it.requiredAddonUrls.isEmpty())
        }
    }

    @Test fun `Paramount retains current US provider ids`() {
        val sources = services().first { it.title == "Paramount+" }.collectionSources
        assertEquals(setOf(2303, 2616), sources.map { it.tmdbWatchProviderId }.toSet())
        assertTrue(sources.all { it.watchRegion == "US" })
    }

    @Test fun `genre collections retain correct direct TMDB sources for both types`() {
        val genres = MediaRepository.buildPreinstalledDefaults()
            .filter { it.kind == CatalogKind.COLLECTION && it.collectionGroup == CollectionGroupKind.GENRE }
        assertTrue(genres.isNotEmpty())
        assertTrue(genres.all { it.collectionSources.all { s -> s.kind == CollectionSourceKind.TMDB_GENRE } })
        val action = genres.first { it.title == "Action" }
        assertTrue(action.collectionSources.any { it.mediaType == "movie" && it.tmdbGenreId == 28 })
        assertTrue(action.collectionSources.any { it.mediaType == "series" && it.tmdbGenreId == 10759 })
    }
}
