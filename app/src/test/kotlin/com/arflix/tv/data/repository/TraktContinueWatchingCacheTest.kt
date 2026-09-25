package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class TraktContinueWatchingCacheTest {
    private val candidate = ContinueWatchingCandidate(
        ContinueWatchingItem(id = 10, title = "Show", mediaType = MediaType.TV,
            progress = 0, season = 1, episode = 2, isUpNext = true),
        "2026-09-23T10:00:00Z"
    )

    @Test fun unchangedActivityStillExpiresWhenEpisodesMayHaveAired() {
        val cache = TraktUpNextCache()
        cache.put("p", "watched", false, 1_000L, listOf(candidate))
        assertEquals(listOf(candidate), cache.get("p", "watched", false, 300_999L))
        assertNull(cache.get("p", "watched", false, 301_000L))
    }

    @Test fun readsDoNotExtendExpiry() {
        val cache = TraktUpNextCache(100L)
        cache.put("p", "watched", false, 1_000L, listOf(candidate))
        assertNotNull(cache.get("p", "watched", false, 1_099L))
        assertNull(cache.get("p", "watched", false, 1_100L))
    }

    @Test fun profileSignatureAndSpecialsArePartOfCacheIdentity() {
        val cache = TraktUpNextCache()
        cache.put("p", "watched", false, 1_000L, listOf(candidate))
        assertNull(cache.get("other", "watched", false, 1_001L))
        assertNull(cache.get("p", "new-watch", false, 1_001L))
        assertNull(cache.get("p", null, false, 1_001L))
        assertNull(cache.get("p", "watched", true, 1_001L))
        assertNull(cache.get("p", "watched", false, 999L))
    }

    @Test fun emptyCompleteResultsAreCachedButExpire() {
        val cache = TraktUpNextCache(100L)
        cache.put("p", "watched", false, 1_000L, emptyList())
        assertEquals(emptyList<ContinueWatchingCandidate>(), cache.get("p", "watched", false, 1_001L))
        assertNull(cache.get("p", "watched", false, 1_100L))
    }

    @Test fun clearingInvalidatesEvenUnchangedActivity() {
        val cache = TraktUpNextCache()
        cache.put("p", "watched", false, 1_000L, listOf(candidate))
        cache.clear()
        assertNull(cache.get("p", "watched", false, 1_001L))
    }

    @Test fun cachedCandidatesSurviveChangesToTheInputList() {
        val cache = TraktUpNextCache()
        val candidates = mutableListOf(candidate)
        cache.put("p", "watched", false, 1_000L, candidates)
        candidates.clear()
        assertEquals(listOf(candidate), cache.get("p", "watched", false, 1_001L))
    }

    @Test fun cooldownDoesNotDependOnHavingCachedItems() {
        val backoff = TraktRequestBackoff()
        assertFalse(backoff.isOpen(1_000L))
        assertEquals(60_000L, backoff.rateLimited(1_000L))
        assertTrue(backoff.isOpen(60_999L))
        assertFalse(backoff.isOpen(61_000L))
    }

    @Test fun respectsServerRetryAfterInsteadOfRetryingInFiveSeconds() {
        val backoff = TraktRequestBackoff()
        assertEquals(120_000L, backoff.rateLimited(1_000L, 120L))
        assertTrue(backoff.isOpen(120_999L))
        assertFalse(backoff.isOpen(121_000L))
    }

    @Test fun repeatedRefusalsBackOffAndSuccessResets() {
        val backoff = TraktRequestBackoff()
        assertEquals(60_000L, backoff.rateLimited(1_000L))
        assertEquals(120_000L, backoff.rateLimited(61_000L))
        backoff.reset()
        assertFalse(backoff.isOpen(61_001L))
        assertEquals(60_000L, backoff.rateLimited(61_001L))
    }
}
