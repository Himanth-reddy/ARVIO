package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeRowStateTest {

    @Test
    fun `row key depends on catalog identity and not its position`() {
        assertThat(stableHomeRowKey("tv", "trending_movies"))
            .isEqualTo("tv_home_row_trending_movies")
        assertThat(stableHomeRowKey("mobile", "trending_movies"))
            .isEqualTo("mobile_home_row_trending_movies")
    }

    @Test
    fun `focused catalog follows its id when rows are inserted above it`() {
        val updatedRows = listOf("continue_watching", "sports", "trending_movies", "trending_tv")

        val resolvedIndex = resolveHomeCategoryIndex(
            categoryIds = updatedRows,
            preferredCategoryId = "trending_tv",
            fallbackIndex = 2
        )

        assertThat(resolvedIndex).isEqualTo(3)
    }

    @Test
    fun `focused catalog follows its id when rows are reordered`() {
        val reorderedRows = listOf("trending_tv", "continue_watching", "trending_movies")

        val resolvedIndex = resolveHomeCategoryIndex(
            categoryIds = reorderedRows,
            preferredCategoryId = "trending_movies",
            fallbackIndex = 0
        )

        assertThat(resolvedIndex).isEqualTo(2)
    }

    @Test
    fun `missing focused catalog keeps a clamped fallback row`() {
        val resolvedIndex = resolveHomeCategoryIndex(
            categoryIds = listOf("continue_watching", "trending_movies"),
            preferredCategoryId = "removed_catalog",
            fallbackIndex = 8
        )

        assertThat(resolvedIndex).isEqualTo(1)
    }

    @Test
    fun `poster keys do not change when unrelated items are inserted`() {
        val original = listOf(mediaItem(1), mediaItem(2), mediaItem(3))
        val updated = listOf(mediaItem(99)) + original

        val originalKeys = stableHomeRowItemKeys("trending", original)
        val updatedKeys = stableHomeRowItemKeys("trending", updated)

        assertThat(updatedKeys.drop(1)).containsExactlyElementsIn(originalKeys).inOrder()
    }

    @Test
    fun `duplicate poster keys remain unique and deterministic`() {
        val duplicates = listOf(mediaItem(7), mediaItem(7), mediaItem(7))

        val keys = stableHomeRowItemKeys("trending", duplicates)

        assertThat(keys).containsExactly(
            "MOVIE-7",
            "MOVIE-7#duplicate1",
            "MOVIE-7#duplicate2"
        ).inOrder()
    }

    @Test
    fun `focused poster follows its identity when catalog is reordered`() {
        val reorderedKeys = stableHomeRowItemKeys(
            "trending",
            listOf(mediaItem(3), mediaItem(1), mediaItem(2))
        )

        val resolvedIndex = resolveHomeItemIndex(
            itemKeys = reorderedKeys,
            preferredItemKey = "MOVIE-2",
            fallbackIndex = 1
        )

        assertThat(resolvedIndex).isEqualTo(2)
    }

    @Test
    fun `empty refreshing catalog resets poster index`() {
        val resolvedIndex = resolveHomeItemIndex(
            itemKeys = emptyList(),
            preferredItemKey = "MOVIE-20",
            fallbackIndex = 19
        )

        assertThat(resolvedIndex).isEqualTo(0)
    }

    @Test
    fun `partial paged catalog clamps poster index to real items`() {
        val resolvedIndex = resolveHomeItemIndex(
            itemKeys = stableHomeRowItemKeys("trending", List(10) { mediaItem(it + 1) }),
            preferredItemKey = "MOVIE-20",
            fallbackIndex = 19
        )

        assertThat(resolvedIndex).isEqualTo(9)
    }

    @Test
    fun `completed shorter catalog clamps poster index`() {
        val resolvedIndex = resolveHomeItemIndex(
            itemKeys = stableHomeRowItemKeys("trending", List(10) { mediaItem(it + 1) }),
            preferredItemKey = "MOVIE-20",
            fallbackIndex = 19
        )

        assertThat(resolvedIndex).isEqualTo(9)
    }

    @Test
    fun `remembered index ignores trailing placeholders`() {
        val items = List(4) { mediaItem(it + 1) } + MediaItem(
            id = -1,
            title = "",
            mediaType = MediaType.MOVIE,
            isPlaceholder = true
        )

        assertThat(clampHomeItemIndex(items, 8)).isEqualTo(3)
    }

    private fun mediaItem(id: Int) = MediaItem(
        id = id,
        title = "Title $id",
        mediaType = MediaType.MOVIE
    )
}
