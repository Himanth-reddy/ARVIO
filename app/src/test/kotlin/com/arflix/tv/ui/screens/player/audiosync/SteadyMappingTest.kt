package com.arflix.tv.ui.screens.player.audiosync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SteadyMappingTest {

    private var now = 0L
    private val steady = SteadyMapping { now }

    private fun shift(ms: Long) = SubtitleSyncModel(listOf(SubtitleSyncSegment(0L, 1.0, ms.toDouble())))

    /** Feeds [model] on every 250ms tick for [forMs], returning every decision that was not Keep. */
    private fun feed(model: SubtitleSyncModel?, forMs: Long = 250L, settled: Boolean = true): List<SteadyMapping.Decision> =
        (0 until (forMs / 250L).coerceAtLeast(1L)).mapNotNull {
            steady.decide(model, 60_000L, settled).also { now += 250L }
                .takeIf { it != SteadyMapping.Decision.Keep }
        }

    private fun settleOn(ms: Long): SubtitleSyncModel = shift(ms).also { feed(it) }

    @Test
    fun `before the first confirmed lock every mapping passes straight through`() {
        assertThat(feed(shift(4_900L), settled = false)).hasSize(1)
        assertThat(feed(null, settled = false)).hasSize(1)
        assertThat(steady.displayed).isNull()
    }

    @Test
    fun `the first confirmed lock is shown at once`() {
        feed(shift(4_900L), settled = false)
        val decisions = feed(shift(6_500L))

        assertThat(decisions).hasSize(1)
        assertThat(steady.displayed?.let { SteadyMapping.delayMs(it, 0L) }).isEqualTo(6_500L)
    }

    @Test
    fun `a wobble under half a second never reaches the screen`() {
        settleOn(6_500L)

        assertThat(feed(shift(6_800L), forMs = 60_000L)).isEmpty()
        assertThat(feed(shift(6_200L), forMs = 60_000L)).isEmpty()
    }

    @Test
    fun `a real change has to hold for five seconds`() {
        settleOn(6_500L)
        now += 60_000L

        assertThat(feed(shift(7_400L), forMs = 4_500L)).isEmpty()
        assertThat(feed(shift(7_400L), forMs = 1_000L)).hasSize(1)
    }

    @Test
    fun `a reading that changes before it has held is not shown`() {
        settleOn(6_500L)
        now += 60_000L

        feed(shift(7_400L), forMs = 3_000L)
        assertThat(feed(shift(8_400L), forMs = 3_000L)).isEmpty()
    }

    @Test
    fun `at most one ordinary change per thirty seconds`() {
        settleOn(6_500L)
        now += 60_000L
        feed(shift(7_400L), forMs = 6_000L)

        assertThat(feed(shift(8_300L), forMs = 20_000L)).isEmpty()
        assertThat(feed(shift(8_300L), forMs = 10_000L)).hasSize(1)
    }

    @Test
    fun `a cut-sized change is not rate limited`() {
        settleOn(6_500L)
        now += 60_000L
        feed(shift(7_400L), forMs = 6_000L)

        assertThat(feed(shift(10_600L), forMs = 6_000L)).hasSize(1)
    }

    @Test
    fun `after a seek the new timing is shown at once`() {
        settleOn(6_500L)
        steady.onSeek()

        val decisions = feed(shift(9_500L))

        assertThat(decisions).hasSize(1)
        assertThat((decisions.single() as SteadyMapping.Decision.Show).changeMs).isEqualTo(3_000L)
    }

    @Test
    fun `a mapping that keeps reversing freezes the timing`() {
        settleOn(6_500L)
        now += 60_000L
        val decisions = listOf(7_400L, 6_500L, 7_400L, 6_500L, 7_400L).flatMap { feed(shift(it), forMs = 31_000L) }

        assertThat(decisions.last()).isEqualTo(SteadyMapping.Decision.Froze)
        assertThat(feed(shift(9_000L), forMs = 60_000L)).isEmpty()
    }
}
