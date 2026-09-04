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

    @Test
    fun `display aspect ratio respects anamorphic pixels`() {
        val ratio = seekPreviewDisplayAspectRatio(
            videoWidth = 720,
            videoHeight = 576,
            pixelWidthHeightRatio = 64f / 45f,
            unappliedRotationDegrees = 0,
            fallbackWidth = 1920,
            fallbackHeight = 1080,
        )

        assertEquals(16f / 9f, ratio, 0.001f)
        assertEquals(416 to 234, fitSeekPreviewAspectRatio(ratio, 416, 234))
    }

    @Test
    fun `display aspect ratio preserves cinema width without cropping`() {
        val ratio = seekPreviewDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 800,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 0,
            fallbackWidth = 1920,
            fallbackHeight = 1080,
        )

        assertEquals(2.4f, ratio, 0.001f)
        assertEquals(416 to 173, fitSeekPreviewAspectRatio(ratio, 416, 234))
    }

    @Test
    fun `display aspect ratio accounts for unapplied rotation`() {
        val ratio = seekPreviewDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 90,
            fallbackWidth = 1920,
            fallbackHeight = 1080,
        )

        assertEquals(9f / 16f, ratio, 0.001f)
        assertEquals(132 to 234, fitSeekPreviewAspectRatio(ratio, 416, 234))
    }

    @Test
    fun `hysteresis prevents bucket flipping from small jitter across boundaries`() {
        val duration = 7_200_000L

        // Initial scrub with uninitialized bucket (-1L)
        val initialBucket = quantizeSeekPreviewPositionWithHysteresis(-1L, 20_000L, duration)
        assertEquals(20_000L, initialBucket)

        // Micro-tremor moving slightly forward past the 5s rounding midpoint (25_500ms):
        // Normal quantize would jump to 30_000L, but hysteresis holds 20_000L
        assertEquals(20_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 25_500L, duration))
        assertEquals(20_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 27_000L, duration))

        // Micro-tremor moving backwards (14_500ms):
        // Normal quantize would jump to 10_000L, but hysteresis holds 20_000L
        assertEquals(20_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 14_500L, duration))
        assertEquals(20_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 13_000L, duration))

        // Deliberate movement beyond the ±7,500ms hysteresis threshold switches buckets
        assertEquals(30_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 28_000L, duration))
        assertEquals(10_000L, quantizeSeekPreviewPositionWithHysteresis(20_000L, 12_000L, duration))
    }
}
