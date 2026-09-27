package com.arflix.tv.network

import com.arflix.tv.network.TmdbPriorityDispatcher.Priority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbPriorityDispatcherTest {
    @Test
    fun visiblePaginationAndInitialRowsDoNotWaitForBackgroundArtwork() = runTest {
        val dispatcher = TmdbPriorityDispatcher()
        val releaseArtwork = CompletableDeferred<Unit>()
        var artworkFinished = false
        var paginationStarted = false
        var initialRowStarted = false
        val artwork = launch {
            dispatcher.withPermit(Priority.BACKGROUND) {
                releaseArtwork.await()
                artworkFinished = true
            }
        }
        runCurrent()
        launch { dispatcher.withPermit(Priority.IMMEDIATE) { paginationStarted = true } }
        launch { dispatcher.withPermit(Priority.DEFERRED) { initialRowStarted = true } }
        runCurrent()
        assertTrue(paginationStarted)
        assertTrue(initialRowStarted)
        assertFalse(artworkFinished)
        releaseArtwork.complete(Unit)
        artwork.join()
    }

    @Test
    fun saturatedLanesKeepTheThreeTwoOneLimits() = runTest {
        val dispatcher = TmdbPriorityDispatcher()
        val release = CompletableDeferred<Unit>()
        val active = mutableMapOf<Priority, Int>()
        var peakTotal = 0
        val jobs = Priority.entries.flatMap { priority ->
            List(12) {
                launch {
                    dispatcher.withPermit(priority) {
                        active[priority] = active.getOrDefault(priority, 0) + 1
                        peakTotal = maxOf(peakTotal, active.values.sum())
                        try {
                            release.await()
                        } finally {
                            active[priority] = active.getValue(priority) - 1
                        }
                    }
                }
            }
        }
        runCurrent()
        assertEquals(3, active[Priority.IMMEDIATE])
        assertEquals(2, active[Priority.DEFERRED])
        assertEquals(1, active[Priority.BACKGROUND])
        assertEquals(6, peakTotal)
        release.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(6, peakTotal)
        assertEquals(0, active.values.sum())
    }

    @Test
    fun cancelledQueuedWorkNeverRunsAndDoesNotLeakPermits() = runTest {
        val dispatcher = TmdbPriorityDispatcher()
        val release = CompletableDeferred<Unit>()
        val holding = launch { dispatcher.withPermit(Priority.BACKGROUND) { release.await() } }
        runCurrent()
        var cancelledWorkRan = false
        val queued = launch { dispatcher.withPermit(Priority.BACKGROUND) { cancelledWorkRan = true } }
        runCurrent()
        queued.cancelAndJoin()
        release.complete(Unit)
        holding.join()
        var nextRan = false
        dispatcher.withPermit(Priority.BACKGROUND) { nextRan = true }
        assertFalse(cancelledWorkRan)
        assertTrue(nextRan)
    }

    @Test
    fun cancellingRunningWorkReleasesItsPermit() = runTest {
        val dispatcher = TmdbPriorityDispatcher()
        val holding = launch {
            dispatcher.withPermit(Priority.BACKGROUND) { CompletableDeferred<Unit>().await() }
        }
        runCurrent()
        var nextRan = false
        val next = launch { dispatcher.withPermit(Priority.BACKGROUND) { nextRan = true } }
        runCurrent()
        assertFalse(nextRan)
        holding.cancelAndJoin()
        next.join()
        assertTrue(nextRan)
    }

    @Test
    fun failedWorkReleasesItsPermit() = runTest {
        val dispatcher = TmdbPriorityDispatcher()
        val failure = runCatching {
            dispatcher.withPermit(Priority.BACKGROUND) { error("test failure") }
        }
        assertTrue(failure.isFailure)
        assertEquals("next", dispatcher.withPermit(Priority.BACKGROUND) { "next" })
    }
}
