package com.arflix.tv.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the keyed lazy-item pattern used by Home and Search without live catalog services. */
class KeyedRowRestorationDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val showingRows = mutableStateOf(true)
    private var firstRow: LazyListState? = null

    private fun showRows(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val holder = rememberSaveableStateHolder()
            if (showingRows.value) {
                holder.SaveableStateProvider("catalog") {
                    LazyColumn(Modifier.size(320.dp, 180.dp).testTag("rows")) {
                        items((0..20).toList(), key = { "category-$it" }) { category ->
                            val row = rememberLazyListState()
                            SideEffect { if (category == 0) firstRow = row }
                            LazyRow(
                                state = row,
                                modifier = Modifier.height(80.dp).testTag("row-$category"),
                            ) {
                                items(40) { item ->
                                    Box(Modifier.size(100.dp, 80.dp)) { BasicText("$category/$item") }
                                }
                            }
                        }
                    }
                }
            }
        }
        return restoration
    }

    @Test
    fun horizontalPositionSurvivesRowDisposalDuringVerticalScrolling() {
        showRows()
        compose.onNodeWithTag("row-0").performScrollToIndex(12)
        compose.onNodeWithTag("rows").performScrollToIndex(18)
        compose.onNodeWithTag("rows").performScrollToIndex(0)
        compose.runOnIdle { assertEquals(12, firstRow!!.firstVisibleItemIndex) }
    }

    @Test
    fun horizontalPositionSurvivesNavigationAndSavedStateRestore() {
        val restoration = showRows()
        compose.onNodeWithTag("row-0").performScrollToIndex(12)
        compose.runOnIdle { showingRows.value = false }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { showingRows.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(12, firstRow!!.firstVisibleItemIndex) }
    }
}
