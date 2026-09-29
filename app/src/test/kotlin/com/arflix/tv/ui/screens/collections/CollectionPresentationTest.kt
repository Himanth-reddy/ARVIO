package com.arflix.tv.ui.screens.collections

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CollectionPresentationTest {
    @Test fun `TV cards retain home sizes instead of stretching to fill columns`() {
        assertEquals(CollectionGridLayout(7, 105), collectionGridLayout(960, false, true))
        assertEquals(CollectionGridLayout(4, 210), collectionGridLayout(960, false, false))
        assertEquals(105, collectionGridLayout(1280, false, true).cardWidthDp)
        assertEquals(210, collectionGridLayout(1280, false, false).cardWidthDp)
    }

    @Test fun `touch grids fill available width and tiny windows cannot overflow`() {
        assertEquals(CollectionGridLayout(3, 115), collectionGridLayout(411, true, true))
        assertEquals(CollectionGridLayout(2, 179), collectionGridLayout(411, true, false))
        assertEquals(CollectionGridLayout(1, 60), collectionGridLayout(100, true, false))
        for (screen in listOf(320, 360, 390, 411, 600, 800, 1280)) {
            for (posters in listOf(false, true)) {
                val layout = collectionGridLayout(screen, true, posters)
                val used = layout.columns * layout.cardWidthDp + (layout.columns - 1) * 12
                assertTrue(used <= screen - 40)
                assertTrue(screen - 40 - used < layout.columns)
            }
        }
    }

    @Test fun `budget is factual USD only for movies with known positive budget`() {
        val movie = MediaItem(1, "Movie", budget = 150_000_000L)
        assertEquals("$150,000,000", collectionBudget(movie, Locale.US))
        assertNull(collectionBudget(movie.copy(budget = 0)))
        assertNull(collectionBudget(movie.copy(budget = -1)))
        assertNull(collectionBudget(movie.copy(budget = null)))
        assertNull(collectionBudget(movie.copy(mediaType = MediaType.TV)))
        assertNull(collectionBudget(null))
    }

    @Test fun `ratings prefer imdb and label tmdb accurately`() {
        val item = MediaItem(1, "Movie", imdbRating = "8.2", tmdbRating = "7.9")
        assertEquals("IMDb 8.2", collectionRating(item))
        assertEquals("TMDB 7.9", collectionRating(item.copy(imdbRating = "")))
        assertNull(collectionRating(item.copy(imdbRating = "0", tmdbRating = "0")))
        assertNull(collectionRating(item.copy(imdbRating = "NaN", tmdbRating = "11")))
        assertNull(collectionRating(item.copy(imdbRating = "-1", tmdbRating = "unknown")))
        assertNull(collectionRating(MediaItem(1, "Movie", rating = "PG-13")))
    }

    @Test fun `compact budget keeps small amounts and scales large amounts accurately`() {
        val movie = MediaItem(1, "Movie", budget = 225_000_000L)
        assertEquals("$225M", collectionCompactBudget(movie, Locale.US))
        assertEquals("$12.5M", collectionCompactBudget(movie.copy(budget = 12_500_000), Locale.US))
        assertEquals("$1.2B", collectionCompactBudget(movie.copy(budget = 1_200_000_000), Locale.US))
        assertEquals("$750,000", collectionCompactBudget(movie.copy(budget = 750_000), Locale.US))
        assertNull(collectionCompactBudget(movie.copy(mediaType = MediaType.TV)))
        assertNull(collectionCompactBudget(movie.copy(budget = 0)))
    }
}
