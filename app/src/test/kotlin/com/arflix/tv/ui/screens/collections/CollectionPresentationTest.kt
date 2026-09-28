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
}
