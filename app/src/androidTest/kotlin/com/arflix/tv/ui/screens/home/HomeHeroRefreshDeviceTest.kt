package com.arflix.tv.ui.screens.home

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.arflix.tv.MainActivity
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real ViewModel's startup publication after collection focus has changed. */
class HomeHeroRefreshDeviceTest {
    @Test fun lateStartupLogoDoesNotContaminateFocusedCollection() {
        val watching = MediaItem(42, "Continue Watching", mediaType = MediaType.TV)
        val collection = MediaItem(-7001, "International Cinema", status = "collection:countries")
        val categories = listOf(
            Category("continue_watching", "Continue Watching", listOf(watching)),
            Category("collection_row_countries", "International Cinema", listOf(collection))
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[HomeViewModel::class.java]
                viewModel.setPreloadedData(categories, watching, null, emptyMap())
                viewModel.updateHeroItem(collection)
                repeat(30) {
                    viewModel.setPreloadedData(categories, watching, "https://example.com/stale-logo.png", emptyMap())
                    assertEquals(collection, viewModel.uiState.value.heroItem)
                    assertNull(viewModel.uiState.value.heroLogoUrl)
                    assertNull(viewModel.uiState.value.heroTrailerKey)
                }
            }
        }
    }
}
