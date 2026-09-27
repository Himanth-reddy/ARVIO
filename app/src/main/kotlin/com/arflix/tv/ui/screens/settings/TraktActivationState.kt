package com.arflix.tv.ui.screens.settings

import com.arflix.tv.R

internal fun SettingsUiState.dismissTraktSuccess(deviceCode: String): SettingsUiState {
    // A completed attempt's timer must not dismiss a later activation.
    if (traktCode?.deviceCode != deviceCode || traktAuthOutcome != TraktAuthOutcome.CONNECTED) return this
    return copy(
        traktCode = null,
        traktCodeExpiresAtMillis = null,
        traktAuthOutcome = null
    )
}

internal fun SettingsUiState.finishTraktActivationPolling(failure: SettingsMessage?): SettingsUiState {
    val expired = failure == null || failure == SettingsMessage.Res(R.string.settings_trakt_code_expired)
    return if (expired) {
        copy(
            traktAuthOutcome = TraktAuthOutcome.EXPIRED,
            isTraktAuthStarting = false,
            isTraktPolling = false,
            traktUsername = null
        )
    } else {
        copy(
            traktCode = null,
            traktCodeExpiresAtMillis = null,
            traktAuthOutcome = null,
            isTraktAuthStarting = false,
            isTraktPolling = false,
            traktUsername = null,
            toastMessage = failure,
            toastType = ToastType.ERROR
        )
    }
}
