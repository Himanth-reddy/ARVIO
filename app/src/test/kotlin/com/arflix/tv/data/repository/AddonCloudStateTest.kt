package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import org.junit.Assert.*
import org.junit.Test

class AddonCloudStateTest {
    private fun addon(id: String) = Addon(id, id, "1", "", true, true, AddonType.CUSTOM, url = "https://$id.example/manifest.json")
    private fun state(ids: List<String>, time: Long, changes: Map<String, AddonChange> = emptyMap()) =
        AddonCloudState(ids.map(::addon), time, changes)

    @Test fun `stale toggle preserves additions from another device`() {
        val merged = reconcileExplicitAddonState(state(listOf("opensubtitles"), 300),
            state(listOf("opensubtitles", "a", "b"), 200))
        assertEquals(listOf("opensubtitles", "a", "b"), merged.addons.map { it.id })
    }

    @Test fun `explicit deletion removes only named addon across stale lists`() {
        val changes = recordAddonChanges(emptyMap(), emptySet(), setOf("a"), 300)
        val merged = reconcileExplicitAddonState(state(listOf("opensubtitles"), 300, changes),
            state(listOf("opensubtitles", "a", "b"), 400))
        assertEquals(listOf("opensubtitles", "b"), merged.addons.map { it.id })
        assertEquals(changes, merged.changes)
    }

    @Test fun `refresh does not turn stale entries into reinstalls`() {
        val changes = mapOf("a" to AddonChange(200, true))
        assertEquals(changes, recordAddonChanges(changes, emptySet(), emptySet(), 300))
        val merged = reconcileExplicitAddonState(state(listOf("opensubtitles", "a"), 400),
            state(listOf("opensubtitles"), 200, changes))
        assertEquals(listOf("opensubtitles"), merged.addons.map { it.id })
    }

    @Test fun `reinstall wins over old removal replay`() {
        val removed = mapOf("a" to AddonChange(200, true))
        val added = recordAddonChanges(removed, setOf("a"), emptySet(), 300)
        val merged = reconcileExplicitAddonState(state(listOf("opensubtitles", "a"), 300, added),
            state(listOf("opensubtitles"), 400, removed))
        assertEquals(listOf("opensubtitles", "a"), merged.addons.map { it.id })
        assertFalse(merged.changes.getValue("a").removed)
    }

    @Test fun `deletion is deterministic on a timestamp tie and survives replay`() {
        val add = mapOf("a" to AddonChange(200, false))
        val remove = mapOf("a" to AddonChange(200, true))
        assertEquals(remove, mergeAddonChanges(add, remove))
        assertEquals(remove, mergeAddonChanges(remove, add))
    }

    @Test fun `OpenSubtitles and invalid records are protected`() {
        val changes = recordAddonChanges(emptyMap(), emptySet(), setOf("opensubtitles", "a"), 300)
        assertEquals(setOf("a"), changes.keys)
        assertTrue(mergeAddonChanges(emptyMap(), mapOf("" to AddonChange(2, true), "b" to AddonChange(0, true))).isEmpty())
    }
}
