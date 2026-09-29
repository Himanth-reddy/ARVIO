package com.arflix.tv.ui.screens.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.CollectionTileShape
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeCollectionFocusDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countryOutlineStaysAnchoredThroughoutBothScrollDirections() {
        val focus = showRow(CollectionTileShape.LANDSCAPE)
        val outline = compose.onNodeWithTag("home_focus_collection_row_countries", useUnmergedTree = true)
        val bounds = outline.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionLeft).forEach { key ->
            compose.onRoot().performKeyInput { pressKey(key) }
            repeat(14) {
                compose.mainClock.advanceTimeByFrame()
                assertEquals(bounds, outline.fetchSemanticsNode().boundsInRoot)
            }
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(1, focus.currentItemIndex) }
    }

    @Test fun portraitCollectionOutlineIsNotClippedByLandscapeRowPreference() {
        showRow(CollectionTileShape.POSTER)
        val row = compose.onNodeWithTag("home_row_collection_row_countries", useUnmergedTree = true)
        val outline = compose.onNodeWithTag("home_focus_collection_row_countries", useUnmergedTree = true)
        row.assertHeightIsEqualTo(245.dp)
        outline.assertHeightIsEqualTo(157.5.dp)
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val focusBounds = outline.fetchSemanticsNode().boundsInRoot
        assertTrue(focusBounds.bottom < rowBounds.bottom)
    }

    private fun showRow(shape: CollectionTileShape): HomeFocusState {
        val focus = HomeFocusState().apply { userHasNavigated = true }
        val category = Category("collection_row_countries", "International Cinema", (1..40).map {
            MediaItem(it, "Country $it", status = "collection:country_$it", collectionTileShape = shape)
        })
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = listOf(category), cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = false, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, _, _, _ -> }, onNavigateToCollection = {},
                    onNavigateToSearch = {}, onNavigateToWatchlist = {}, onNavigateToTv = { _, _ -> },
                    onNavigateToSettings = {}, onSwitchProfile = {}, onExitApp = {},
                    onOpenContextMenu = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        return focus
    }
}
