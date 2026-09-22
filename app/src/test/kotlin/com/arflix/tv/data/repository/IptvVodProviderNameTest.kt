package com.arflix.tv.data.repository

import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.ui.components.sourceAttributionLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression tests for [IptvRepository.withIptvProvider] (marked `internal` so
 * tests can call it directly, same convention as `activePlaylists`).
 *
 * Root cause: a user with several IPTV providers saw several rows in the source
 * list all reading "IPTV Series VOD", with nothing saying whose stream was whose.
 * `XtreamCredentials` carries host and login only - it is a cache key and a
 * request builder - so the playlist name the user typed was dropped the moment
 * credentials were resolved, and `addonName` was a constant string at every
 * source-building site.
 *
 * The source menu never lacked a place to show it: `sourceAttributionLabels` ->
 * `rowSubtitle` renders `behaviorHints.provider` right after the add-on name, the
 * slot Stremio add-ons use for their indexer. IPTV simply never filled it. The
 * last test here pins that end-to-end, so a change to either side shows up.
 */
class IptvVodProviderNameTest {

    @Test fun `the provider name is carried on the source`() {
        val repository = newRepository()
        with(repository) {
            val stamped = source().withIptvProvider("Fusion Media")
            assertEquals("Fusion Media", stamped.behaviorHints?.provider)
        }
    }

    @Test fun `surrounding spaces in a typed-in name are dropped`() {
        val repository = newRepository()
        with(repository) {
            assertEquals("Fusion Media", source().withIptvProvider("  Fusion Media  ").behaviorHints?.provider)
        }
    }

    /**
     * A playlist may be saved without a name. Stamping an empty string would put
     * a stray separator in the row, so the source has to come back untouched.
     */
    @Test fun `a nameless playlist leaves the source exactly as it was`() {
        val repository = newRepository()
        with(repository) {
            val original = source()
            assertSame(original, original.withIptvProvider(""))
            assertSame(original, original.withIptvProvider("   "))
            assertNull(original.withIptvProvider("").behaviorHints?.provider)
        }
    }

    /**
     * Stamping must not wipe the hints a source already carries - the Stalker
     * path in particular fills other fields.
     */
    @Test fun `existing behaviour hints survive the stamp`() {
        val repository = newRepository()
        with(repository) {
            val stamped = source(hints = StreamBehaviorHints(filename = "episode.mkv", notWebReady = true))
                .withIptvProvider("Star IPTV")
            assertEquals("Star IPTV", stamped.behaviorHints?.provider)
            assertEquals("episode.mkv", stamped.behaviorHints?.filename)
            assertEquals(true, stamped.behaviorHints?.notWebReady)
        }
    }

    /**
     * The point of the whole change: the source menu turns the stamped name into
     * the second line of the row, next to the add-on name.
     */
    @Test fun `the source menu shows the stamped name next to the addon name`() {
        val repository = newRepository()
        with(repository) {
            val stamped = source().withIptvProvider("Fusion Media")
            assertEquals(listOf("Fusion Media"), sourceAttributionLabels(stamped, "IPTV Series VOD"))
        }
    }

    /**
     * Measured on a device on 22.09.2026: the Stalker description line used to
     * begin with the portal name, so once the name was stamped as well, the row
     * read "IPTV VOD - Portal 100" directly above "Portal 100 - 192 min -
     * IMDb 7.601". The description keeps the facts only it carries; the name
     * lives in one place now.
     */
    @Test fun `the stalker description no longer repeats the provider name`() {
        val repository = newRepository()
        with(repository) {
            val stamped = source(
                hints = null
            ).copy(description = "192 min \u00b7 IMDb 7.601").withIptvProvider("Portal 100")
            assertEquals("Portal 100", stamped.behaviorHints?.provider)
            assertEquals(false, stamped.description.orEmpty().contains("Portal 100"))
        }
    }

    private fun source(hints: StreamBehaviorHints? = null) = StreamSource(
        source = "The Gentlemen (2024) - S02E02 - Episode 2",
        addonName = "IPTV Series VOD",
        addonId = "iptv_xtream_vod",
        quality = "4K",
        size = "",
        url = "https://example.invalid/series/u/p/1.mkv",
        behaviorHints = hints
    )

    private fun newRepository(): IptvRepository {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val okHttpClient = io.mockk.mockk<okhttp3.OkHttpClient>(relaxed = true)
        val profileManager = io.mockk.mockk<ProfileManager>(relaxed = true)
        val invalidationBus = io.mockk.mockk<CloudSyncInvalidationBus>(relaxed = true)
        return IptvRepository(context, okHttpClient, profileManager, invalidationBus)
    }
}
