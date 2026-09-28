package com.arflix.tv.ui.screens.collections

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CollectionPresentationTest {
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
