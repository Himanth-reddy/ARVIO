package com.arflix.tv.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression harness for Issue 5 Layer 1 (see IMPORTANT note in the deep-dive):
 * TV focus regressions never surface in a compile check, so every
 * [FocusSection] boundary — first/last element, cross-section transition,
 * episode-count edge cases — is pinned here before any rendering refactor.
 */
class TvFocusCoordinatorTest {

    private fun fullContent() = DetailsSectionContent(
        episodeCount = 10,
        totalSeasons = 3,
        ratingsPageCount = 1,
        castCount = 12,
        reviewCount = 5,
        similarCount = 8,
        collectionCount = 4,
        maxButtonIndex = 5,
        isTv = true,
        hasRatingsSection = true
    )

    private fun indices() = DetailsFocusIndices(
        buttonIndex = 2,
        episodeIndex = 3,
        ratingsIndex = 0,
        seasonIndex = 1,
        castIndex = 4,
        reviewIndex = 2,
        similarIndex = 5,
        collectionIndex = 1
    )

    // ---- isAtLeftmost ----

    @Test
    fun `isAtLeftmost true only for zero indices`() {
        val zero = DetailsFocusIndices()
        for (section in FocusSection.entries) {
            assertTrue("$section", isAtLeftmost(section, zero))
        }
        val nonZero = indices()
        for (section in FocusSection.entries) {
            // ratingsIndex is 0 in indices()
            if (section == FocusSection.RATINGS) {
                assertTrue(isAtLeftmost(section, nonZero))
            } else {
                assertFalse("$section", isAtLeftmost(section, nonZero))
            }
        }
    }

    // ---- handleLeft ----

    @Test
    fun `handleLeft decrements every section index`() {
        val got = mutableMapOf<FocusSection, Int>()
        handleLeft(
            FocusSection.EPISODES, 2, 3, 0, 1, 4, 2, 5, 1,
            { got[FocusSection.BUTTONS] = it }, { got[FocusSection.EPISODES] = it },
            { got[FocusSection.RATINGS] = it }, { got[FocusSection.SEASONS] = it },
            { got[FocusSection.CAST] = it }, { got[FocusSection.REVIEWS] = it },
            { got[FocusSection.SIMILAR] = it }, { got[FocusSection.COLLECTION] = it }
        )
        assertEquals(mapOf(FocusSection.EPISODES to 2), got)
    }

    @Test
    fun `handleLeft at index zero calls no setter`() {
        var called = false
        val noop: (Int) -> Unit = { called = true }
        for (section in FocusSection.entries) {
            called = false
            handleLeft(section, 0, 0, 0, 0, 0, 0, 0, 0,
                noop, noop, noop, noop, noop, noop, noop, noop)
            assertFalse("$section", called)
        }
    }

    // ---- handleRight ----

    @Test
    fun `handleRight stops at last element of every section`() {
        val content = fullContent()
        // cursor on last valid index per section: no setter must fire.
        // Order matches FocusSection.entries: BUTTONS, EPISODES, SEASONS,
        // RATINGS, CAST, REVIEWS, SIMILAR, COLLECTION.
        val lastIndices = listOf(
            content.maxButtonIndex, content.episodeCount - 1, content.totalSeasons - 1,
            content.ratingsPageCount - 1, content.castCount - 1, content.reviewCount - 1,
            content.similarCount - 1, content.collectionCount - 1
        )
        FocusSection.entries.forEachIndexed { i, section ->
            var called = false
            val noop: (Int) -> Unit = { called = true }
            val idx = lastIndices[i]
            handleRight(
                section, idx, idx, idx, idx, idx, idx, idx, idx,
                content.maxButtonIndex, content.episodeCount, content.ratingsPageCount,
                content.totalSeasons, content.castCount, content.reviewCount,
                content.similarCount, content.collectionCount,
                noop, noop, noop, noop, noop, noop, noop, noop
            )
            assertFalse("$section", called)
        }
    }

    @Test
    fun `handleRight advances mid-list cursor`() {
        var got = -1
        handleRight(
            FocusSection.SIMILAR, 0, 0, 0, 0, 0, 0, 2, 0,
            5, 10, 1, 3, 12, 5, 8, 4,
            {}, {}, {}, {}, {}, {}, { got = it }, {}
        )
        assertEquals(3, got)
    }

    @Test
    fun `handleRight buttons bound follows collection CTA presence`() {
        var got = -1
        // without collection: max 4
        handleRight(
            FocusSection.BUTTONS, 4, 0, 0, 0, 0, 0, 0, 0,
            4, 10, 1, 3, 12, 5, 8, 0,
            { got = it }, {}, {}, {}, {}, {}, {}, {}
        )
        assertEquals(-1, got)
        // with collection: max 5
        handleRight(
            FocusSection.BUTTONS, 4, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 1, 3, 12, 5, 8, 4,
            { got = it }, {}, {}, {}, {}, {}, {}, {}
        )
        assertEquals(5, got)
    }

