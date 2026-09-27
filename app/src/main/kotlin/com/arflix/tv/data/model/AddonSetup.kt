package com.arflix.tv.data.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Helpers for opening a Stremio addon's own settings page.
 *
 * Stremio addons keep their settings in the install URL itself
 * (`https://host/<config>/manifest.json`). The official addon SDK strips both
 * `configurable` and `configurationRequired` from the manifest it serves for such a
 * configured URL, so an addon that has been set up once no longer says it can be
 * configured. ARVIO therefore remembers the settings page in [Addon.configureUrl]
 * and, when the configured manifest is silent, looks one path segment up for the
 * unconfigured manifest of the same addon.
 */
object AddonSetup {

    /** Settings page advertised by the manifest at [transportUrl], or null if it has none. */
    fun advertisedConfigureUrl(transportUrl: String?, hints: AddonBehaviorHints?): String? {
        if (hints?.configurable != true && hints?.configurationRequired != true) return null
        val url = transportUrl?.trim()?.toHttpUrlOrNull() ?: return null
        val path = url.encodedPath.trimEnd('/').removeSuffix("/manifest.json")
        return url.newBuilder()
            .encodedPath("$path/configure")
            .fragment(null)
            .build()
            .toString()
    }

    /**
     * The transport URL one path segment up, where the unconfigured manifest of a configured
     * addon lives. Null when there is no segment to drop or the URL carries a query.
     */
    fun parentTransportUrl(transportUrl: String?): String? {
        val url = transportUrl?.trim()?.trimEnd('/')?.toHttpUrlOrNull() ?: return null
        if (url.query != null) return null
        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        return url.newBuilder()
            .encodedPath("/")
            .apply { segments.dropLast(1).forEach { addPathSegment(it) } }
            .build()
            .toString()
            .trimEnd('/')
    }
}

/** The addon says it cannot deliver anything until it has been set up. */
val Addon.needsConfiguration: Boolean
    get() = manifest?.behaviorHints?.configurationRequired == true

/** Where the user changes this addon's settings, or null when it has no settings page. */
val Addon.settingsPageUrl: String?
    get() = configureUrl ?: AddonSetup.advertisedConfigureUrl(transportUrl, manifest?.behaviorHints)

/** The preinstalled OpenSubtitles addon cannot be removed. */
val Addon.isRemovable: Boolean
    get() = !(id == "opensubtitles" && type == AddonType.SUBTITLE)
