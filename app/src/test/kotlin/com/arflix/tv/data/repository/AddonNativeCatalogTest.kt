package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.api.StremioMetaVideo
import com.arflix.tv.data.model.MediaType
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonNativeCatalogTest {
    @get:Rule val folder = TemporaryFolder()
    private fun context(): Context = mockk<Context> {
        every { filesDir } returns folder.root
    }

    @Test fun episodeLookupKeepsOpaqueIdsAndSeasonCoordinates() {
        val meta = StremioMetaPreview(videos = listOf(
            StremioMetaVideo(id = "provider:episode/abc?part=2", season = 2, episode = 1),
            StremioMetaVideo(id = "other", season = 1, episode = 1)))
        assertEquals("provider:episode/abc?part=2", nativeEpisodeStreamId(meta, 2, 1))
        assertEquals("other", nativeEpisodeStreamId(meta, 1, 1))
        assertNull(nativeEpisodeStreamId(meta, 2, 2))
        assertNull(nativeEpisodeStreamId(null, 1, 1))
    }

    @Test fun cloudWatchHistoryRestoresIdentityWithoutBrowsingCatalog() = runBlocking {
        val original = AddonNativeCatalog(context(), mockk(relaxed = true))
            .register("provider", StremioMetaPreview(id = "provider:show", name = "Show"), MediaType.TV)!!
        val saved = ContinueWatchingItem(id = original.id, title = original.title, mediaType = MediaType.TV,
            progress = 20, resumePositionSeconds = 300, season = 1, episode = 2,
            addonNativeId = original.addonNativeId, addonNativeAddonId = original.addonNativeAddonId)
        val restoredHistory = decodeContinueWatchingCache(Gson().toJson(listOf(saved)), Gson()).single()
        val card = restoredHistory.toMediaItem()
        assertTrue(card.hasOpenableId)
        val restored = AddonNativeCatalog(context(), mockk(relaxed = true))
        restored.restore(card)
        restored.flush()
        val restarted = AddonNativeCatalog(context(), mockk(relaxed = true))
        assertEquals("provider:show", restarted.streamId(card.id))
        assertEquals("provider", restarted.card(MediaType.TV, card.id)?.addonNativeAddonId)
    }

    @Test fun connectedTrackersDoNotHideLocalNativePlaybackOrUpNext() {
        val native = ContinueWatchingItem(id = -123, title = "Native", mediaType = MediaType.TV,
            progress = 20, resumePositionSeconds = 300, addonNativeId = "provider:show",
            addonNativeAddonId = "provider")
        assertEquals(listOf(native), ContinueWatchingMerge.merge(emptyList(), listOf(native)))
        val next = native.copy(progress = 0, resumePositionSeconds = 0, isUpNext = true)
        assertEquals(listOf(next), ContinueWatchingMerge.merge(emptyList(), listOf(next)))
        assertTrue(ContinueWatchingMerge.merge(emptyList(), listOf(native.copy(progress = 99))).isEmpty())
    }
}
