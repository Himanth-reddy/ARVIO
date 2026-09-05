package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Test

class GuideContinuityTest {
    @Test fun resumesValidatedSavedChannelOutsideStartupPage() {
        assertEquals("provider:50000", LiveTvStartup.chooseStartupChannelId(
            availableChannelIds = setOf("provider:1"), firstAvailableChannelId = "provider:1",
            explicitChannelId = null, sessionLastChannelId = "provider:50000", hasOpenedBefore = true,
            favoriteChannelIds = emptyList(), isFullyLoaded = true, resolvedSessionChannelId = "provider:50000",
        ))
    }

    private val past = IptvProgram("Past", startUtcMillis = 0, endUtcMillis = 100)
    private val live = IptvProgram("Live", startUtcMillis = 100, endUtcMillis = 200)
    private val next = IptvProgram("Next", startUtcMillis = 200, endUtcMillis = 300)

    @Test fun clockPromotesNextAndRetainsArchive() {
        val cached = IptvNowNext(now = past, next = live, upcoming = listOf(live, next))
        val current = cached.atTime(100)
        assertEquals(live, current.now)
        assertEquals(next, current.next)
        assertEquals(listOf(past), current.recent)
        assertEquals(listOf(live, next), current.atTime(300).recent.drop(1))
        assertNull(current.atTime(300).now)
    }

    @Test fun failedOrEmptyWindowDoesNotEraseAlreadyVisibleGuide() {
        val guide = IptvNowNext(now = live)
        val cached = mapOf("a" to guide)
        assertEquals(cached, retainGuideWindows(cached, emptyMap(), setOf("a")))
        assertEquals(cached, retainGuideWindows(cached, mapOf("a" to IptvNowNext()), setOf("a")))
    }

    @Test fun guideCacheEvictsOldestWindowsAndKeepsRevisitedRows() {
        val guide = IptvNowNext(now = live)
        var cache = retainGuideWindows(emptyMap(), mapOf("a" to guide, "b" to guide), setOf("a", "b"), 2)
        cache = retainGuideWindows(cache, emptyMap(), setOf("a"), 2)
        cache = retainGuideWindows(cache, mapOf("c" to guide), setOf("c"), 2)
        assertEquals(setOf("a", "c"), cache.keys)
    }

    @Test fun liveRecoveryIsBoundedAndNeverSeeksCatchupToLive() {
        var now = 0L
        val recovery = LiveWindowRecovery { now }
        assertFalse(recovery.claim(isCatchup = true))
        assertTrue(recovery.claim(isCatchup = false))
        assertFalse(recovery.claim(isCatchup = false))
        now = 60_000L
        assertTrue(recovery.claim(isCatchup = false))
    }
}
