package com.arflix.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonSetupTest {

    @Test
    fun `configure path is inserted before query and preserves encoded config`() {
        for (input in listOf(
            "https://addon.example?token=test",
            "https://addon.example/manifest.json?token=test",
            "https://addon.example/manifest.json/?token=test"
        )) {
            assertEquals("https://addon.example/configure?token=test",
                AddonSetup.advertisedConfigureUrl(input, AddonBehaviorHints(configurable = true)))
        }
        assertEquals(
            "https://addon.example/stremio/a%2Fb/configure?token=a%2Fb%2B&lang=en",
            AddonSetup.advertisedConfigureUrl(
                "https://addon.example/stremio/a%2Fb/manifest.json?token=a%2Fb%2B&lang=en#ignored",
                AddonBehaviorHints(configurationRequired = true)
            )
        )
    }

    @Test
    fun `invalid configure transports are rejected`() {
        for (input in listOf("", "not a url", "javascript:alert(1)", "file:///manifest.json")) {
            assertNull(AddonSetup.advertisedConfigureUrl(input, AddonBehaviorHints(configurable = true)))
        }
    }

    private fun addon(
        id: String = "addon",
        type: AddonType = AddonType.CUSTOM,
        hints: AddonBehaviorHints? = null,
        transportUrl: String? = "https://addon.example.com",
        configureUrl: String? = null
    ) = Addon(
        id = id,
        name = "Addon",
        version = "1.0.0",
        description = "",
        isInstalled = true,
        type = type,
        manifest = AddonManifest(id = "org.example.addon", name = "Addon", version = "1.0.0", behaviorHints = hints),
        transportUrl = transportUrl,
        configureUrl = configureUrl
    )

    @Test
    fun `configurable manifest advertises configure page on its transport url`() {
        assertEquals(
            "https://addon.example.com/abc/configure",
            AddonSetup.advertisedConfigureUrl(
                "https://addon.example.com/abc/",
                AddonBehaviorHints(configurable = true)
            )
        )
    }

    @Test
    fun `configuration required also means there is a configure page`() {
        assertEquals(
            "https://addon.example.com/configure",
            AddonSetup.advertisedConfigureUrl(
                "https://addon.example.com",
                AddonBehaviorHints(configurationRequired = true)
            )
        )
    }

    @Test
    fun `manifest without hints advertises nothing`() {
        assertNull(AddonSetup.advertisedConfigureUrl("https://addon.example.com", null))
        assertNull(AddonSetup.advertisedConfigureUrl("https://addon.example.com", AddonBehaviorHints()))
        assertNull(AddonSetup.advertisedConfigureUrl(null, AddonBehaviorHints(configurable = true)))
    }

    @Test
    fun `parent transport url drops the config segment`() {
        assertEquals(
            "https://addon.example.com",
            AddonSetup.parentTransportUrl("https://addon.example.com/eyJsYW5nIjoiZGUifQ")
        )
        assertEquals(
            "https://addon.example.com/stremio",
            AddonSetup.parentTransportUrl("https://addon.example.com/stremio/cfg/")
        )
    }

    @Test
    fun `parent transport url is null without a segment or with a query`() {
        assertNull(AddonSetup.parentTransportUrl("https://addon.example.com"))
        assertNull(AddonSetup.parentTransportUrl("https://addon.example.com/"))
        assertNull(AddonSetup.parentTransportUrl("https://addon.example.com/cfg?token=1"))
        assertNull(AddonSetup.parentTransportUrl("not a url"))
    }

    @Test
    fun `remembered configure url wins over the manifest`() {
        val configured = addon(configureUrl = "https://addon.example.com/configure")
        assertEquals("https://addon.example.com/configure", configured.settingsPageUrl)
        assertNull(addon().settingsPageUrl)
    }

    @Test
    fun `needs configuration follows the manifest flag`() {
        assertTrue(addon(hints = AddonBehaviorHints(configurationRequired = true)).needsConfiguration)
        assertFalse(addon(hints = AddonBehaviorHints(configurable = true)).needsConfiguration)
    }

    @Test
    fun `only the preinstalled subtitles addon is not removable`() {
        assertFalse(addon(id = "opensubtitles", type = AddonType.SUBTITLE).isRemovable)
        assertTrue(addon(id = "opensubtitles", type = AddonType.CUSTOM).isRemovable)
    }
}
