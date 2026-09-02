package com.arflix.tv.ui.screens.player.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPreviewFrameProviderTest {
    @Test
    fun `quantization selects nearest ten second frame`() {
        val duration = 7_200_000L

        assertEquals(0L, quantizeSeekPreviewPosition(0L, duration))
        assertEquals(10_000L, quantizeSeekPreviewPosition(6_000L, duration))
        assertEquals(20_000L, quantizeSeekPreviewPosition(15_000L, duration))
        assertEquals(1_230_000L, quantizeSeekPreviewPosition(1_226_000L, duration))
    }

    @Test
    fun `quantization clamps positions to the media duration`() {
        val duration = 93_456L

        assertEquals(0L, quantizeSeekPreviewPosition(-10_000L, duration))
        assertEquals(duration, quantizeSeekPreviewPosition(100_000L, duration))
        assertEquals(duration, quantizeSeekPreviewPosition(Long.MAX_VALUE, duration))
    }

    @Test
    fun `unknown duration never produces an invalid preview position`() {
        assertEquals(0L, quantizeSeekPreviewPosition(30_000L, 0L))
        assertEquals(0L, quantizeSeekPreviewPosition(30_000L, -1L))
    }

    @Test
    fun `held remote navigation accelerates in controlled steps`() {
        assertEquals(10_000L, acceleratedSeekPreviewStepMs(0))
        assertEquals(10_000L, acceleratedSeekPreviewStepMs(7))
        assertEquals(30_000L, acceleratedSeekPreviewStepMs(8))
        assertEquals(30_000L, acceleratedSeekPreviewStepMs(17))
        assertEquals(60_000L, acceleratedSeekPreviewStepMs(18))
    }

    @Test
    fun `preview dimensions preserve widescreen aspect ratio`() {
        assertEquals(416 to 173, fitSeekPreviewDimensions(1920, 800, 416, 234))
    }

    @Test
    fun `preview dimensions preserve four by three aspect ratio`() {
        assertEquals(312 to 234, fitSeekPreviewDimensions(1440, 1080, 416, 234))
    }

    @Test
    fun `preview dimensions keep sixteen by nine within bounds`() {
        assertEquals(416 to 234, fitSeekPreviewDimensions(1920, 1080, 416, 234))
    }
}
