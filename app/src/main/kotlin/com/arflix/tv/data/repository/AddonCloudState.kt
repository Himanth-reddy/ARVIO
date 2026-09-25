package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon

data class AddonChange(val updatedAt: Long = 0L, val removed: Boolean = false)

data class AddonCloudState(
    val addons: List<Addon>,
    val updatedAt: Long,
    val changes: Map<String, AddonChange>
)

internal fun mergeAddonChanges(
    local: Map<String, AddonChange>,
    remote: Map<String, AddonChange>
): Map<String, AddonChange> = buildMap {
    for ((id, change) in local.entries + remote.entries) {
        if (id.isBlank() || change.updatedAt <= 0 || (id == "opensubtitles" && change.removed)) continue
        val previous = get(id)
        if (previous == null || change.updatedAt > previous.updatedAt ||
            (change.updatedAt == previous.updatedAt && change.removed)) put(id, change)
    }
}

internal fun reconcileExplicitAddonState(local: AddonCloudState, remote: AddonCloudState): AddonCloudState {
    val changes = mergeAddonChanges(local.changes, remote.changes)
    val lists = if (local.updatedAt > remote.updatedAt) local.addons + remote.addons else remote.addons + local.addons
    return AddonCloudState(
        addons = lists.distinctBy { it.id }.filterNot { changes[it.id]?.removed == true },
        updatedAt = maxOf(local.updatedAt, remote.updatedAt, changes.values.maxOfOrNull { it.updatedAt } ?: 0L),
        changes = changes
    )
}

internal fun recordAddonChanges(previous: Map<String, AddonChange>, added: Set<String>, removed: Set<String>, timestamp: Long): Map<String, AddonChange> {
    val edits = added.associateWith { AddonChange(timestamp, false) } + removed.associateWith { AddonChange(timestamp, true) }
    return mergeAddonChanges(previous, edits)
}
