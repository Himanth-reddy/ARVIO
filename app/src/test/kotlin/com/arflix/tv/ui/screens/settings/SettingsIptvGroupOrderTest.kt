package com.arflix.tv.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsIptvGroupOrderTest {

    @Test
    fun staleSavedGroupLabelsCannotReappearAfterProviderRefresh() {
        val ordered = orderedIptvGroups(
            playlistId = "list_1",
            availableGroups = listOf("Entertainment", "Kids", "Movies"),
            groupOrder = listOf("list_1|[B] Kids", "list_1|Movies"),
        )

        assertThat(ordered).containsExactly("Movies", "Entertainment", "Kids").inOrder()
        assertThat(ordered).doesNotContain("[B] Kids")
    }
}

class StalkerDpadIndexTest {

    @Test
    fun iptvRowMaxActionIsAlwaysFive() {
        // Both M3U playlist rows and Stalker portal rows carry the same full
        // chip row (categories / toggle / edit / up / down / delete).
        assertThat(iptvRowMaxAction()).isEqualTo(5)
    }

    @Test
    fun firstIptvGroupIndexStartsAtTwoForM3UPlaylists() {
        // M3U and Xtream sources now carry the bulk-toggle row too, so their
        // categories start at 2 like the Stalker ones (was 1).
        assertThat(
            firstIptvGroupIndex(listOf("Movies", "Kids"))
        ).isEqualTo(2)
    }

    @Test
    fun firstIptvGroupIndexStartsAtTwoForStalkerPortals() {
        // Each Stalker portal gets a bulk-toggle row at index 1.
        assertThat(
            firstIptvGroupIndex(listOf("Movies", "Kids"))
        ).isEqualTo(2)
        assertThat(
            firstIptvGroupIndex(listOf("News"))
        ).isEqualTo(2)
    }

    @Test
    fun firstIptvGroupIndexIsOneWhenThereAreNoGroupsAtAll() {
        // No bulk-toggle row when there are no categories to toggle - that is
        // the only case left in which the categories start at 1.
        assertThat(
            firstIptvGroupIndex(emptyList())
        ).isEqualTo(1)
    }
}

class StalkerCategoryTabIndexTest {

    @Test
    fun aSourceWithoutTheTabBarKeepsEveryIndexItHad() {
        // The whole point of the flag: an M3U or Xtream playlist has no movie
        // or series catalog, gets no tab bar, and its live TV page must not
        // move by a single row.
        assertThat(firstIptvGroupIndex(listOf("Movies", "Kids"), hasTabBar = false)).isEqualTo(2)
        assertThat(firstIptvGroupIndex(emptyList(), hasTabBar = false)).isEqualTo(1)
    }

    @Test
    fun theTabBarPushesTheLiveTvListDownByExactlyOneRow() {
        assertThat(firstIptvGroupIndex(listOf("Movies", "Kids"), hasTabBar = true)).isEqualTo(3)
        assertThat(firstIptvGroupIndex(emptyList(), hasTabBar = true)).isEqualTo(2)
    }

    @Test
    fun theCatalogTabsHaveNoResetRow() {
        // Tab bar at 0, bulk toggle at 1, categories from 2 - one row shorter
        // than live TV, because there is no order to reset.
        assertThat(firstStalkerCategoryIndex(4)).isEqualTo(2)
        assertThat(lastStalkerCategoryIndex(4)).isEqualTo(5)
    }

    @Test
    fun aCatalogTabWithoutCategoriesHasNothingBelowTheBar() {
        // No categories means no bulk toggle either, so the bar is the only
        // row and the focus must not be allowed to walk off it.
        assertThat(firstStalkerCategoryIndex(0)).isEqualTo(1)
        assertThat(lastStalkerCategoryIndex(0)).isEqualTo(0)
    }

