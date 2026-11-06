package com.example.chat

import com.example.chat.domain.models.PrivacyPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins Bandi's decision (Nov 4, 2026): anti-view-once retention is OFF by
 *  default. Saved user choices are untouched: fromMap only falls back to the
 *  default when the key is absent. */
class AntiViewOnceDefaultTest {

    @Test fun defaultIsOff() {
        assertFalse(PrivacyPreferences().antiViewOnce)
    }

    @Test fun missingKeyFallsBackToOff() {
        assertFalse(PrivacyPreferences.fromMap(emptyMap<String, Any?>()).antiViewOnce)
    }

    @Test fun savedChoiceIsPreserved() {
        assertTrue(PrivacyPreferences.fromMap(mapOf("antiViewOnce" to true)).antiViewOnce)
        assertFalse(PrivacyPreferences.fromMap(mapOf("antiViewOnce" to false)).antiViewOnce)
    }

    @Test fun roundTripPreservesChoice() {
        assertTrue(PrivacyPreferences.fromMap(PrivacyPreferences(antiViewOnce = true).toMap()).antiViewOnce)
        assertFalse(PrivacyPreferences.fromMap(PrivacyPreferences(antiViewOnce = false).toMap()).antiViewOnce)
    }
}
