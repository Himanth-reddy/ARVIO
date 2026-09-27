package com.arflix.tv.ui.screens.settings

import com.arflix.tv.R
import com.arflix.tv.data.api.TraktDeviceCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TraktActivationStateTest {
    private fun pending(code: String = "attempt-a") = SettingsUiState(
        traktCode = TraktDeviceCode(code, "USERCODE", "https://trakt.tv/activate", 600, 5),
        traktCodeExpiresAtMillis = 600_000L,
        isTraktPolling = true
    )

    @Test fun successClosesOnlyItsOwnDialog() {
        val connected = pending().copy(isTraktAuthenticated = true, traktAuthOutcome = TraktAuthOutcome.CONNECTED)
        val closed = connected.dismissTraktSuccess("attempt-a")
        assertNull(closed.traktCode)
        assertNull(closed.traktCodeExpiresAtMillis)
        assertNull(closed.traktAuthOutcome)
        assertTrue(closed.isTraktAuthenticated)
    }

    @Test fun oldSuccessTimerCannotCloseANewActivation() = runTest {
        var state = pending().copy(traktAuthOutcome = TraktAuthOutcome.CONNECTED)
        launch {
            delay(2_000)
            state = state.dismissTraktSuccess("attempt-a")
        }
        runCurrent()
        advanceTimeBy(1_000)
        state = pending("attempt-b")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("attempt-b", state.traktCode?.deviceCode)
        assertTrue(state.isTraktPolling)
        assertNull(state.traktAuthOutcome)
    }

    @Test fun timerDoesNotChangeDismissedOrNonSuccessState() {
        val dismissed = SettingsUiState()
        assertSame(dismissed, dismissed.dismissTraktSuccess("attempt-a"))
        val expired = pending().copy(traktAuthOutcome = TraktAuthOutcome.EXPIRED)
        assertSame(expired, expired.dismissTraktSuccess("attempt-a"))
    }

    @Test fun timeoutAndHttp410ShowTheSameRetryPanel() {
        val initial = pending()
        val localTimeout = initial.finishTraktActivationPolling(null)
        // This is the failure assigned by the HTTP 410 branch in startTraktPolling.
        val serverTimeout = initial.finishTraktActivationPolling(SettingsMessage.Res(R.string.settings_trakt_code_expired))
        assertEquals(localTimeout, serverTimeout)
        assertEquals(TraktAuthOutcome.EXPIRED, serverTimeout.traktAuthOutcome)
        assertEquals(initial.traktCode, serverTimeout.traktCode)
        assertFalse(serverTimeout.isTraktPolling)
        assertNull(serverTimeout.toastMessage)
    }

    @Test fun otherErrorsStillCloseTheDialogAndShowTheirMessage() {
        val errors = listOf(
            SettingsMessage.Res(R.string.settings_trakt_code_invalid),
            SettingsMessage.Res(R.string.settings_trakt_code_used),
            SettingsMessage.Res(R.string.settings_trakt_denied),
            SettingsMessage.Raw("Network unavailable")
        )
        errors.forEach { error ->
            val state = pending().finishTraktActivationPolling(error)
            assertNull(state.traktCode)
            assertNull(state.traktAuthOutcome)
            assertFalse(state.isTraktPolling)
            assertEquals(error, state.toastMessage)
            assertEquals(ToastType.ERROR, state.toastType)
        }
    }
}
