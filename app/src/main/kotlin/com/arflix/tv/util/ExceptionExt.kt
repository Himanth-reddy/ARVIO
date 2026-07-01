package com.arflix.tv.util

import kotlinx.coroutines.CancellationException

/**
 * Helper to rethrow CancellationException so that coroutine cancellation
 * isn't swallowed by generic catch (e: Exception) blocks.
 */
fun Exception.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
