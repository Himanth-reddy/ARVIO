package com.arflix.tv.data.repository

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun normalizeHomeServerUrl(rawUrl: String): String {
    val input = rawUrl.trim()
    if (input.isEmpty()) return ""
    val url = (if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
        input
    } else {
        "http://$input"
    }).toHttpUrlOrNull() ?: return ""
    val segments = url.encodedPathSegments.dropLastWhile { it.isEmpty() }
    val webIndex = segments.indexOfLast { it.equals("web", ignoreCase = true) }
    val dashboardSuffix = webIndex >= 0 && (
        webIndex == segments.lastIndex ||
            (webIndex == segments.lastIndex - 1 && segments.last().endsWith(".html", true))
        )
    // Strip only a web-client suffix; retain reverse-proxy prefixes and explicit ports.
    val baseSegments = if (dashboardSuffix) segments.take(webIndex) else segments
    return url.newBuilder()
        .encodedPath("/" + baseSegments.joinToString("/"))
        .query(null)
        .fragment(null)
        .build().toString().trimEnd('/')
}
