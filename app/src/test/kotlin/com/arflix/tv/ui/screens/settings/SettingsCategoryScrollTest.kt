package com.arflix.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCategoryScrollTest {
    @Test fun visibleRowsDoNotMoveOnEveryPress() {
        assertEquals(0f, settingsCategoryScrollDelta(80, 60, 0, 300, false), 0f)
    }

    @Test fun revealsOnlyOverflowAtEitherEdge() {
        assertEquals(30f, settingsCategoryScrollDelta(270, 60, 0, 300, false), 0f)
        assertEquals(-20f, settingsCategoryScrollDelta(-20, 60, 0, 300, false), 0f)
    }

    @Test fun heldGroupStillCentersForReordering() {
        assertEquals(150f, settingsCategoryScrollDelta(270, 60, 0, 300, true), 0f)
    }

    @Test fun respectsViewportPaddingAndOversizedRows() {
        assertEquals(0f, settingsCategoryScrollDelta(20, 60, -20, 280, false), 0f)
        assertEquals(20f, settingsCategoryScrollDelta(20, 400, 0, 300, true), 0f)
    }
}
