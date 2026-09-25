package com.arflix.tv.navigation

import com.arflix.tv.data.model.Profile

/** A persisted active profile is not evidence of PIN verification in this session. */
internal object ProfileSessionGate {
    fun initialProfileId(skipSelection: Boolean, profile: Profile?): String? =
        profile?.takeIf { skipSelection && !it.isLocked }?.id

    fun canOpenLink(activeProfileId: String?, selectedProfileId: String?): Boolean =
        activeProfileId != null && activeProfileId == selectedProfileId
}
