package com.arflix.tv.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emitted when the app returns to the foreground, so screens can re-read state
 * another device may have changed while this one was away.
 *
 * This is raised from the activity rather than from a screen because the screen
 * that needs it is usually not the one on display. A TV left on a details page
 * overnight resumes onto that page, and a Home-scoped lifecycle observer never
 * runs; its ViewModel is still alive in the back stack, though, so an app-wide
 * signal reaches it either way.
 *
 * The first resume after launch is deliberately not signalled — startup already
 * fetches, and forcing a second pass there is what previously cancelled the
 * cache fast path on every launch.
 */
@Singleton
class AppForegroundSignals @Inject constructor() {
    private val _returnedToForeground = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Fires on every foreground except the first one after launch. */
    val returnedToForeground: SharedFlow<Unit> = _returnedToForeground.asSharedFlow()

    fun notifyReturnedToForeground() {
        _returnedToForeground.tryEmit(Unit)
    }
}
