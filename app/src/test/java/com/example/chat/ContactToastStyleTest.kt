package com.example.chat

import com.example.chat.domain.models.NotificationToastPreferences
import com.example.chat.domain.notifications.ContactToastStyle
import org.junit.Assert.*
import org.junit.Test

class ContactToastStyleTest {
    @Test fun typingUsesItsOwnSettingsNotOnlineOrProfile() {
        val prefs = NotificationToastPreferences(onlineToastRadius = 2.0, profileToastRadius = 50.0,
            typingToastRadius = 19.0, typingToastPosition = "Bottom of the screen", typingToastBgColor = 12L,
            typingToastTextColor = 24L, typingToastWithProfilePicture = false, typingToastElevation = false)
        val style = ContactToastStyle.resolve("typing", prefs)!!
        assertEquals(19.0, style.radius, 0.0); assertEquals("bottom", style.position)
        assertEquals(12L, style.background); assertEquals(24L, style.text)
        assertFalse(style.showAvatar); assertFalse(style.elevated)
    }
    @Test fun onlineUsesItsOwnSettings() {
        val style = ContactToastStyle.resolve("online", NotificationToastPreferences(onlineToastRadius = 14.0,
            onlineToastPosition = "Center of the screen", onlineToastBgColor = 3L))!!
        assertEquals(14.0, style.radius, 0.0); assertEquals("center", style.position); assertEquals(3L, style.background)
    }
    @Test fun neverSubstitutesProfileUpdatesForViewsOrInventsStoryEvents() {
        for (kind in listOf("profile", "profileUpdate", "profileView", "storyView", "recording", "other")) {
            assertNull(kind, ContactToastStyle.resolve(kind, NotificationToastPreferences()))
        }
    }
    @Test fun styleDoesNotOverrideEventPermissionOrForceAvatar() {
        // A per-contact Always can allow an event despite its global switch. This resolver only styles it.
        val style = ContactToastStyle.resolve("typing", NotificationToastPreferences(typingToastEnabled = false))!!
        assertTrue(style.showAvatar)
    }
    @Test fun invalidStoredRadiusFallsBackWithoutInvalidComposeDimensions() {
        assertEquals(8.0, ContactToastStyle.resolve("typing", NotificationToastPreferences(typingToastRadius = Double.NaN))!!.radius, 0.0)
    }
    @Test fun radiusAndUnknownPositionAreBounded() {
        val low = ContactToastStyle.resolve("online", NotificationToastPreferences(onlineToastRadius = -4.0, onlineToastPosition = "unknown"))!!
        val high = ContactToastStyle.resolve("typing", NotificationToastPreferences(typingToastRadius = 500.0))!!
        assertEquals(0.0, low.radius, 0.0); assertEquals(64.0, high.radius, 0.0); assertEquals("top", low.position)
    }
}
