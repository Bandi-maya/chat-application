package com.example.chat

import com.example.chat.domain.models.HomePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameTapLockPreferenceTest {
    @Test fun defaultsOffForUnsetStoredPrefs() {
        assertFalse(HomePreferences().nameTapOpensLocked)
        assertFalse(HomePreferences.fromMap(emptyMap()).nameTapOpensLocked)
    }

    @Test fun explicitOnSurvivesRoundTrip() {
        val on = HomePreferences().copy(nameTapOpensLocked = true)
        assertTrue(HomePreferences.fromMap(on.toMap()).nameTapOpensLocked)
        assertEquals(false, HomePreferences().toMap()["nameTapOpensLocked"])
    }
}
