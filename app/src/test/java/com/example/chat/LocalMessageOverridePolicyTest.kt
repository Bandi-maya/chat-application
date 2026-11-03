package com.example.chat

import com.example.chat.core.messages.LocalMessageOverridePolicy as Policy
import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocalMessageOverridePolicyTest {
    private val message = ChatMessage("m", "c", "sender", text = "Original", createdAt = Instant.EPOCH)
    @Test fun sameUidOnDifferentServersDoesNotShareLocalOverrides() { assertNotEquals(Policy.accountKey("t", "https://a.invalid", "same"), Policy.accountKey("t", "https://b.invalid", "same")) }
    @Test fun tenantChangeDoesNotShareLocalOverrides() { assertNotEquals(Policy.accountKey("t1", "https://a.invalid", "same"), Policy.accountKey("t2", "https://a.invalid", "same")) }
    @Test fun missingRoutingDoesNotAllowLocalStoreScope() { assertNull(Policy.accountKey("", "https://a.invalid", "u")) }
    @Test fun newSessionWithEqualValuesIsStillDifferent() { val one = String(charArrayOf('a')); val two = String(charArrayOf('a')); assertFalse(Policy.sessionCurrent(one, two)); assertTrue(Policy.sessionCurrent(one, one)) }
    @Test fun differentAccountsCannotShareOverride() { assertNotEquals(Policy.key("one", "c", "m"), Policy.key("two", "c", "m")) }
    @Test fun differentChatsCannotShareOverride() { assertNotEquals(Policy.key("one", "c", "m"), Policy.key("one", "d", "m")) }
    @Test fun delimitedKeysDoNotCollide() { assertNotEquals(Policy.key("ab", "c", "d"), Policy.key("a", "bc", "d")) }
    @Test fun unauthenticatedScopeCannotPersistOverride() { assertNull(Policy.key("", "c", "m")) }
    @Test fun otherPersonsTextCanBeChangedLocallyWithoutCanonicalMutation() {
        assertEquals("Local", Policy.displayText(message, "Local")); assertEquals("Original", message.text); assertNull(message.editedAt)
    }
    @Test fun revokedTextIgnoresStoredOverride() { assertEquals("Original", Policy.displayText(message.copy(isRevokedBySender = true), "Local")) }
    @Test fun viewOnceTextIgnoresStoredOverride() { assertFalse(Policy.editable(message.copy(metadata = mapOf("view_once" to true)))) }
    @Test fun structuredPollIsNotPlainEditableText() { assertFalse(Policy.editable(message.copy(text = "[POLL] structured"))) }
    @Test fun serverTextChangeChangesFingerprint() { assertNotEquals(Policy.fingerprint(message), Policy.fingerprint(message.copy(text = "Updated"))) }
    @Test fun deletingLocalOverrideRestoresOriginal() { assertEquals("Original", Policy.displayText(message, null)) }
}
