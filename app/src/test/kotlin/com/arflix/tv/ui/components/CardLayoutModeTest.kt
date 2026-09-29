package com.arflix.tv.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey

class CardLayoutModeTest {
    @Test
    fun `batch row modes preserve profile overrides and legacy fallback`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("card_layout_mode") to "Poster",
            stringPreferencesKey("profile_p1_card_layout_mode") to "Landscape",
            profileCatalogueRowLayoutModeKey("p1", "home:movies") to "Poster"
        )
        val keys = listOf("home:movies", "home:shows")
        assertThat(catalogueRowLayoutModes("p1", preferences, keys)).containsExactly(
            "home:movies", CardLayoutMode.POSTER, "home:shows", CardLayoutMode.LANDSCAPE
        )
        assertThat(catalogueRowLayoutModes("p2", preferences, keys)).containsExactly(
            "home:movies", CardLayoutMode.POSTER, "home:shows", CardLayoutMode.POSTER
        )
        preferences[profileCatalogueRowLayoutModeKey("p1", "home:movies")] = "Landscape"
        assertThat(catalogueRowLayoutModes("p1", preferences, keys)["home:movies"])
            .isEqualTo(CardLayoutMode.LANDSCAPE)
        assertThat(catalogueRowLayoutModes("p1", preferences, emptyList())).isEmpty()
    }

    @Test
    fun `normalizes card layout values`() {
        assertThat(normalizeCardLayoutMode("Poster")).isEqualTo(CARD_LAYOUT_MODE_POSTER)
        assertThat(normalizeCardLayoutMode("poster")).isEqualTo(CARD_LAYOUT_MODE_POSTER)
        assertThat(normalizeCardLayoutMode("Landscape")).isEqualTo(CARD_LAYOUT_MODE_LANDSCAPE)
        assertThat(normalizeCardLayoutMode("unexpected")).isEqualTo(CARD_LAYOUT_MODE_LANDSCAPE)
        assertThat(normalizeCardLayoutMode(null)).isEqualTo(CARD_LAYOUT_MODE_LANDSCAPE)
    }

    @Test
    fun `normalizes catalogue row keys for stable preferences`() {
        assertThat(normalizeCatalogueRowLayoutKey(" Home:Trending Movies "))
            .isEqualTo("home:trending_movies")
        assertThat(normalizeCatalogueRowLayoutKey("")).isEqualTo("default")
    }

    @Test
    fun `builds and parses profile row layout preference names`() {
        val preferenceName = profileCatalogueRowLayoutPreferenceName(
            profileId = "p1",
            rowKey = "Home:Trending Movies"
        )

        assertThat(preferenceName)
            .isEqualTo("profile_p1_catalogue_row_layout_home:trending_movies")
        assertThat(catalogueRowLayoutKeyFromPreferenceName("p1", preferenceName))
            .isEqualTo("home:trending_movies")
        assertThat(catalogueRowLayoutKeyFromPreferenceName("p2", preferenceName))
            .isNull()
    }

    @Test
    fun `toggles between poster and landscape`() {
        assertThat(toggledCardLayoutMode(CardLayoutMode.LANDSCAPE))
            .isEqualTo(CARD_LAYOUT_MODE_POSTER)
        assertThat(toggledCardLayoutMode(CardLayoutMode.POSTER))
            .isEqualTo(CARD_LAYOUT_MODE_LANDSCAPE)
    }
}