    @Test
    fun theTabOrderIsTheOneTheBarDraws() {
        // Left to right, and the D-pad column maps onto it by ordinal - the
        // live TV tab has to stay first, it is the list that was always there.
        assertThat(StalkerCategoryTab.entries.map { it.name })
            .containsExactly("LIVE", "MOVIES", "SERIES").inOrder()
        assertThat(StalkerCategoryTab.LIVE.catalogKind()).isNull()
        assertThat(StalkerCategoryTab.MOVIES.catalogKind())
            .isEqualTo(com.arflix.tv.data.model.StalkerCatalogKind.MOVIES)
        assertThat(StalkerCategoryTab.SERIES.catalogKind())
            .isEqualTo(com.arflix.tv.data.model.StalkerCatalogKind.SERIES)
    }
}

class KeptIptvActionIndexTest {

    // Reset at 0, bulk toggle at 1, five categories at 2..6.
    private val firstGroup = 2
    private val groupCount = 5

    @Test
    fun theColumnSurvivesAStepFromOneCategoryToTheNext() {
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 3, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(1)
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 6, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(1)
    }

    @Test
    fun theFirstColumnStaysTheFirstColumn() {
        assertThat(
            keptIptvActionIndex(actionIndex = 0, targetFocusIndex = 4, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(0)
    }

    @Test
    fun rowsWithoutChipsClampTheColumnBack() {
        // Reset row and bulk-toggle row have no chips at all.
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 0, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(0)
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 1, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(0)
    }

    @Test
    fun steppingPastTheLastCategoryClampsTheColumnBack() {
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 7, firstGroupIndex = firstGroup, groupCount = groupCount)
        ).isEqualTo(0)
    }

    @Test
    fun anEmptyCategoryListNeverKeepsAColumn() {
        assertThat(
            keptIptvActionIndex(actionIndex = 1, targetFocusIndex = 1, firstGroupIndex = 1, groupCount = 0)
        ).isEqualTo(0)
    }
}

class HeldGroupMoveTargetTest {

    // A source without a bulk-toggle row: reset at 0, first category row at 1.
    // Since W4 that is only a source with no categories, but the arithmetic
    // has to stay right for any first index the screen hands in.
    private val firstAtOne = 1
    // Stalker portals: reset at 0, bulk toggle at 1, first category row at 2.
    private val firstStalker = 2

    @Test
    fun movingUpInTheMiddleFollowsTheGroup() {
        assertThat(
            heldGroupMoveTarget(focusedIndex = 4, firstGroupIndex = firstAtOne, groupCount = 10, moveUp = true)
        ).isEqualTo(3)
    }

    @Test
    fun movingDownInTheMiddleFollowsTheGroup() {
        assertThat(
            heldGroupMoveTarget(focusedIndex = 4, firstGroupIndex = firstAtOne, groupCount = 10, moveUp = false)
        ).isEqualTo(5)
    }

    @Test
    fun topGroupCannotMoveUp() {
        // IptvRepository.moveGroupUp silently does nothing at the top, so the
        // focus must not move either - otherwise focus and list drift apart.
        assertThat(
            heldGroupMoveTarget(focusedIndex = firstAtOne, firstGroupIndex = firstAtOne, groupCount = 10, moveUp = true)
        ).isNull()
    }

    @Test
    fun bottomGroupCannotMoveDown() {
        assertThat(
            heldGroupMoveTarget(focusedIndex = 10, firstGroupIndex = firstAtOne, groupCount = 10, moveUp = false)
        ).isNull()
    }

    @Test
    fun stalkerPortalsKeepTheBulkToggleRowOutOfReach() {
        // The first category of a Stalker portal sits at focus index 2; moving
        // it up must not push it into the bulk-toggle or reset row.
        assertThat(
            heldGroupMoveTarget(focusedIndex = firstStalker, firstGroupIndex = firstStalker, groupCount = 5, moveUp = true)
        ).isNull()
        assertThat(
            heldGroupMoveTarget(focusedIndex = 3, firstGroupIndex = firstStalker, groupCount = 5, moveUp = true)
        ).isEqualTo(2)
        assertThat(
            heldGroupMoveTarget(focusedIndex = 6, firstGroupIndex = firstStalker, groupCount = 5, moveUp = false)
        ).isNull()
    }

