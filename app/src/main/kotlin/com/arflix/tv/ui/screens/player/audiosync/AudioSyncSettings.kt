package com.arflix.tv.ui.screens.player.audiosync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings of the audio subtitle sync. Persistence is injected by the platform. */
internal object AudioSyncSettings {
    private val _fallbackEnabled = MutableStateFlow(true)

    /** Sync a subtitle to the audio when the match scan leaves its timing unverified. */
    val fallbackEnabled: StateFlow<Boolean> = _fallbackEnabled.asStateFlow()

    private var save: (key: String, value: Boolean) -> Unit = { _, _ -> }

    fun installPersistence(load: (key: String) -> Boolean?, save: (key: String, value: Boolean) -> Unit) {
        this.save = save
        _fallbackEnabled.value = load(FALLBACK_ENABLED) ?: true
    }

    fun setFallbackEnabled(enabled: Boolean) {
        _fallbackEnabled.value = enabled
        save(FALLBACK_ENABLED, enabled)
    }

    private const val FALLBACK_ENABLED = "audio_sync_fallback_enabled"
}
