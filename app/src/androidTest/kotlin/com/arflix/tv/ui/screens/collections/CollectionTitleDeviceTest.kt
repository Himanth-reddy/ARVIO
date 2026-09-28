package com.arflix.tv.ui.screens.collections

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectionTitleDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoReplacesTextWithoutMovingLayoutAndMissingLogoRestoresTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "collection-title-test.png")
        val bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val url = mutableStateOf<String?>(null)
        compose.setContent {
            CollectionTitle("Example title", url.value, false, false,
                Modifier.width(420.dp).testTag("title-slot"))
        }
        compose.onNodeWithTag("collection_spotlight_title").assertTextEquals("Example title")
        val initial = compose.onNodeWithTag("title-slot").getUnclippedBoundsInRoot()
        compose.runOnIdle { url.value = file.toURI().toString() }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("collection_spotlight_title").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("collection_clearlogo").assertIsDisplayed()
        assertEquals(initial, compose.onNodeWithTag("title-slot").getUnclippedBoundsInRoot())
        compose.runOnIdle { url.value = File(context.cacheDir, "missing-logo.png").toURI().toString() }
        compose.onNodeWithTag("collection_spotlight_title").assertTextEquals("Example title")
        assertEquals(initial, compose.onNodeWithTag("title-slot").getUnclippedBoundsInRoot())
        compose.runOnIdle { url.value = null }
        compose.onNodeWithTag("collection_clearlogo").assertDoesNotExist()
        compose.onNodeWithTag("collection_spotlight_title").assertTextEquals("Example title")
        file.delete()
    }
}
