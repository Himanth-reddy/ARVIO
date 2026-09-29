package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class HomeHeroStateTest {
    private val watching = MediaItem(42, "Continue Watching", mediaType = MediaType.TV)
    private val collection = MediaItem(7, "International Cinema", status = "collection:countries")

    @Test fun `late catalog and logo completions cannot replace focused collection`() {
        val focused = HomeUiState(
            heroItem = collection.copy(backdrop = "collection-hero.jpg"),
            heroLogoUrl = "collection-logo.png", heroOverviewOverride = "Collection description",
            previousHeroItem = watching, heroTrailerKey = "collection-video"
        )
        assertSame(focused, focused.withBackgroundHero(watching, null))
        assertSame(focused, focused.withBackgroundHero(watching, "watching-logo.png"))
    }

    @Test fun `collection without logo never borrows the startup title logo`() {
        val focused = HomeUiState(heroItem = collection)
        assertSame(focused, focused.withBackgroundHero(watching, "startup.png"))
        assertNull(homeHeroLogo(collection, null, watching, "startup.png"))
    }

    @Test fun `startup still seeds hero and late matching logo still loads`() {
        val seeded = HomeUiState().withBackgroundHero(watching, null)
        assertEquals(watching, seeded.heroItem)
        val decorated = seeded.withBackgroundHero(watching, "logo.png")
        assertEquals("logo.png", decorated.heroLogoUrl)
        assertEquals("logo.png", homeHeroLogo(watching, null, watching, "logo.png"))
        assertEquals("current.png", homeHeroLogo(collection, "current.png", watching, "old.png"))
    }

    @Test fun `metadata on same item is not rolled back by stale catalog data`() {
        val focused = HomeUiState(heroItem = watching.copy(overview = "Full synopsis"), heroLogoUrl = "new.png")
        assertSame(focused, focused.withBackgroundHero(watching, "old.png"))
    }

    @Test fun `id collisions across types and collection sources are not matches`() {
        assertFalse(watching.isSameHomeHero(watching.copy(mediaType = MediaType.MOVIE)))
        assertFalse(collection.isSameHomeHero(collection.copy(status = null)))
        assertFalse(collection.isSameHomeHero(collection.copy(status = "collection:genres")))
        assertTrue(watching.isSameHomeHero(watching.copy(status = "Returning Series")))
    }

    @Test fun `placeholders cannot seed a hero`() {
        val empty = HomeUiState()
        assertSame(empty, empty.withBackgroundHero(watching.copy(isPlaceholder = true), null))
        assertSame(empty, empty.withBackgroundHero(null, "orphan.png"))
    }
}
