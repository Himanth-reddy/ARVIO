package com.arflix.tv.data.repository

import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.data.repository.CloudSyncRepository.CloudProfileSettings
import com.arflix.tv.ui.screens.details.DetailsUiState
import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class AnimeStructuringPreferenceTest {
    private val gson = Gson()

    @Test
    fun animeStructuringDefaultsToBroadcastInInitialUiStates() {
        assertThat(SettingsUiState().animeStructuringStyle).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(DetailsUiState().animeStructuringStyle).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(DetailsUiState().hasAlternateAnimeStructure).isFalse()
    }

    @Test
    fun animeStructuringStyleFromIdResolvesCorrectly() {
        assertThat(AnimeStructuringStyle.fromId("broadcast")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("BROADCAST")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("standard")).isEqualTo(AnimeStructuringStyle.STANDARD)
        assertThat(AnimeStructuringStyle.fromId("STANDARD")).isEqualTo(AnimeStructuringStyle.STANDARD)
        // Backwards compatibility with legacy keys
        assertThat(AnimeStructuringStyle.fromId("japanese")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("western")).isEqualTo(AnimeStructuringStyle.STANDARD)
        assertThat(AnimeStructuringStyle.fromId(null)).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("invalid")).isEqualTo(AnimeStructuringStyle.BROADCAST)
    }

    @Test
    fun newCloudProfileDefaultsToBroadcast() {
        assertThat(CloudProfileSettings().animeEpisodeStructuring).isEqualTo("broadcast")
    }

    @Test
    fun cloudProfileWithoutPreferenceDefaultsToBroadcast() {
        val restored = gson.fromJson("{}", CloudProfileSettings::class.java)
        assertThat(restored.animeEpisodeStructuring).isEqualTo("broadcast")
    }

    @Test
    fun explicitStandardSurvivesCloudRoundTrip() {
        val original = CloudProfileSettings(animeEpisodeStructuring = "standard")
        val restored = gson.fromJson(gson.toJson(original), CloudProfileSettings::class.java)

        assertThat(restored.animeEpisodeStructuring).isEqualTo("standard")
        assertThat(AnimeStructuringStyle.fromId(restored.animeEpisodeStructuring)).isEqualTo(AnimeStructuringStyle.STANDARD)
    }

    @Test
    fun explicitBroadcastSurvivesCloudRoundTrip() {
        val original = CloudProfileSettings(animeEpisodeStructuring = "broadcast")
        val restored = gson.fromJson(gson.toJson(original), CloudProfileSettings::class.java)

        assertThat(restored.animeEpisodeStructuring).isEqualTo("broadcast")
        assertThat(AnimeStructuringStyle.fromId(restored.animeEpisodeStructuring)).isEqualTo(AnimeStructuringStyle.BROADCAST)
    }

    @Test
    fun legacyKeysSurviveCloudRoundTrip() {
        val legacy = CloudProfileSettings(animeEpisodeStructuring = "western")
        val restored = gson.fromJson(gson.toJson(legacy), CloudProfileSettings::class.java)
        assertThat(AnimeStructuringStyle.fromId(restored.animeEpisodeStructuring)).isEqualTo(AnimeStructuringStyle.STANDARD)
    }
}
