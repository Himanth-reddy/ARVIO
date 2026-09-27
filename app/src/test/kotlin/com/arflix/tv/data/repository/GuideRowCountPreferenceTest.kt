package com.arflix.tv.data.repository

import com.arflix.tv.data.repository.CloudSyncRepository.CloudProfileSettings
import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test

class GuideRowCountPreferenceTest {
    private val gson = Gson()

    @Test
    fun initialUiUsesAutoRows() {
        assertThat(SettingsUiState().guideRowCount).isEqualTo(0)
    }

    @Test
    fun missingCloudFieldDoesNotRequestALocalOverwrite() {
        assertThat(CloudProfileSettings().guideRowCount).isNull()
        assertThat(gson.fromJson("{}", CloudProfileSettings::class.java).guideRowCount).isNull()
    }

    @Test
    fun explicitAutoIsNotTreatedAsAMissingPreference() {
        val restored = roundTrip(0)
        assertThat(restored.guideRowCount).isEqualTo(0)
    }

    @Test
    fun everySelectableRowCountSurvivesCloudRoundTrip() {
        for (count in 6..10) {
            assertThat(roundTrip(count).guideRowCount).isEqualTo(count)
        }
    }

    @Test
    fun profileSettingsKeepIndependentRowCounts() {
        val profiles = mapOf(
            "first" to CloudProfileSettings(guideRowCount = 6),
            "second" to CloudProfileSettings(guideRowCount = 10),
            "legacy" to CloudProfileSettings(),
        )
        val type = object : TypeToken<Map<String, CloudProfileSettings>>() {}.type
        val restored: Map<String, CloudProfileSettings> = gson.fromJson(gson.toJson(profiles), type)
        assertThat(restored.getValue("first").guideRowCount).isEqualTo(6)
        assertThat(restored.getValue("second").guideRowCount).isEqualTo(10)
        assertThat(restored.getValue("legacy").guideRowCount).isNull()
    }

    private fun roundTrip(count: Int): CloudProfileSettings = gson.fromJson(
        gson.toJson(CloudProfileSettings(guideRowCount = count)),
        CloudProfileSettings::class.java,
    )
}
