package com.arflix.tv.util

import com.arflix.tv.data.model.CatalogSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbCatalogUrlTest {

    @Test
    fun `detects TMDB urls as a TMDB catalog source`() {
        assertEquals(
            CatalogSourceType.TMDB,
            CatalogUrlParser.detectSource("https://www.themoviedb.org/list/8291245")
        )
        assertEquals(
            CatalogSourceType.TMDB,
            CatalogUrlParser.detectSource("themoviedb.org/collection/1241")
        )
    }

    @Test
    fun `parses every supported page kind`() {
        val list = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/list/8291245")
        assertEquals("list", list?.kind)
        assertEquals(8291245, list?.id)
        assertNull(list?.mediaType)

        val collection = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/collection/1241-harry-potter-collection")
        assertEquals("collection", collection?.kind)
        assertEquals(1241, collection?.id)
        assertEquals("harry-potter-collection", collection?.slug)

        val company = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/company/3-pixar")
        assertEquals("company" to 3, company?.kind to company?.id)

        val network = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/network/49-hbo")
        assertEquals("network" to 49, network?.kind to network?.id)

        val person = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/person/287-brad-pitt")
        assertEquals("person" to 287, person?.kind to person?.id)
    }

    @Test
    fun `keeps the media type scope when the page carries one`() {
        val keyword = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/keyword/9715-superhero/movie")
        assertEquals("keyword", keyword?.kind)
        assertEquals("movie", keyword?.mediaType)

        val genre = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/genre/28-action/tv")
        assertEquals("tv", genre?.mediaType)
    }

    @Test
    fun `accepts a language prefixed path`() {
        val parsed = CatalogUrlParser.parseTmdb("https://www.themoviedb.org/en-US/list/8291245")
        assertEquals("list", parsed?.kind)
        assertEquals(8291245, parsed?.id)
    }

    @Test
    fun `rejects pages that are not catalogs`() {
        // A single movie page is a title, not a catalog.
        assertNull(CatalogUrlParser.parseTmdb("https://www.themoviedb.org/movie/27205-inception"))
        assertNull(CatalogUrlParser.parseTmdb("https://www.themoviedb.org/list/not-a-number"))
        assertNull(CatalogUrlParser.parseTmdb("https://www.themoviedb.org/list/0"))
        assertNull(CatalogUrlParser.parseTmdb("https://www.themoviedb.org/list/-1"))
        assertNull(CatalogUrlParser.parseTmdb("https://www.themoviedb.org/"))
        assertNull(CatalogUrlParser.parseTmdb("https://trakt.tv/lists/123"))
    }
}
