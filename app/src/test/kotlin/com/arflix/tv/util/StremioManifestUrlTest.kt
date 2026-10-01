package com.arflix.tv.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioManifestUrlTest {

    @Test
    fun `detects addon manifest links`() {
        assertTrue(CatalogUrlParser.isStremioManifestUrl("https://example.com/manifest.json"))
        // Configured addons carry their config in the path before the manifest.
        assertTrue(
            CatalogUrlParser.isStremioManifestUrl(
                "https://aiostreams.example.cloud/stremio/0000/eyJpIjoiYWJjIn0/manifest.json"
            )
        )
        assertTrue(CatalogUrlParser.isStremioManifestUrl("stremio://example.com/manifest.json"))
        assertTrue(CatalogUrlParser.isStremioManifestUrl("  example.com/manifest.json  "))
    }

    @Test
    fun `leaves catalog urls alone`() {
        assertFalse(CatalogUrlParser.isStremioManifestUrl("https://trakt.tv/users/me/lists/favourites"))
        assertFalse(CatalogUrlParser.isStremioManifestUrl("https://mdblist.com/lists/user/list"))
        assertFalse(CatalogUrlParser.isStremioManifestUrl("https://www.themoviedb.org/list/8291245"))
        // A pack manifest is a different document and keeps its own flow.
        assertFalse(CatalogUrlParser.isStremioManifestUrl("https://example.com/pack-manifest.json"))
        assertFalse(CatalogUrlParser.isStremioManifestUrl(""))
    }
}
