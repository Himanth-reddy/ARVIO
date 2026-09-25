package com.arflix.tv.navigation

import com.arflix.tv.data.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSessionGateTest {
    private val profile = Profile(id = "one", name = "Profile", avatarColor = 0L)

    @Test
    fun `persisted profile cannot open a link before selection`() {
        val selected = ProfileSessionGate.initialProfileId(false, profile)
        assertNull(selected)
        assertFalse(ProfileSessionGate.canOpenLink(profile.id, selected))
    }

    @Test
    fun `skip selection only admits an unlocked profile`() {
        assertEquals(profile.id, ProfileSessionGate.initialProfileId(true, profile))
        assertNull(ProfileSessionGate.initialProfileId(true, profile.copy(isLocked = true)))
        assertNull(ProfileSessionGate.initialProfileId(true, null))
    }

    @Test
    fun `successful selection permits link for that profile only`() {
        assertTrue(ProfileSessionGate.canOpenLink(profile.id, profile.id))
        assertFalse(ProfileSessionGate.canOpenLink("other", profile.id))
        assertFalse(ProfileSessionGate.canOpenLink(null, profile.id))
    }

    @Test
    fun `switching profile blocks pending links until next successful selection`() {
        assertFalse(ProfileSessionGate.canOpenLink(profile.id, null))
        assertFalse(ProfileSessionGate.canOpenLink(null, null))
        assertTrue(ProfileSessionGate.canOpenLink("other", "other"))
    }
}
