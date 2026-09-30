package com.arflix.tv.ui.screens.player.subtitles

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetimeConsistencyTest {

    /** 600 paired lines over 45 minutes, with [residualAt] giving each line's error. */
    private fun pairs(residualAt: (Long) -> Long) = (0 until 600).map { index ->
        val t = index * 4_500L
        t to residualAt(t)
    }

    @Test
    fun `a map that fits everywhere passes`() {
        // Small jitter everywhere — Peaky Blinders' −47.8s map left ≤100ms per window.
        val verdict = RetimeConsistency.check(pairs { t -> (t / 4_500L % 7) * 30 - 90 })

        assertThat(verdict.fits).isTrue()
    }

    @Test
    fun `a map that is right early and seconds out late is refused`() {
        // The Shards S01E04 shape: fine for the first half, ~4s off after a cut / uneven drift.
        val verdict = RetimeConsistency.check(pairs { t -> if (t < 1_350_000L) 100L else -4_000L })

        assertThat(verdict.fits).isFalse()
        assertThat(verdict.spreadMs).isGreaterThan(RetimeConsistency.MAX_WINDOW_SPREAD_MS)
    }

    /** A retime result placing 600 lines over 45 minutes at `authored + shiftAt(authored)`. */
    private fun retime(shiftAt: (Long) -> Long) = AutoSyncTimelineRetimeResult(
        cues = (0 until 600).map { index ->
            val t = index * 4_500L
            AutoSyncRetimedCue(t, t + 2_000L, t + shiftAt(t), t + 2_000L + shiftAt(t))
        },
        groups = emptyList(),
        targetCoverage = 0.98,
        referenceCoverage = 0.95,
        skippedTargetCues = 0,
        skippedReferenceCues = 0,
        longestTargetSkipRun = 0,
        averageGroupCost = 0.5,
        oneToOneGroups = 600,
        oneToTwoGroups = 0,
        twoToOneGroups = 0,
        oneToThreeGroups = 0,
        threeToOneGroups = 0,
        twoToTwoGroups = 0,
        confident = true,
    )

    /** The Shards S01E04 shape: +5.9s at the start growing to ~+9.5s, with steps on the way. */
    private fun shardsShift(t: Long): Long = 5_900L + t * 3_600L / 2_700_000L + if (t > 1_300_000L) 700L else 0L

    @Test
    fun `the placement profile follows each part of the episode`() {
        val profile = RetimeConsistency.placementProfile(retime(::shardsShift))

        assertThat(profile).hasSize(RetimeConsistency.WINDOWS)
        assertThat(profile.first()).isIn(com.google.common.collect.Range.closed(5_900L, 6_300L))
        assertThat(profile.last()).isIn(com.google.common.collect.Range.closed(9_800L, 10_300L))
    }

    @Test
    fun `too little evidence never refuses`() {
        val few = (0 until 10).map { it * 60_000L to if (it < 5) 0L else 5_000L }

        assertThat(RetimeConsistency.check(few).fits).isTrue()
    }
}