    // ---- moveUpSection ----

    @Test
    fun `moveUp walks full TV chain to buttons`() {
        val content = fullContent()
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.BUTTONS, content))
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.SEASONS, content))
        assertEquals(FocusSection.SEASONS, moveUpSection(FocusSection.EPISODES, content))
        assertEquals(FocusSection.EPISODES, moveUpSection(FocusSection.RATINGS, content))
        assertEquals(FocusSection.RATINGS, moveUpSection(FocusSection.CAST, content))
        assertEquals(FocusSection.CAST, moveUpSection(FocusSection.REVIEWS, content))
        assertEquals(FocusSection.COLLECTION, moveUpSection(FocusSection.SIMILAR, content))
        assertEquals(FocusSection.REVIEWS, moveUpSection(FocusSection.COLLECTION, content))
    }

    @Test
    fun `moveUp skips missing sections`() {
        val sparse = DetailsSectionContent(
            episodeCount = 0, totalSeasons = 1, castCount = 6,
            reviewCount = 0, similarCount = 3, collectionCount = 0,
            isTv = true, hasRatingsSection = false
        )
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.EPISODES, sparse))
        assertEquals(FocusSection.CAST, moveUpSection(FocusSection.SIMILAR, sparse))
        assertEquals(FocusSection.CAST, moveUpSection(FocusSection.COLLECTION, sparse))
        // REVIEWS with cast present goes up to CAST (not BUTTONS).
        assertEquals(FocusSection.CAST, moveUpSection(FocusSection.REVIEWS, sparse))
    }

    @Test
    fun `moveUp from cast on movie jumps straight to buttons`() {
        val movie = fullContent().copy(isTv = false)
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.CAST, movie))
    }

    @Test
    fun `moveUp single-season show skips seasons row`() {
        val single = fullContent().copy(totalSeasons = 1)
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.EPISODES, single))
        assertEquals(FocusSection.EPISODES, moveUpSection(FocusSection.RATINGS, single))
    }

    // ---- moveDownSection ----

    @Test
    fun `moveDown walks full TV chain to similar`() {
        val content = fullContent()
        assertEquals(FocusSection.SEASONS, moveDownSection(FocusSection.BUTTONS, content))
        assertEquals(FocusSection.EPISODES, moveDownSection(FocusSection.SEASONS, content))
        assertEquals(FocusSection.RATINGS, moveDownSection(FocusSection.EPISODES, content))
        assertEquals(FocusSection.CAST, moveDownSection(FocusSection.RATINGS, content))
        assertEquals(FocusSection.REVIEWS, moveDownSection(FocusSection.CAST, content))
        assertEquals(FocusSection.COLLECTION, moveDownSection(FocusSection.REVIEWS, content))
        assertEquals(FocusSection.SIMILAR, moveDownSection(FocusSection.COLLECTION, content))
        assertEquals(FocusSection.SIMILAR, moveDownSection(FocusSection.SIMILAR, content))
    }

    @Test
    fun `moveDown from buttons skips to first available row`() {
        // movie with only cast + similar
        val movie = DetailsSectionContent(
            castCount = 5, similarCount = 4, isTv = false
        )
        assertEquals(FocusSection.CAST, moveDownSection(FocusSection.BUTTONS, movie))
        assertEquals(FocusSection.SIMILAR, moveDownSection(FocusSection.CAST, movie))
    }

    @Test
    fun `moveDown from buttons lands on first available row`() {
        val castOnly = DetailsSectionContent(castCount = 5, isTv = false)
        // BUTTONS skips to CAST (first available below)…
        assertEquals(FocusSection.CAST, moveDownSection(FocusSection.BUTTONS, castOnly))
        // …and CAST with nothing below stays put.
        assertEquals(FocusSection.CAST, moveDownSection(FocusSection.CAST, castOnly))
    }

    @Test
    fun `moveDown empty show stays on buttons`() {
        val empty = DetailsSectionContent(isTv = true)
        assertEquals(FocusSection.BUTTONS, moveDownSection(FocusSection.BUTTONS, empty))
    }

    @Test
    fun `section availability matches content presence`() {
        val content = fullContent()
        for (section in FocusSection.entries) {
            assertTrue("$section", content.isAvailable(section))
        }
        val empty = DetailsSectionContent(isTv = true)
        assertTrue(empty.isAvailable(FocusSection.BUTTONS))
        assertFalse(empty.isAvailable(FocusSection.SEASONS))
        assertFalse(empty.isAvailable(FocusSection.EPISODES))
        assertFalse(empty.isAvailable(FocusSection.CAST))
    }
}
