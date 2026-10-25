package com.example.chat

import com.example.chat.core.profile.ProfileDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

/** Behavior test of the profile_screen.dart display-name fallback chain. */
class ProfileDisplayNameParityTest {

    @Test
    fun overrideWinsWhenNotPlaceholder() {
        assertEquals(
            "Custom Name",
            ProfileDisplayName.effectiveDisplayName("Custom Name", "Real Name", "user1"),
        )
    }

    @Test
    fun placeholderOverridesAreIgnored() {
        // 'Alex Rivera' and 'Bandi Maya' overrides fall through to rawName.
        assertEquals(
            "Real Name",
            ProfileDisplayName.effectiveDisplayName("Alex Rivera", "Real Name", "user1"),
        )
        assertEquals(
            "Real Name",
            ProfileDisplayName.effectiveDisplayName("Bandi Maya", "Real Name", "user1"),
        )
    }

    @Test
    fun blankOverrideFallsThrough() {
        assertEquals(
            "Real Name",
            ProfileDisplayName.effectiveDisplayName("   ", "Real Name", "user1"),
        )
    }

    @Test
    fun placeholderRawNamesFallToUsername() {
        assertEquals(
            "user1",
            ProfileDisplayName.effectiveDisplayName("", "Bandi Maya", "user1"),
        )
        assertEquals(
            "user1",
            ProfileDisplayName.effectiveDisplayName("", "Chaty User", "user1"),
        )
        assertEquals(
            "user1",
            ProfileDisplayName.effectiveDisplayName("", "  ", "user1"),
        )
    }

    @Test
    fun emptyUsernameFallsToUserLiteral() {
        assertEquals(
            "User",
            ProfileDisplayName.effectiveDisplayName("", "", ""),
        )
    }

    @Test
    fun overrideIsTrimmed() {
        assertEquals(
            "Padded",
            ProfileDisplayName.effectiveDisplayName("  Padded  ", "Real Name", "user1"),
        )
    }
}
