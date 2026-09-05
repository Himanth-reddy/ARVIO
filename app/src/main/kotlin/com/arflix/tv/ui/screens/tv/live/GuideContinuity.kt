package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.IptvNowNext

/** Rebase cached schedules on the clock, without fetching or discarding archive data. */
internal fun IptvNowNext.atTime(nowMs: Long): IptvNowNext {
    val programs = (recent + listOfNotNull(now, next, later) + upcoming)
        .distinctBy { Triple(it.startUtcMillis, it.endUtcMillis, it.title) }
        .sortedBy { it.startUtcMillis }
    val future = programs.filter { it.startUtcMillis > nowMs }
    return copy(
        now = programs.lastOrNull { it.isLive(nowMs) },
        next = future.getOrNull(0),
        later = future.getOrNull(1),
        upcoming = future,
        recent = programs.filter { it.endUtcMillis <= nowMs },
    )
}

/** Retains neighbouring windows, but never the entire provider's guide in the UI heap. */
internal fun retainGuideWindows(
    previous: Map<String, IptvNowNext>,
    fresh: Map<String, IptvNowNext>,
    requested: Set<String>,
    limit: Int = 160,
): Map<String, IptvNowNext> = LinkedHashMap(previous).apply {
    requested.forEach { id ->
        val old = remove(id)
        val next = fresh[id]?.takeIf {
            it.now != null || it.next != null || it.later != null || it.upcoming.isNotEmpty() || it.recent.isNotEmpty()
        } ?: old
        if (next != null) put(id, next)
    }
    while (size > limit) remove(keys.first())
}

internal class LiveWindowRecovery(private val clock: () -> Long) {
    private var lastRecoveryAt: Long? = null

    fun claim(isCatchup: Boolean): Boolean {
        if (isCatchup) return false
        val now = clock()
        if (lastRecoveryAt?.let { now - it < 60_000L } == true) return false
        lastRecoveryAt = now
        return true
    }
}
