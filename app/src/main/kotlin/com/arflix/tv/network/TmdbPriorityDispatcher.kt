package com.arflix.tv.network

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-level priority dispatcher for api.themoviedb.org traffic (Issue 1).
 *
 * Replaces the hand-tuned `delay(180/220/260/320/420ms)` stagger in DetailsViewModel
 * with permit-based gating. Both HomeViewModel (catalog pages, logo/hero decoration)
 * and DetailsViewModel (primary metadata + secondary waves) submit here, so the
 * budget is shared across screens — per-ViewModel semaphores would be invisible to
 * each other while both contend on OkHttp's single per-host connection pool.
 *
 * Budgets (total 5 = OkHttp's default maxRequestsPerHost, so we never queue
 * inside OkHttp itself):
 * - [Priority.IMMEDIATE]: 3 slots — metadata needed before first render
 *   (details, external IDs, logo, season episodes, home category pages are NOT here).
 * - [Priority.DEFERRED]: 1 slot — visible but not critical (trailer, cast,
 *   providers, similar + logos, collection).
 * - [Priority.BACKGROUND]: 1 slot — the user never waits on this (reviews,
 *   home catalog pages, logo/hero decoration, season prefetch).
 *
 * Properties:
 * - Fast networks: permits are free, so deferred work starts immediately — the
 *   artificial 180–420ms dead wait disappears.
 * - Slow networks: deferred work queues behind in-flight immediates automatically.
 * - Leaving the screen cancels the ViewModel's child jobs; [withPermit] is
 *   cancellable, so queued deferred acquisitions drop — the cheap equivalent of
 *   cancelAllBelowPriority(IMMEDIATE) via structured concurrency.
 */
@Singleton
class TmdbPriorityDispatcher @Inject constructor() {

    enum class Priority {
        IMMEDIATE,
        DEFERRED,
        BACKGROUND,
    }

    private val immediateSlots = Semaphore(permits = 3)
    private val deferredSlots = Semaphore(permits = 1)
    private val backgroundSlots = Semaphore(permits = 1)

    suspend fun <T> withPermit(priority: Priority, block: suspend () -> T): T {
        return when (priority) {
            Priority.IMMEDIATE -> immediateSlots.withPermit { block() }
            Priority.DEFERRED -> deferredSlots.withPermit { block() }
            Priority.BACKGROUND -> backgroundSlots.withPermit { block() }
        }
    }
}
