package com.arflix.tv.ui.screens.home

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.arflix.tv.MainActivity
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.profilesDataStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real HomeScreen + real trailer metadata, without signing in to a customer account. */
class HomeTrailerScreenDeviceTest {
    @Test fun homeMetadataLoadsAnInlinePlayerForTheFocusedTitle() {
        val movies = listOf(
            MediaItem(872585, "Oppenheimer", mediaType = MediaType.MOVIE,
                backdrop = "https://image.tmdb.org/t/p/w1280/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                image = "https://image.tmdb.org/t/p/w500/ptpr0kGAckfQkJeJIt8st5dglvd.jpg"),
            MediaItem(157336, "Interstellar", mediaType = MediaType.MOVIE),
            MediaItem(693134, "Dune: Part Two", mediaType = MediaType.MOVIE)
        )
        val categories = listOf(Category("trending_movies", "Trending Movies", movies))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: HomeViewModel
            lateinit var host: MainActivity
            scenario.onActivity { activity ->
                host = activity
                vm = ViewModelProvider(activity)[HomeViewModel::class.java]
                activity.setContent {
                    CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                        HomeScreen(viewModel = vm, preloadedCategories = categories, preloadedHeroItem = movies.first())
                    }
                }
            }
            await("Home did not resolve a trailer", 45_000) { !vm.uiState.value.heroTrailerKey.isNullOrBlank() }
            await("Home did not mount its inline player", 20_000) {
                var count = 0
                scenario.onActivity { count = webViews(it.window.decorView) }
                count == 1
            }
            Thread.sleep(6000)
            val output = File(host.getExternalFilesDir(null), "trailer-test").apply { mkdirs() }
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(output, "home-real-screen.png"))
            val profileId = runBlocking { host.profilesDataStore.data.first()[stringPreferencesKey("active_profile_id")] }.orEmpty().ifBlank { "default" }
            val cardsKey = booleanPreferencesKey("profile_${profileId}_trailer_in_cards")
            val previous = runBlocking { host.settingsDataStore.data.first()[cardsKey] }
            try {
                runBlocking { host.settingsDataStore.edit { it[cardsKey] = false } }
                await("Hero mode preference did not apply", 10_000) { !vm.uiState.value.trailerInCards }
                await("Hero mode did not mount one player", 20_000) {
                    var count = 0
                    scenario.onActivity { count = webViews(it.window.decorView) }
                    count == 1
                }
                Thread.sleep(6000)
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(output, "home-real-hero.png"))
            } finally {
                runBlocking { host.settingsDataStore.edit { if (previous == null) it.remove(cardsKey) else it[cardsKey] = previous } }
            }
        }
    }
    private fun webViews(view: View): Int = if (view is WebView) 1 else
        if (view is ViewGroup) (0 until view.childCount).sumOf { webViews(view.getChildAt(it)) } else 0
    private fun await(message: String, timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, predicate())
    }
}
