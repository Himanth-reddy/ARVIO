package com.arflix.tv.ui.screens.tv.live

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GuideRenderingDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_783_000_000_000L / 1_800_000L * 1_800_000L
    private val rows by lazy {
        (0 until 55_000).map { index ->
            IptvChannel(id = "render:$index", name = "Channel $index", group = "News",
                streamUrl = "https://example.test/live", catchupDays = 2).enrichForFastStartup(index + 1)
        }
    }
    private val guide by lazy {
        rows.take(144).associate { row ->
            val programs = (0 until 24).map { slot ->
                val start = now - 120 * 60_000L + slot * 30 * 60_000L
                IptvProgram("Programme ${row.id}:$slot", "A programme description for rendering cost.",
                    start, start + 30 * 60_000L, catchupAvailable = true)
            }
            row.id to IptvNowNext(now = programs[4], next = programs[5],
                recent = programs.take(4), upcoming = programs.drop(5))
        }
    }
    private var focused = ""

    private fun showGuide() {
        val mode = mutableStateOf(EpgGridFocusMode.ChannelList)
        compose.setContent {
            Box(Modifier.width(900.dp).height(400.dp)) {
                EpgGrid(channels = rows.take(144), totalChannelCount = 55_000,
                    clockTickMillis = now, nowNext = guide, selectedChannelId = "render:0",
                    focusSelectedChannelSignal = 1, scrollResetKey = "render-test",
                    favorites = emptySet(), onChannelSelect = {}, gridFocused = true,
                    onChannelFocused = { focused = it.id }, focusMode = mode.value,
                    onEnterEpg = { mode.value = EpgGridFocusMode.Epg },
                    onExitEpg = { mode.value = EpgGridFocusMode.ChannelList })
            }
        }
        compose.waitForIdle()
    }

    @Test fun channelModeDoesNotComposeTheEntireDayForEveryVisibleRow() {
        showGuide()
        val count = compose.onAllNodes(hasText("Programme ", substring = true),
            useUnmergedTree = true).fetchSemanticsNodes().size
        Log.i("GuideRenderCells", "composedProgrammeCells=$count")
        assertTrue("Visible guide has no programmes", count > 0)
        assertTrue("Too many offscreen programme cells: $count", count < 90)
        compose.onNodeWithText("Programme render:0:4").assertIsDisplayed()
        compose.onNodeWithText("Programme render:0:23").assertDoesNotExist()
    }

    @Test fun epgNavigationStillReachesOffscreenProgrammesAndAdjacentChannel() {
        showGuide()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        repeat(10) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertEquals("render:1", focused) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals("render:0", focused) }
    }

}
