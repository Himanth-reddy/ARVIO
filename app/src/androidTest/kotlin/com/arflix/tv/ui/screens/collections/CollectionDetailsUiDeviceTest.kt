package com.arflix.tv.ui.screens.collections

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.arflix.tv.data.model.*
import com.arflix.tv.di.RepositoryAccessEntryPoint
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real TMDB cards in the production browser; no profile changes or media playback. */
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class CollectionDetailsUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun liveArtworkFocusScrollAndOpen() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("collectionLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = DeviceType.valueOf(InstrumentationRegistry.getArguments().getString("collectionDevice") ?: "TV")
        val posters = InstrumentationRegistry.getArguments().getString("collectionPosters") != "false"
        val repository = EntryPointAccessors.fromApplication(context, RepositoryAccessEntryPoint::class.java).mediaRepository()
        val catalog = CatalogConfig("collection-ui-test", "Science fiction", CatalogSourceType.PREINSTALLED,
            kind = CatalogKind.COLLECTION,
            collectionDescription = "New worlds, speculative futures and journeys beyond our own.",
            collectionSources = listOf(CollectionSourceConfig(kind = CollectionSourceKind.TMDB_GENRE,
                mediaType = "movie", tmdbGenreId = 878, sortBy = "popularity.desc")))
        val page = repository.loadCollectionCatalogPage(catalog, 0, 30, MediaType.MOVIE)
        assertTrue("Real collection should contain enough cards to scroll", page.items.size >= 20)
        val details = repository.getMovieDetails(page.items.first().id)
        val items = page.items.map { if (it.id == details?.id) details else it }.filterNotNull()
        var opened: MediaItem? = null
        var nearEnd = false
        val selected = mutableStateOf(CollectionTab.MOVIES)
        compose.runOnUiThread {
            compose.activity.actionBar?.hide()
            androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,
                compose.activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides device) {
                val firstTab = remember { FocusRequester() }
                val secondTab = remember { FocusRequester() }
                val mobile = device.isTouchDevice()
                val inputMode = LocalInputModeManager.current
                val width = LocalConfiguration.current.screenWidthDp - if (mobile) 40 else 84
                val gap = if (posters) 18 else 14
                val minimum = if (posters) { if (mobile) 106 else 128 } else { if (mobile) 160 else 220 }
                val columns = ((width + gap) / (minimum + gap)).coerceIn(1, 8)
                val cardWidth = ((width - gap * (columns - 1)).toFloat() / columns).dp
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(300)
                    if (!mobile) {
                        inputMode.requestInputMode(InputMode.Keyboard)
                        firstTab.requestFocus()
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    CollectionItemsGrid(catalog, {}, items, columns, cardWidth, posters,
                        rememberTvLazyGridState(), -1, {}, true, true, emptyMap(), selected.value,
                        firstTab, secondTab, false, { selected.value = it }, { opened = it },
                        { _, _ -> }, {}, {}, { nearEnd = true }, false, false, "", 8.dp)
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag("collection_spotlight_title").assertTextEquals(items.first().title)
        compose.onNodeWithTag("collection_grid").assertIsDisplayed()
        val gridTop = compose.onNodeWithTag("collection_grid").fetchSemanticsNode().boundsInRoot.top
        val heroBottom = compose.onNodeWithTag("collection_spotlight").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Hero and cards cannot overlap", gridTop >= heroBottom)
        if (device == DeviceType.TV) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.mainClock.advanceTimeBy(200)
            compose.waitUntil(3000) {
                compose.onNodeWithTag("collection_spotlight_title").fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.Text].first().text == items[1].title
            }
            assertEquals(gridTop, compose.onNodeWithTag("collection_grid").fetchSemanticsNode().boundsInRoot.top)
            compose.onRoot().performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(items[1].id, opened?.id) }
            repeat(14) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
            compose.runOnIdle { assertTrue("Pagination must remain reachable", nearEnd) }
            repeat(14) { compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) } }
        } else {
            compose.onNodeWithTag("collection_item_0").onChildren().filter(hasClickAction()).onFirst().performClick()
            compose.runOnIdle { assertEquals(items.first().id, opened?.id) }
            compose.onNodeWithTag("collection_grid").performTouchInput { swipeUp() }
            compose.onNodeWithTag("collection_grid").performTouchInput { swipeDown() }
        }
        // Let real images decode before recording the visual result (not a loading benchmark).
        Thread.sleep(2500)
        compose.waitForIdle()
        val suffix = if (posters) "" else "-landscape"
        val file = File(context.getExternalFilesDir(null), "collection-redesign-${device.name}$suffix.png")
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Collection UI passed: $device, ${items.size} real cards; screenshot ${file.name}\n") })
    }
}
