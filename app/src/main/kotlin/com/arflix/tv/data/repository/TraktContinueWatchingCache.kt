package com.arflix.tv.data.repository

/** Cache only complete show-progress results, before playback deduplication. */
internal class TraktUpNextCache(private val ttlMs: Long = 300_000L) {
    private data class Snapshot(
        val profileId: String,
        val signature: String,
        val includeSpecials: Boolean,
        val fetchedAtMs: Long,
        val candidates: List<ContinueWatchingCandidate>,
    )

    private var snapshot: Snapshot? = null

    @Synchronized
    fun get(profileId: String, signature: String?, includeSpecials: Boolean, nowMs: Long): List<ContinueWatchingCandidate>? {
        val saved = snapshot ?: return null
        return saved.candidates.takeIf {
            signature != null && saved.signature == signature && saved.profileId == profileId &&
                saved.includeSpecials == includeSpecials && nowMs - saved.fetchedAtMs in 0 until ttlMs
        }
    }

    @Synchronized
    fun put(profileId: String, signature: String, includeSpecials: Boolean, nowMs: Long, candidates: List<ContinueWatchingCandidate>) {
        snapshot = Snapshot(profileId, signature, includeSpecials, nowMs, candidates.toList())
    }

    @Synchronized
    fun clear() {
        snapshot = null
    }
}

/** Shared by queued CW reads, independently of whether a saved row exists. */
internal class TraktRequestBackoff {
    private var failures = 0
    private var openUntilMs = 0L

    @Synchronized
    fun isOpen(nowMs: Long): Boolean = nowMs < openUntilMs

    @Synchronized
    fun rateLimited(nowMs: Long, retryAfterSeconds: Long? = null): Long {
        failures = (failures + 1).coerceAtMost(6)
        val exponentialMs = (60_000L shl (failures - 1)).coerceAtMost(30 * 60_000L)
        val serverMs = retryAfterSeconds?.coerceIn(0L, Long.MAX_VALUE / 1000L)?.times(1000L) ?: 0L
        val cooldownMs = maxOf(exponentialMs, serverMs).coerceAtMost(Long.MAX_VALUE - nowMs)
        openUntilMs = maxOf(openUntilMs, nowMs + cooldownMs)
        return openUntilMs - nowMs
    }

    @Synchronized
    fun reset() {
        failures = 0
        openUntilMs = 0L
    }
}
