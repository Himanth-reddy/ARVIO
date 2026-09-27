package com.arflix.tv.data.repository

import retrofit2.HttpException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight per-addon HTTP 429 cooldown tracker (Issue 2).
 *
 * Proactive prefetch ([DetailsViewModel.prefetchStreamsInBackground]) and user-triggered
 * [StreamRepository.resolveMovieStreamsProgressive] /
 * [StreamRepository.resolveEpisodeStreamsProgressive] both fan out to every installed Stremio
 * addon. Services like Torrentio / MediaFusion rate-limit by IP, so a 429 observed from one
 * scrape should suppress that addon for a short window across *both* paths — otherwise the
 * next Details entry re-scrapes the same addon and burns quota the user needs for Play.
 *
 * Thread-safe; clock source is wall-clock millis which is fine for a best-effort cooldown.
 */
object AddonRateLimitTracker {
    private const val ADDON_COOLDOWN_MS = 30_000L

    // A bare "429" substring over-matches (ports, counts, titles). Only treat it
    // as a rate limit when it sits next to an HTTP/status/code token or
    // rate-limit phrasing.
    private val HTTP_429_TOKEN =
        Regex("(?i)\\b(http|status|code|error)\\b[^\\w]{0,10}\\b429\\b|\\b429\\b[^\\w]{0,10}(too many|rate limit|ratelimit)")

    private val cooldownUntilMs = ConcurrentHashMap<String, Long>()

    fun recordRateLimit(addonId: String) {
        val key = addonId.trim()
        if (key.isEmpty()) return
        cooldownUntilMs[key] = System.currentTimeMillis() + ADDON_COOLDOWN_MS
    }

    fun isCoolingDown(addonId: String): Boolean {
        val key = addonId.trim()
        if (key.isEmpty()) return false
        val until = cooldownUntilMs[key] ?: return false
        if (System.currentTimeMillis() >= until) {
            cooldownUntilMs.remove(key, until)
            return false
        }
        return true
    }

    /** True when [throwable] looks like an HTTP 429 / rate-limit rejection. */
    fun isRateLimitError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val http = throwable as? HttpException
        if (http != null && http.code() == 429) return true
        val message = throwable.message.orEmpty()
        if (HTTP_429_TOKEN.containsMatchIn(message)) return true
        val lower = message.lowercase(Locale.US)
        if (lower.contains("too many requests")) return true
        if (lower.contains("rate limit") || lower.contains("ratelimit")) return true
        val cause = throwable.cause
        return cause != null && cause !== throwable && isRateLimitError(cause)
    }
}
