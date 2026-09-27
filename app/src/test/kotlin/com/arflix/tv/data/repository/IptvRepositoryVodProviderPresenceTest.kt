package com.arflix.tv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Has this user any IPTV provider at all?" - the question the details screen
 * asks before deciding which empty state to show. It has to be answerable
 * without asking a provider anything, because the answer is needed exactly when
 * a lookup came back empty.
 */
class IptvRepositoryVodProviderPresenceTest {

    private fun newRepository(): IptvRepository {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val okHttpClient = io.mockk.mockk<okhttp3.OkHttpClient>(relaxed = true)
        val profileManager = io.mockk.mockk<ProfileManager>(relaxed = true)
        val invalidationBus = io.mockk.mockk<CloudSyncInvalidationBus>(relaxed = true)
        return IptvRepository(context, okHttpClient, profileManager, invalidationBus)
    }

    private fun portal(
        importVod: Boolean? = true,
        importSeries: Boolean? = true,
        enabled: Boolean = true
    ) = StalkerPortalEntry(
        id = "portal-1",
        name = "Portal 1",
        portalUrl = "http://portal.example.com",
        macAddress = "00:1A:79:AA:BB:CC",
        enabled = enabled,
        importVod = importVod,
        importSeries = importSeries
    )

    @Test
    fun `an empty setup has no provider`() {
        assertFalse(newRepository().hasVodSearchProviders(IptvConfig()))
    }

    @Test
    fun `a stalker portal left on for movies is a provider`() {
        val config = IptvConfig(stalkerPortals = listOf(portal()))

        assertTrue(newRepository().hasVodSearchProviders(config))
    }

    @Test
    fun `a portal left on for series only still counts`() {
        val config = IptvConfig(
            stalkerPortals = listOf(portal(importVod = false, importSeries = true))
        )

        assertTrue(newRepository().hasVodSearchProviders(config))
    }

    @Test
    fun `a portal switched off for both halves is not a provider`() {
        val config = IptvConfig(
            stalkerPortals = listOf(portal(importVod = false, importSeries = false))
        )

        assertFalse(newRepository().hasVodSearchProviders(config))
    }

    @Test
    fun `a disabled portal is not a provider`() {
        val config = IptvConfig(stalkerPortals = listOf(portal(enabled = false)))

        assertFalse(newRepository().hasVodSearchProviders(config))
    }

    @Test
    fun `a portal hidden by stream integration settings is not counted`() {
        val repository = newRepository()
        val config = IptvConfig(stalkerPortals = listOf(portal()))
        assertFalse(repository.hasVodSearchProviders(config, emptySet()))
        assertFalse(repository.hasVodSearchProviders(config, setOf("iptv_stalker:other")))
        assertTrue(repository.hasVodSearchProviders(config, setOf("iptv_stalker:portal-1")))
    }
}
