package com.arflix.tv.ui.screens.player.subtitles

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the "find best match" scan takes subtitle candidates: which come first, and how many it may
 * download at all. Apart from the ViewModel so both can be tested.
 */
internal object MatchCandidatePool {

    /**
     * Providers in turn, each in its own ranked order, starting with the provider of the
     * best-ranked subtitle. Release-name ranking puts addons without release names at the very end
     * (OpenSubtitles publishes numeric ids): Peaky Blinders S01E04 (Sept 2026) filled a ten-slot
     * pool with Wizdom/Ktuvit mirrors of two files and never downloaded the OpenSubtitles file that
     * matched. Taking providers in turn keeps every provider near the front of a bounded pool.
     */
    fun <T> providerInterleaved(ranked: List<T>, providerOf: (T) -> String): List<T> {
        val byProvider = LinkedHashMap<String, ArrayDeque<T>>()
        for (item in ranked) byProvider.getOrPut(providerOf(item)) { ArrayDeque() }.addLast(item)
        val ordered = ArrayList<T>(ranked.size)
        while (byProvider.values.any { it.isNotEmpty() }) {
            for (queue in byProvider.values) queue.removeFirstOrNull()?.let(ordered::add)
        }
        return ordered
    }

    /**
     * Loads [pool] in order, [batchSize] at a time, up to [maxItems]. The deadline includes
     * in-flight loads; completed results survive a timeout. [now] must use a monotonic clock.
     *
     * Returns what loaded (non-null), in pool order, and how many candidates were tried.
     */
    suspend fun <T, R : Any> loadBounded(
        pool: List<T>,
        batchSize: Int,
        maxItems: Int,
        budgetMs: Long,
        startedAtMs: Long,
        now: () -> Long,
        load: suspend (T) -> R?
    ): Pair<List<Pair<T, R>>, Int> {
        val remainingMs = budgetMs - (now() - startedAtMs).coerceAtLeast(0)
        if (remainingMs <= 0 || maxItems <= 0) return emptyList<Pair<T, R>>() to 0
        val loaded = java.util.concurrent.ConcurrentHashMap<Int, Pair<T, R>>()
        var tried = 0
        withTimeoutOrNull(remainingMs) {
            for (batch in pool.take(maxItems).withIndex().chunked(batchSize.coerceAtLeast(1))) {
                if (now() - startedAtMs >= budgetMs) break
                tried += batch.size
                coroutineScope {
                    batch.map { (index, item) ->
                        async { load(item)?.let { loaded[index] = item to it } }
                    }.awaitAll()
                }
            }
        }
        return loaded.toSortedMap().values.toList() to tried
    }
}

/**
 * Applies the retimer's verdicts to the scored candidates. The retimer is the arbiter for what it
 * evaluated: a candidate it could not pair consistently with any reference is rejected — kept
 * below the accept bar, unshifted — even where the overlap sweep wanted to accept or shift it, and
 * that holds when NO candidate retimed as well. (It used to return the sweep's verdicts unchanged
 * in that case, so a subtitle the consistency check had just refused could still be accepted.)
 * Candidates the retimer did not evaluate (past its cap, or not loaded) keep their own verdict.
 */
internal fun <C, K, R> applyRetimeVerdicts(
    candidates: List<C>,
    keyOf: (C) -> K?,
    evaluated: Set<K>,
    confident: Map<K, R>,
    reject: (C) -> C,
    accept: (C, R) -> C
): List<C> = candidates.map { candidate ->
    val key = keyOf(candidate) ?: return@map candidate
    val result = confident[key]
    when {
        result != null -> accept(candidate, result)
        key in evaluated -> reject(candidate)
        else -> candidate
    }
}
