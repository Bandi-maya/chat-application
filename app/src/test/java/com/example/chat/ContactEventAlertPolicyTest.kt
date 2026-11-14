package com.example.chat

import com.example.chat.data.realtime.ContactEventAlertPolicy
import com.example.chat.data.realtime.ContactEventKind
import com.example.chat.domain.models.ContactEventAlertOverride
import com.example.chat.domain.models.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEventAlertPolicyTest {

    private fun popup(
        global: Boolean = true,
        contactOverride: ContactEventAlertOverride? = null,
        kind: ContactEventKind = ContactEventKind.ONLINE,
        protectedChat: Boolean = false,
        muted: Boolean = false,
        open: Boolean = false,
    ): Boolean = ContactEventAlertPolicy.shouldPopup(
        globalEventEnabled = global,
        contactOverride = contactOverride,
        kind = kind,
        conversationProtected = protectedChat,
        conversationMuted = muted,
        conversationOpen = open,
    )

    @Test
    fun defaultFollowsGlobalToggle() {
        assertTrue(popup(global = true))
        assertFalse(popup(global = false))
        assertTrue(popup(global = true, contactOverride = ContactEventAlertOverride()))
    }

    @Test
    fun perKindModesResolveIndependently() {
        val contactOverride = ContactEventAlertOverride(
            online = ContactEventAlertOverride.MODE_ALWAYS,
            typing = ContactEventAlertOverride.MODE_NEVER,
            recording = ContactEventAlertOverride.MODE_DEFAULT,
        )
        assertTrue(popup(global = false, contactOverride = contactOverride, kind = ContactEventKind.ONLINE))
        assertFalse(popup(global = true, contactOverride = contactOverride, kind = ContactEventKind.TYPING))
        assertTrue(popup(global = true, contactOverride = contactOverride, kind = ContactEventKind.RECORDING))
        assertFalse(popup(global = false, contactOverride = contactOverride, kind = ContactEventKind.RECORDING))
    }

    @Test
    fun alwaysNeverBeatsGlobalButNotGuards() {
        val always = ContactEventAlertOverride(online = ContactEventAlertOverride.MODE_ALWAYS)
        assertTrue(popup(global = false, contactOverride = always))
        assertFalse(popup(global = false, contactOverride = always, protectedChat = true))
        assertFalse(popup(global = false, contactOverride = always, muted = true))
        assertFalse(popup(global = false, contactOverride = always, open = true))
    }

    @Test
    fun guardsSilenceEveryMode() {
        val always = ContactEventAlertOverride(
            online = ContactEventAlertOverride.MODE_ALWAYS,
            typing = ContactEventAlertOverride.MODE_ALWAYS,
            recording = ContactEventAlertOverride.MODE_ALWAYS,
        )
        for (kind in ContactEventKind.entries) {
            assertFalse(popup(global = true, contactOverride = always, kind = kind, protectedChat = true))
            assertFalse(popup(global = true, contactOverride = always, kind = kind, muted = true))
            assertFalse(popup(global = true, contactOverride = always, kind = kind, open = true))
        }
    }

    @Test
    fun overrideRoundTripsThroughPreferenceMap() {
        val original = NotificationPreferences(
            contactEventOverrides = mapOf(
                "user-1" to ContactEventAlertOverride(
                    online = ContactEventAlertOverride.MODE_ALWAYS,
                    recording = ContactEventAlertOverride.MODE_NEVER,
                ),
            ),
        )
        val restored = NotificationPreferences.fromMap(original.toMap())
        val contactOverride = restored.contactEventOverrides["user-1"]
        assertEquals(ContactEventAlertOverride.MODE_ALWAYS, contactOverride?.online)
        assertEquals(ContactEventAlertOverride.MODE_DEFAULT, contactOverride?.typing)
        assertEquals(ContactEventAlertOverride.MODE_NEVER, contactOverride?.recording)
    }

    @Test
    fun unknownPersistedModesParseToDefault() {
        val parsed = ContactEventAlertOverride.fromMap(
            mapOf("online" to "sometimes", "typing" to 42, "recording" to "never"),
        )
        assertEquals(ContactEventAlertOverride.MODE_DEFAULT, parsed.online)
        assertEquals(ContactEventAlertOverride.MODE_DEFAULT, parsed.typing)
        assertEquals(ContactEventAlertOverride.MODE_NEVER, parsed.recording)
    }

    @Test
    fun fullyDefaultEntriesAreNotPersisted() {
        val prefs = NotificationPreferences.fromMap(
            mapOf(
                "contactEventOverrides" to mapOf(
                    "user-1" to mapOf("online" to "default", "typing" to "default", "recording" to "default"),
                    "user-2" to "not-a-map",
                    "user-3" to mapOf("typing" to "always"),
                ),
            ),
        )
        assertNull(prefs.contactEventOverrides["user-1"])
        assertNull(prefs.contactEventOverrides["user-2"])
        assertEquals(ContactEventAlertOverride.MODE_ALWAYS, prefs.contactEventOverrides["user-3"]?.typing)
    }

    @Test
    fun missingOverridesKeyParsesToEmptyMap() {
        val prefs = NotificationPreferences.fromMap(emptyMap())
        assertTrue(prefs.contactEventOverrides.isEmpty())
        assertTrue(NotificationPreferences().contactEventOverrides.isEmpty())
    }

    @Test
    fun isFullyDefaultTracksEveryMode() {
        assertTrue(ContactEventAlertOverride().isFullyDefault)
        assertFalse(ContactEventAlertOverride(typing = ContactEventAlertOverride.MODE_NEVER).isFullyDefault)
    }
}
