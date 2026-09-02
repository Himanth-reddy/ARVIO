package com.arflix.tv.data.repository.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncProviderStoreStateTest {

    @Test
    fun `connecting a second tracker does not implicitly select both`() {
        assertThat(
            defaultTrackingReadMode(
                hasTrakt = true,
                hasSimkl = true,
                hasMdbList = false,
                preferredProvider = SyncProvider.TRAKT
            )
        ).isEqualTo(TrackingReadMode.TRAKT)

        assertThat(
            defaultTrackingReadMode(
                hasTrakt = true,
                hasSimkl = true,
                hasMdbList = false,
                preferredProvider = SyncProvider.SIMKL
            )
        ).isEqualTo(TrackingReadMode.SIMKL)
    }

    @Test
    fun `trakt is deterministic fallback when both trackers have no explicit preference`() {
        assertThat(
            defaultTrackingReadMode(
                hasTrakt = true,
                hasSimkl = true,
                hasMdbList = false
            )
        ).isEqualTo(TrackingReadMode.TRAKT)
    }

    @Test
    fun `connecting simkl preserves an explicit trakt-only read mode`() {
        assertThat(
            repairUnavailableTrackingReadMode(
                mode = TrackingReadMode.TRAKT,
                hasTrakt = true,
                hasSimkl = true,
                hasMdbList = false,
                replacement = TrackingReadMode.TRAKT
            )
        ).isEqualTo(TrackingReadMode.TRAKT)
    }

    @Test
    fun `legacy cloud selection cannot overwrite an existing local choice`() {
        assertThat(
            shouldApplyCloudTrackingSelection(
                incomingUpdatedAt = null,
                localUpdatedAt = null,
                hasLocalSelection = true
            )
        ).isFalse()
    }

    @Test
    fun `legacy cloud selection restores onto an empty install`() {
        assertThat(
            shouldApplyCloudTrackingSelection(
                incomingUpdatedAt = null,
                localUpdatedAt = null,
                hasLocalSelection = false
            )
        ).isTrue()
    }

    @Test
    fun `newer tracking selection wins across devices`() {
        assertThat(
            shouldApplyCloudTrackingSelection(
                incomingUpdatedAt = 200L,
                localUpdatedAt = 100L,
                hasLocalSelection = true
            )
        ).isTrue()
        assertThat(
            shouldApplyCloudTrackingSelection(
                incomingUpdatedAt = 100L,
                localUpdatedAt = 200L,
                hasLocalSelection = true
            )
        ).isFalse()
    }

    @Test
    fun `legacy cloud credential is additive and never deletes a local credential`() {
        assertThat(
            shouldApplyCloudCredential(
                incomingUpdatedAt = null,
                localUpdatedAt = null,
                incomingHasCredential = false,
                localHasCredential = true
            )
        ).isFalse()
        assertThat(
            shouldApplyCloudCredential(
                incomingUpdatedAt = null,
                localUpdatedAt = null,
                incomingHasCredential = true,
                localHasCredential = false
            )
        ).isTrue()
    }

    @Test
    fun `modern credential tombstone only applies when newer`() {
        assertThat(
            shouldApplyCloudCredential(
                incomingUpdatedAt = 200L,
                localUpdatedAt = 100L,
                incomingHasCredential = false,
                localHasCredential = true
            )
        ).isTrue()
        assertThat(
            shouldApplyCloudCredential(
                incomingUpdatedAt = 100L,
                localUpdatedAt = 200L,
                incomingHasCredential = false,
                localHasCredential = true
            )
        ).isFalse()
    }
}
