package com.example.chat

import com.example.chat.ui.settings.privacy.PrivacyOptionsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Server enum <-> label mappings for the privacy center (research 5.2/5.3). */
class PrivacyOptionsLogicTest {

    @Test
    fun `audience enums map to WhatsApp-shaped labels`() {
        assertEquals("Everyone", PrivacyOptionsLogic.audienceLabel("everyone"))
        assertEquals("My Contacts", PrivacyOptionsLogic.audienceLabel("contacts"))
        assertEquals("My Contacts Except…", PrivacyOptionsLogic.audienceLabel("contacts_except"))
        assertEquals("Nobody", PrivacyOptionsLogic.audienceLabel("nobody"))
    }

    @Test
    fun `unknown audience enum fails closed`() {
        assertNull(PrivacyOptionsLogic.audienceLabel("friends"))
        assertNull(PrivacyOptionsLogic.audienceLabel(null))
        assertNull(PrivacyOptionsLogic.audiencePolicy("Friends"))
    }

    @Test
    fun `labels round-trip to enums`() {
        for (label in PrivacyOptionsLogic.audienceLabels) {
            assertEquals(label, PrivacyOptionsLogic.audienceLabel(PrivacyOptionsLogic.audiencePolicy(label)))
        }
    }

    @Test
    fun `online policy has WhatsApp's two options`() {
        assertEquals(listOf("Everyone", "Same as last seen"), PrivacyOptionsLogic.onlineLabels)
        assertEquals("same_as_last_seen", PrivacyOptionsLogic.onlinePolicy("Same as last seen"))
        assertEquals("Everyone", PrivacyOptionsLogic.onlineLabel("everyone"))
        assertNull(PrivacyOptionsLogic.onlineLabel("contacts"))
    }
}
