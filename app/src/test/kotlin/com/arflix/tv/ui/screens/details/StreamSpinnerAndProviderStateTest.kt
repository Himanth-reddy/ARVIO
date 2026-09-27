package com.arflix.tv.ui.screens.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Job

/**
 * The two decisions the source picker's empty state rests on.
 *
 * Both were wrong for a setup whose only provider is IPTV: the spinner was
 * never switched off when the provider answered with nothing, and when it was,
 * the text told the user to go install a streaming addon.
 */
class StreamSpinnerAndProviderStateTest {

    @Test
    fun `empty source searches stop for every completion order including addons last`() {
        val orders = listOf(
            listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
            listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0)
        )
        // Slots represent the main add-on search, home server, and IPTV jobs.
        for (order in orders) {
            val jobs = List(3) { Job() }
            for ((position, slot) in order.withIndex()) {
                val shouldStop = shouldStopStreamSpinner(
                    isLoadingStreams = true,
                    hasStreams = false,
                    pluginScrapersLoading = false,
                    otherSourceJobsActive = hasOtherActiveStreamJobs(jobs, jobs[slot])
                )
                org.junit.Assert.assertEquals("Order $order, finishing $slot", position == 2, shouldStop)
                jobs[slot].complete()
            }
        }
    }

    @Test
    fun `finishing job is excluded by identity while it is still active`() {
        val finishing = Job()
        assertTrue(finishing.isActive)
        assertFalse(hasOtherActiveStreamJobs(listOf(finishing, null, null), finishing))
        val other = Job()
        assertTrue(hasOtherActiveStreamJobs(listOf(finishing, other), finishing))
        other.complete()
        assertFalse(hasOtherActiveStreamJobs(listOf(finishing, other), finishing))
        finishing.complete()
    }

    @Test
    fun `failed and cancelled provider jobs do not keep the final search waiting`() {
        val main = Job()
        val failed = Job().apply { completeExceptionally(IllegalStateException("offline")) }
        val cancelled = Job().apply { cancel() }
        assertFalse(hasOtherActiveStreamJobs(listOf(main, failed, cancelled), main))
        main.complete()
    }

    @Test
    fun `last source job finding nothing stops the spinner`() {
        assertTrue(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = false,
                pluginScrapersLoading = false,
                otherSourceJobsActive = false
            )
        )
    }

    @Test
    fun `spinner keeps running while another source job is still working`() {
        assertFalse(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = false,
                pluginScrapersLoading = false,
                otherSourceJobsActive = true
            )
        )
    }

    @Test
    fun `spinner keeps running while plugin scrapers are still working`() {
        assertFalse(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = false,
                pluginScrapersLoading = true,
                otherSourceJobsActive = false
            )
        )
    }

    @Test
    fun `sources already on screen are never taken as a reason to touch the flag`() {
        assertFalse(
            shouldStopStreamSpinner(
                isLoadingStreams = true,
                hasStreams = true,
                pluginScrapersLoading = false,
                otherSourceJobsActive = false
            )
        )
    }

    @Test
    fun `an IPTV provider alone counts as a stream provider`() {
        assertTrue(
            hasAnyStreamProvider(
                streamingAddonCount = 0,
                hasHomeServerConnections = false,
                hasIptvVodProviders = true
            )
        )
    }

    @Test
    fun `no addon no home server and no IPTV provider is the only empty setup`() {
        assertFalse(
            hasAnyStreamProvider(
                streamingAddonCount = 0,
                hasHomeServerConnections = false,
                hasIptvVodProviders = false
            )
        )
        assertTrue(
            hasAnyStreamProvider(
                streamingAddonCount = 1,
                hasHomeServerConnections = false,
                hasIptvVodProviders = false
            )
        )
        assertTrue(
            hasAnyStreamProvider(
                streamingAddonCount = 0,
                hasHomeServerConnections = true,
                hasIptvVodProviders = false
            )
        )
    }
}
