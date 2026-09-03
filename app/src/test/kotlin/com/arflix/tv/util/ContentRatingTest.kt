package com.arflix.tv.util

import com.arflix.tv.data.api.TmdbContentRatingResult
import com.arflix.tv.data.api.TmdbContentRatingsResponse
import com.arflix.tv.data.api.TmdbReleaseDate
import com.arflix.tv.data.api.TmdbReleaseDatesResponse
import com.arflix.tv.data.api.TmdbReleaseDatesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentRatingTest {

    private fun movie(vararg countries: Pair<String, List<Pair<String?, Int>>>) =
        TmdbReleaseDatesResponse(
            results = countries.map { (country, releases) ->
                TmdbReleaseDatesResult(
                    iso31661 = country,
                    releaseDates = releases.map { (certification, type) ->
                        TmdbReleaseDate(certification = certification, type = type)
                    }
                )
            }
        )

    private fun tv(vararg countries: Pair<String, String?>) =
        TmdbContentRatingsResponse(
            results = countries.map { (country, rating) ->
                TmdbContentRatingResult(iso31661 = country, rating = rating)
            }
        )

    // === country from the content language ===

    @Test
    fun `content language yields its country`() {
        assertEquals("DE", ContentRating.regionOf("de-DE"))
        assertEquals("BR", ContentRating.regionOf("pt-BR"))
        assertEquals("US", ContentRating.regionOf("en-US"))
    }

    @Test
    fun `content language without a usable country falls back to US`() {
        assertEquals("US", ContentRating.regionOf("en"))
        assertEquals("US", ContentRating.regionOf("zh-Hans"))
        assertEquals("US", ContentRating.regionOf(""))
        assertEquals("US", ContentRating.regionOf(null))
    }

    @Test
    fun `lower case country in the content language still matches`() {
        assertEquals("DE", ContentRating.regionOf("de-de"))
    }

    // === labelling rule ===

    @Test
    fun `a bare number gets the rating system of its country`() {
        assertEquals("FSK 16", ContentRating.forMovie(movie("DE" to listOf("16" to 3)), "de-DE"))
        assertEquals("BBFC 15", ContentRating.forMovie(movie("GB" to listOf("15" to 3)), "en-GB"))
    }

    @Test
    fun `a bare number in a country without a known system gets the country code`() {
        assertEquals("FR 12", ContentRating.forMovie(movie("FR" to listOf("12" to 3)), "fr-FR"))
    }

    @Test
    fun `a value that speaks for itself stays untouched`() {
        assertEquals("R", ContentRating.forMovie(movie("US" to listOf("R" to 3)), "en-US"))
        assertEquals("PG-13", ContentRating.forMovie(movie("US" to listOf("PG-13" to 3)), "en-US"))
        assertEquals("TV-MA", ContentRating.forTv(tv("US" to "TV-MA"), "en-US"))
        assertEquals("12A", ContentRating.forMovie(movie("GB" to listOf("12A" to 3)), "en-GB"))
        assertEquals(
            "Tous publics",
            ContentRating.forMovie(movie("FR" to listOf("Tous publics" to 3)), "fr-FR")
        )
        assertEquals("-12", ContentRating.forMovie(movie("FR" to listOf("-12" to 3)), "fr-FR"))
    }

    // === US fallback (E2) ===

    @Test
    fun `without a national certification the US value is shown and marked`() {
        assertEquals(
            "US · R",
            ContentRating.forMovie(movie("US" to listOf("R" to 3)), "de-DE")
        )
        assertEquals(
            "US · TV-MA",
            ContentRating.forTv(tv("US" to "TV-MA"), "de-DE")
        )
    }

    @Test
    fun `a US viewer sees the US value without a marking`() {
        assertEquals("R", ContentRating.forMovie(movie("US" to listOf("R" to 3)), "en-US"))
    }

    @Test
    fun `a national certification wins over the US one`() {
        val response = movie(
            "US" to listOf("R" to 3),
            "DE" to listOf("16" to 3)
        )
        assertEquals("FSK 16", ContentRating.forMovie(response, "de-DE"))
    }

    // === empty certification counts as absent ===

    @Test
    fun `an empty certification is treated as absent and falls back to US`() {
        val response = movie(
            "DE" to listOf("" to 3),
            "US" to listOf("PG-13" to 3)
        )
        assertEquals("US · PG-13", ContentRating.forMovie(response, "de-DE"))
    }

    @Test
    fun `a blank certification is treated as absent`() {
        val response = movie("DE" to listOf("   " to 3))
        assertNull(ContentRating.forMovie(response, "de-DE"))
    }

    @Test
    fun `an empty TV rating is treated as absent`() {
        assertNull(ContentRating.forTv(tv("DE" to ""), "de-DE"))
    }

    @Test
    fun `an empty certification is skipped in favour of a filled one for the same country`() {
        val response = movie("DE" to listOf("" to 3, "12" to 5))
        assertEquals("FSK 12", ContentRating.forMovie(response, "de-DE"))
    }

    // === nothing at all hides the badge ===

    @Test
    fun `no certification anywhere yields null`() {
        assertNull(ContentRating.forMovie(movie("FR" to listOf("12" to 3)), "de-DE"))
        assertNull(ContentRating.forMovie(movie(), "de-DE"))
        assertNull(ContentRating.forMovie(null, "de-DE"))
        assertNull(ContentRating.forTv(null, "de-DE"))
        assertNull(ContentRating.forTv(tv(), "de-DE"))
    }

    @Test
    fun `a null certification yields null`() {
        assertNull(ContentRating.forMovie(movie("DE" to listOf(null to 3)), "de-DE"))
        assertNull(ContentRating.forTv(tv("DE" to null), "de-DE"))
    }

    // === several certifications for one country (movies only) ===

    @Test
    fun `the theatrical certification wins over TV and streaming`() {
        val response = movie("DE" to listOf("12" to 5, "16" to 3, "6" to 4))
        assertEquals("FSK 16", ContentRating.forMovie(response, "de-DE"))
    }

    @Test
    fun `without a theatrical release the lowest release type wins`() {
        val response = movie("DE" to listOf("18" to 6, "12" to 4))
        assertEquals("FSK 12", ContentRating.forMovie(response, "de-DE"))
    }

    @Test
    fun `an entry without a release type does not beat a known one`() {
        val response = movie("DE" to listOf("18" to Int.MAX_VALUE, "12" to 4))
        assertEquals("FSK 12", ContentRating.forMovie(response, "de-DE"))
    }

    @Test
    fun `surrounding whitespace is trimmed from the certification`() {
        assertEquals("FSK 16", ContentRating.forMovie(movie("DE" to listOf(" 16 " to 3)), "de-DE"))
        assertEquals("TV-MA", ContentRating.forTv(tv("US" to " TV-MA "), "en-US"))
    }
}