    @Test
    fun rowsThatAreNotCategoriesNeverMove() {
        // Reset row and bulk-toggle row of a Stalker portal.
        assertThat(
            heldGroupMoveTarget(focusedIndex = 0, firstGroupIndex = firstStalker, groupCount = 5, moveUp = false)
        ).isNull()
        assertThat(
            heldGroupMoveTarget(focusedIndex = 1, firstGroupIndex = firstStalker, groupCount = 5, moveUp = true)
        ).isNull()
        // Past the last category row.
        assertThat(
            heldGroupMoveTarget(focusedIndex = 7, firstGroupIndex = firstStalker, groupCount = 5, moveUp = true)
        ).isNull()
    }

    @Test
    fun emptyCategoryListNeverMoves() {
        assertThat(
            heldGroupMoveTarget(focusedIndex = 1, firstGroupIndex = firstAtOne, groupCount = 0, moveUp = true)
        ).isNull()
    }

    @Test
    fun aSingleGroupCannotMoveInEitherDirection() {
        assertThat(
            heldGroupMoveTarget(focusedIndex = 1, firstGroupIndex = firstAtOne, groupCount = 1, moveUp = true)
        ).isNull()
        assertThat(
            heldGroupMoveTarget(focusedIndex = 1, firstGroupIndex = firstAtOne, groupCount = 1, moveUp = false)
        ).isNull()
    }

    @Test
    fun repeatedPressesWalkAGroupAllTheWayToTheTopAndThenStop() {
        // Twenty groups, the focused one sits on position 19 (focus index 20).
        var focus = 20
        repeat(25) {
            focus = heldGroupMoveTarget(focus, firstAtOne, groupCount = 20, moveUp = true) ?: focus
        }
        assertThat(focus).isEqualTo(firstAtOne)
    }
}

class CenteredScrollOffsetTest {

    @Test
    fun aRowIsPushedDownByHalfTheLeftoverViewport() {
        // 560 dp of list, 70 dp rows: the focused row should start 245 below the
        // top edge, which is what the negative offset asks the list for.
        assertThat(centeredScrollOffset(viewportSize = 560, itemSize = 70)).isEqualTo(-245)
    }

    @Test
    fun anUnmeasuredListFallsBackToTopAlignment() {
        // First frame: the list has no geometry yet. Offset 0 is the old
        // behaviour, which is correct rather than merely harmless.
        assertThat(centeredScrollOffset(viewportSize = 0, itemSize = 70)).isEqualTo(0)
        assertThat(centeredScrollOffset(viewportSize = 560, itemSize = 0)).isEqualTo(0)
    }

    @Test
    fun aRowTallerThanTheViewportIsNotPushedOffScreen() {
        assertThat(centeredScrollOffset(viewportSize = 200, itemSize = 200)).isEqualTo(0)
        assertThat(centeredScrollOffset(viewportSize = 200, itemSize = 260)).isEqualTo(0)
    }

    @Test
    fun negativeMeasurementsNeverProduceAnOffset() {
        assertThat(centeredScrollOffset(viewportSize = -10, itemSize = 70)).isEqualTo(0)
        assertThat(centeredScrollOffset(viewportSize = 560, itemSize = -70)).isEqualTo(0)
    }

    @Test
    fun theOffsetAlwaysPointsUpwardsSoTheRowMovesDown() {
        // A positive offset would scroll PAST the row and hide it above the edge.
        for (item in 10..200 step 10) {
            assertThat(centeredScrollOffset(viewportSize = 560, itemSize = item)).isAtMost(0)
        }
    }
}
