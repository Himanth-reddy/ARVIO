package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.CollectionGroupKind
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeMobileCategoryOrderingTest {

    @Test
    fun `orderCategoriesBySavedCatalogs prioritizes continue watching and enforces savedCatalogs order`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "trending_tv", title = "Trending in Shows", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "trending_anime", title = "Trending in Anime", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "collection_rail_service", title = "Services", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION_RAIL, collectionGroup = CollectionGroupKind.SERVICE),
            CatalogConfig(id = "collection_rail_genre", title = "Genres", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION_RAIL, collectionGroup = CollectionGroupKind.GENRE),
            CatalogConfig(id = "collection_rail_franchise", title = "Franchises", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION_RAIL, collectionGroup = CollectionGroupKind.FRANCHISE),
            CatalogConfig(id = "sports", title = "Sports", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true)
        )

        // Simulate categories finishing loading out of order:
        // In-memory collection rails finish first, then Continue Watching arrives, then TMDB Trending finishes
        val outOfOrderCategories = listOf(
            Category(id = "collection_row_service", title = "Services", items = listOf(MediaItem(2, "Netflix", mediaType = MediaType.MOVIE))),
            Category(id = "collection_row_genre", title = "Genres", items = listOf(MediaItem(3, "Action", mediaType = MediaType.MOVIE))),
            Category(id = "collection_row_franchise", title = "Franchises", items = listOf(MediaItem(4, "Marvel", mediaType = MediaType.MOVIE))),
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(5, "Top Movie", mediaType = MediaType.MOVIE))),
            Category(id = "continue_watching", title = "Continue Watching", items = listOf(MediaItem(1, "CW Movie", mediaType = MediaType.MOVIE))),
            Category(id = "trending_tv", title = "Trending in Shows", items = listOf(MediaItem(6, "Top Show", mediaType = MediaType.TV)))
        )

        val ordered = orderCategoriesBySavedCatalogs(outOfOrderCategories, savedCatalogs)

        assertThat(ordered.map { it.id }).containsExactly(
            "continue_watching",
            "trending_movies",
            "trending_tv",
            "collection_row_service",
            "collection_row_genre",
            "collection_row_franchise"
        ).inOrder()
    }

    @Test
    fun `orderCategoriesBySavedCatalogs ignores collection tile configs in catalog list`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "collection_service_netflix", title = "Netflix", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION, collectionGroup = CollectionGroupKind.SERVICE),
            CatalogConfig(id = "collection_rail_service", title = "Services", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION_RAIL, collectionGroup = CollectionGroupKind.SERVICE),
            CatalogConfig(id = "sports", title = "Sports", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true)
        )

        val categories = listOf(
            Category(id = "sports", title = "Sports", items = listOf(MediaItem(1, "Live Match", mediaType = MediaType.MOVIE))),
            Category(id = "collection_row_service", title = "Services", items = listOf(MediaItem(2, "Netflix", mediaType = MediaType.MOVIE))),
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(3, "Top Movie", mediaType = MediaType.MOVIE)))
        )

        val ordered = orderCategoriesBySavedCatalogs(categories, savedCatalogs)

        assertThat(ordered.map { it.id }).containsExactly(
            "trending_movies",
            "collection_row_service",
            "sports"
        ).inOrder()
    }

    @Test
    fun `applyIptvFavoritesPlacement preserves user catalog order when enabled`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "trending_tv", title = "Trending in Shows", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "favorite_tv", title = "Favorite TV", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true),
            CatalogConfig(id = "sports", title = "Sports", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true)
        )

        val result = applyIptvFavoritesPlacement(savedCatalogs, enabled = true)

        assertThat(result.map { it.id }).containsExactly(
            "trending_movies",
            "trending_tv",
            "favorite_tv",
            "sports"
        ).inOrder()
    }

    @Test
    fun `applyIptvFavoritesPlacement removes favorite tv when disabled`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "favorite_tv", title = "Favorite TV", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true),
            CatalogConfig(id = "sports", title = "Sports", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true)
        )

        val result = applyIptvFavoritesPlacement(savedCatalogs, enabled = false)

        assertThat(result.map { it.id }).containsExactly(
            "trending_movies",
            "sports"
        ).inOrder()
    }

    @Test
    fun `orderCategoriesBySavedCatalogs respects custom placement of favorite tv`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "trending_tv", title = "Trending in Shows", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "favorite_tv", title = "Favorite TV", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true),
            CatalogConfig(id = "sports", title = "Sports", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true)
        )

        val categories = listOf(
            Category(id = "sports", title = "Sports", items = listOf(MediaItem(1, "Live Match", mediaType = MediaType.MOVIE))),
            Category(id = "favorite_tv", title = "Favorite TV", items = listOf(MediaItem(2, "Channel 1", mediaType = MediaType.TV))),
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(3, "Top Movie", mediaType = MediaType.MOVIE))),
            Category(id = "trending_tv", title = "Trending in Shows", items = listOf(MediaItem(4, "Top Show", mediaType = MediaType.TV)))
        )

        val ordered = orderCategoriesBySavedCatalogs(categories, savedCatalogs)

        assertThat(ordered.map { it.id }).containsExactly(
            "trending_movies",
            "trending_tv",
            "favorite_tv",
            "sports"
        ).inOrder()
    }

    @Test
    fun `dropCategoriesMissingFromSavedCatalogs removes a row whose catalog was deleted`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true),
            CatalogConfig(id = "collection_rail_service", title = "Services", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION_RAIL, collectionGroup = CollectionGroupKind.SERVICE),
            CatalogConfig(id = "collection_service_netflix", title = "Netflix", sourceType = CatalogSourceType.PREINSTALLED, isPreinstalled = true, kind = CatalogKind.COLLECTION, collectionGroup = CollectionGroupKind.SERVICE)
        )

        // "trending_tv" and the genre rail were removed in Settings > Catalogs.
        val categories = listOf(
            Category(id = "continue_watching", title = "Continue Watching", items = listOf(MediaItem(1, "CW Movie", mediaType = MediaType.MOVIE))),
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(2, "Top Movie", mediaType = MediaType.MOVIE))),
            Category(id = "trending_tv", title = "Trending in Shows", items = listOf(MediaItem(3, "Top Show", mediaType = MediaType.TV))),
            Category(id = "collection_row_service", title = "Services", items = listOf(MediaItem(4, "Netflix", mediaType = MediaType.MOVIE))),
            Category(id = "collection_row_genre", title = "Genres", items = listOf(MediaItem(5, "Action", mediaType = MediaType.MOVIE)))
        )

        val result = dropCategoriesMissingFromSavedCatalogs(categories, savedCatalogs)

        assertThat(result.map { it.id }).containsExactly(
            "continue_watching",
            "trending_movies",
            "collection_row_service"
        ).inOrder()
    }

    @Test
    fun `dropCategoriesMissingFromSavedCatalogs keeps rows that are not catalogs of their own`() {
        val savedCatalogs = listOf(
            CatalogConfig(id = "trending_movies", title = "Trending in Movies", sourceType = CatalogSourceType.MDBLIST, isPreinstalled = true)
        )

        val categories = listOf(
            Category(id = "continue_watching", title = "Continue Watching", items = listOf(MediaItem(1, "CW Movie", mediaType = MediaType.MOVIE))),
            Category(id = "favorite_tv", title = "Favorite TV", items = listOf(MediaItem(2, "Channel 1", mediaType = MediaType.TV))),
            Category(id = "sports", title = "Sports", items = listOf(MediaItem(3, "Live Match", mediaType = MediaType.MOVIE))),
            Category(id = "popular_live_tv", title = "Popular Live", items = listOf(MediaItem(4, "Live Channel", mediaType = MediaType.MOVIE))),
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(5, "Top Movie", mediaType = MediaType.MOVIE)))
        )

        val result = dropCategoriesMissingFromSavedCatalogs(categories, savedCatalogs)

        assertThat(result.map { it.id }).containsExactly(
            "continue_watching",
            "favorite_tv",
            "sports",
            "popular_live_tv",
            "trending_movies"
        ).inOrder()
    }

    @Test
    fun `dropCategoriesMissingFromSavedCatalogs keeps everything when the saved list is empty`() {
        val categories = listOf(
            Category(id = "trending_movies", title = "Trending in Movies", items = listOf(MediaItem(1, "Top Movie", mediaType = MediaType.MOVIE)))
        )

        val result = dropCategoriesMissingFromSavedCatalogs(categories, emptyList())

        assertThat(result).isEqualTo(categories)
    }
}
