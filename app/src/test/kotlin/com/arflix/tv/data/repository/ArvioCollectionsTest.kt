package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CollectionGroupKind
import com.arflix.tv.data.model.CollectionSourceKind
import org.junit.Assert.*
import org.junit.Test

class ArvioCollectionsTest {
    @Test fun `defaults are independent queries with complete artwork and descriptions`() {
        val entries = ArvioCollections.entries
        assertEquals(43, entries.size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { entry ->
            assertTrue(entry.description!!.length > 25)
            assertTrue(entry.coverImageUrl.startsWith("https://image.tmdb.org/"))
            assertFalse(entry.hideTitle)
            assertTrue(entry.sources.isNotEmpty())
            assertTrue(entry.sources.none { it.kind in setOf(CollectionSourceKind.ADDON_CATALOG,
                CollectionSourceKind.TMDB_LIST, CollectionSourceKind.MDBLIST_PUBLIC, CollectionSourceKind.TRAKT_LIST) })
        }
        val defaults = MediaRepository.buildPreinstalledDefaults()
        assertEquals(43, defaults.count { it.kind == CatalogKind.COLLECTION })
        assertEquals(5, defaults.count { it.kind == CatalogKind.COLLECTION_RAIL })
        assertTrue(defaults.all(CollectionTemplateManifest::isValidCollectionConfig))
        val ids = defaults.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `global disabled choice also hides new rails without hiding custom collections`() {
        val old = setOf("collection_rail_service", "collection_rail_genre", "collection_rail_franchise") +
            ArvioCollections.entries.filter { it.group == CollectionGroupKind.SERVICE }.map { it.id }
        val migrated = ArvioCollections.preserveDisabledDefaults(old)
        assertTrue("collection_rail_featured" in migrated)
        assertTrue("collection_rail_decade" in migrated)
        assertTrue(ArvioCollections.DISABLED_MARKER in migrated)
        assertFalse(migrated.any { it.startsWith("custom_") })
        assertEquals(migrated, ArvioCollections.preserveDisabledDefaults(migrated))
        assertTrue(ArvioCollections.preserveDisabledDefaults(emptySet()).isEmpty())
        val individuallyHidden = setOf("collection_rail_service")
        assertEquals(individuallyHidden, ArvioCollections.preserveDisabledDefaults(individuallyHidden))
    }
}
