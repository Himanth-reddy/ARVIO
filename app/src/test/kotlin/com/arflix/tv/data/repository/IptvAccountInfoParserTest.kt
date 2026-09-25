package com.arflix.tv.data.repository

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvAccountInfoParserTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    // ── Xtream ──

    @Test fun xtreamReadsExpiryAndStreamLimit() {
        val exp = (now + 212 * day) / 1000
        val info = IptvAccountInfoParser.parseXtream(
            """{"user_info":{"auth":1,"status":"Active","exp_date":"$exp","max_connections":"2","active_cons":"1"},"server_info":{}}""",
            "fp", now
        )!!
        assertEquals(IptvAccountInfo.STATUS_ACTIVE, info.status)
        assertEquals(exp * 1000, info.expiresAtMs)
        assertEquals(2, info.maxConnections)
        assertEquals(false, info.unlimited)
        assertEquals(IptvAccountBadge.Ok(212, exp * 1000), IptvAccountInfoParser.badge(info, now))
    }

    @Test fun xtreamWithoutEndDateIsUnlimited() {
        listOf("null", "\"\"", "\"0\"", "0").forEach { raw ->
            val info = IptvAccountInfoParser.parseXtream(
                """{"user_info":{"status":"Active","exp_date":$raw,"max_connections":1}}""", "fp", now
            )!!
            assertEquals(raw, true, info.unlimited)
            assertEquals(raw, IptvAccountBadge.Unlimited, IptvAccountInfoParser.badge(info, now))
        }
    }

    @Test fun xtreamInactiveStatusCountsAsExpired() {
        val exp = (now + 30 * day) / 1000
        val info = IptvAccountInfoParser.parseXtream(
            """{"user_info":{"status":"Banned","exp_date":"$exp","max_connections":"1"}}""", "fp", now
        )!!
        assertEquals(IptvAccountInfo.STATUS_EXPIRED, info.status)
        assertTrue(IptvAccountInfoParser.badge(info, now) is IptvAccountBadge.Expired)
    }

    @Test fun xtreamPastEndDateCountsAsExpired() {
        val exp = (now - 3 * day) / 1000
        val info = IptvAccountInfoParser.parseXtream(
            """{"user_info":{"status":"Active","exp_date":$exp}}""", "fp", now
        )!!
        assertEquals(IptvAccountInfo.STATUS_EXPIRED, info.status)
    }

    @Test fun xtreamRejectsNonAccountAnswers() {
        assertNull(IptvAccountInfoParser.parseXtream("<html>Forbidden</html>", "fp", now))
        assertNull(IptvAccountInfoParser.parseXtream("", "fp", now))
        assertNull(IptvAccountInfoParser.parseXtream("[]", "fp", now))
        assertNull(IptvAccountInfoParser.parseXtream("""{"user_info":{"auth":0}}""", "fp", now))
    }

    // ── Stalker ──

    @Test fun stalkerPrefersEndDate() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"fname":"x","end_date":"2026-11-15 19:00:00","phone":"Nov 16, 2026"},"text":""}""",
            "fp", now, utc
        )!!
        assertEquals(ms(2026, 11, 15, 19), info.expiresAtMs)
        assertNull(info.maxConnections)
    }

    @Test fun stalkerReadsPhoneTextWithTime() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"mac":"00:1A:79:00:00:00","phone":"November 14, 2026, 6:01 pm","message":""}}""",
            "fp", now, utc
        )!!
        assertEquals(ms(2026, 11, 14, 18, 1), info.expiresAtMs)
        assertEquals(IptvAccountInfo.STATUS_ACTIVE, info.status)
    }

    @Test fun stalkerBareDateIsValidThroughThatDay() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"phone":"Oct 3, 2026"}}""", "fp", now, utc
        )!!
        assertEquals(ms(2026, 10, 4) - 1, info.expiresAtMs)
        val badge = IptvAccountInfoParser.badge(info, now)
        assertEquals(IptvAccountBadge.Warning(9, ms(2026, 10, 4) - 1), badge)
    }

    @Test fun stalkerExpiredAccount() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"end_date":"2022-11-15 19:00:00","phone":"Nov 15, 2022"}}""", "fp", now, utc
        )!!
        assertEquals(IptvAccountInfo.STATUS_EXPIRED, info.status)
        assertEquals(IptvAccountBadge.Expired(ms(2022, 11, 15, 19)), IptvAccountInfoParser.badge(info, now))
    }

    @Test fun stalkerZeroEndDateFallsBackToPhone() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"end_date":"0000-00-00 00:00:00","phone":"February 21, 2027, 11:59 pm"}}""", "fp", now, utc
        )!!
        assertEquals(ms(2027, 2, 21, 23, 59), info.expiresAtMs)
    }

    @Test fun stalkerUnreadableDateNeverBecomesAGuess() {
        val info = IptvAccountInfoParser.parseStalker(
            """{"js":{"mac":"x","phone":"+49 170 000000","message":""}}""", "fp", now, utc
        )!!
        assertEquals(IptvAccountInfo.STATUS_UNAVAILABLE, info.status)
        assertNull(info.expiresAtMs)
        assertEquals(IptvAccountBadge.Unavailable, IptvAccountInfoParser.badge(info, now))
    }

    @Test fun stalkerRejectsNonPortalAnswers() {
        assertNull(IptvAccountInfoParser.parseStalker("<html></html>", "fp", now, utc))
        assertNull(IptvAccountInfoParser.parseStalker("""{"js":""}""", "fp", now, utc))
        assertNull(IptvAccountInfoParser.parseStalker(null, "fp", now, utc))
    }

    // ── Badge thresholds ──

    @Test fun badgeSwitchesToWarningAtFourteenDays() {
        fun badgeAt(daysLeft: Long) = IptvAccountInfoParser.badge(
            IptvAccountInfo("fp", now, IptvAccountInfo.STATUS_ACTIVE, now + daysLeft * day + 1000, false, null), now
        )
        assertTrue(badgeAt(15) is IptvAccountBadge.Ok)
        assertTrue(badgeAt(14) is IptvAccountBadge.Warning)
        assertEquals(0, (IptvAccountInfoParser.badge(
            IptvAccountInfo("fp", now, IptvAccountInfo.STATUS_ACTIVE, now + 1000, false, null), now
        ) as IptvAccountBadge.Warning).daysLeft)
    }

    @Test fun badgeCountsDownWithoutNewRequest() {
        val info = IptvAccountInfo("fp", now, IptvAccountInfo.STATUS_ACTIVE, now + 20 * day, false, null)
        assertTrue(IptvAccountInfoParser.badge(info, now) is IptvAccountBadge.Ok)
        assertTrue(IptvAccountInfoParser.badge(info, now + 10 * day) is IptvAccountBadge.Warning)
        assertTrue(IptvAccountInfoParser.badge(info, now + 21 * day) is IptvAccountBadge.Expired)
    }

    // ── Fingerprint ──

    @Test fun fingerprintFollowsCredentialsNotCosmetics() {
        val portal = StalkerPortalEntry("stalker1", "Portal 1", "http://portal.example/c", "00:1A:79:AA:BB:CC")
        val same = IptvAccountInfoParser.fingerprint(portal.copy(name = "Renamed", enabled = false))
        assertEquals(IptvAccountInfoParser.fingerprint(portal), same)
        assertNotEquals(
            IptvAccountInfoParser.fingerprint(portal),
            IptvAccountInfoParser.fingerprint(portal.copy(macAddress = "00:1A:79:AA:BB:CD"))
        )
    }
}
