package com.example.chat

import com.example.chat.domain.models.HomePreferences
import org.junit.Assert.*
import org.junit.Test

class HomeMenuPreferencesTest {
    @Test fun oldPreferencesKeepUsefulDefaultsAndNumberOff() {
        val p = HomePreferences.fromMap(emptyMap())
        assertTrue(p.menuShowNewActions)
        assertTrue(p.menuShowSavedChats)
        assertTrue(p.menuShowMarkRead)
        assertTrue(p.menuShowShare)
        assertFalse(p.menuShowMessageNumber)
    }
    @Test fun switchesRoundTripWithoutChangingSecurityOrFab() {
        val p = HomePreferences().copy(menuShowNewActions = false,
            menuShowSavedChats = false, menuShowMarkRead = false,
            menuShowShare = false, menuShowMessageNumber = true)
        val restored = HomePreferences.fromMap(p.toMap())
        assertEquals(p, restored)
        assertEquals(HomePreferences().hideFab, restored.hideFab)
    }
}
