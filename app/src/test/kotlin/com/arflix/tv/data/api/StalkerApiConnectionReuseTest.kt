package com.arflix.tv.data.api

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one number behind "the first source search finds nothing, the
 * second finds everything".
 *
 * Three packet captures against the user's portal, on three days, all show the
 * same thing: the portal closes an idle connection after ten seconds. OkHttp
 * keeps one for five minutes by default and
 * [com.arflix.tv.network.withIptvProviderRequestGuard] switches its connection
 * retry off, so a request made after a longer pause went into a socket that no
 * longer existed and was lost without a second try.
 *
 * Raise the keep-alive back to or past the portal's window and the bug is back,
 * silently and only for users whose provider behaves this way - hence a test
 * rather than a comment.
 */
class StalkerApiConnectionReuseTest {

    @Test
    fun `idle connections are dropped well before the portal closes them`() {
        assertTrue(
            "keep-alive ${StalkerApi.IDLE_KEEP_ALIVE_SECONDS}s must stay below the measured " +
                "${StalkerApi.MEASURED_PORTAL_IDLE_CLOSE_SECONDS}s the portal allows",
            StalkerApi.IDLE_KEEP_ALIVE_SECONDS < StalkerApi.MEASURED_PORTAL_IDLE_CLOSE_SECONDS
        )
    }

    @Test
    fun `the margin is at least half the portal window`() {
        assertTrue(
            "a keep-alive close to the portal's limit leaves no room for a slow request",
            StalkerApi.IDLE_KEEP_ALIVE_SECONDS * 2 <= StalkerApi.MEASURED_PORTAL_IDLE_CLOSE_SECONDS
        )
    }
}
