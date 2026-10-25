package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.contact_info.ContactInfoLogic
import com.example.chat.ui.chats.linked_devices.QrPayloads
import com.example.chat.ui.chats.locked_chats.SecretPhraseSupport
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior parity tests for the new-chat / contact-info / locked-chats /
 *  linked-devices pure rules (Dart sources at api branch anchor 3bee780). */
class NewChatContactParityTest {

    private fun user(id: String, name: String, username: String, email: String = "") = UserProfile(
        id = id,
        displayName = name,
        username = username,
        avatarInitials = name.take(1),
        avatarColorHex = "0xFF6366F1",
        about = "",
        lastSeenAt = Instant.now(),
        email = email,
    )

    private fun msg(text: String, attachmentType: String? = null) = ChatMessage(
        id = "m$text",
        conversationId = "c",
        senderId = "s",
        type = MessageType.text,
        text = text,
        attachment = attachmentType?.let { MessageAttachment(id = "a$text", type = it, name = "f$text", size = "1 KB") },
        createdAt = Instant.now(),
    )

    // ── NewChatRules (new_chat_screen.dart + ApiBackendService.searchUsers) ──

    @Test
    fun broadcastWithoutTitleGetsCountedTitle() {
        assertEquals("Broadcast (3)", NewChatRules.effectiveGroupTitle("", true, 3))
    }

    @Test
    fun broadcastTitleGetsPrefixUnlessAlreadyBroadcast() {
        assertEquals("Broadcast: Team", NewChatRules.effectiveGroupTitle("Team", true, 2))
        assertEquals("broadcast news", NewChatRules.effectiveGroupTitle("broadcast news", true, 2))
        assertEquals("Broadcasting", NewChatRules.effectiveGroupTitle("Broadcasting", true, 2))
    }

    @Test
    fun groupTitlePassesThroughTrimmed() {
        assertEquals("My Group", NewChatRules.effectiveGroupTitle("  My Group  ", false, 2))
        assertEquals("", NewChatRules.effectiveGroupTitle("   ", false, 2))
    }

    @Test
    fun canCreateGroupMirrorsDartGuard() {
        assertFalse(NewChatRules.canCreateGroup("", false, 2, false))
        assertFalse(NewChatRules.canCreateGroup("G", false, 0, false))
        assertFalse(NewChatRules.canCreateGroup("G", false, 2, true))
        assertTrue(NewChatRules.canCreateGroup("G", false, 2, false))
        // Broadcast: empty title is fine when members exist (title auto-fills).
        assertTrue(NewChatRules.canCreateGroup("", true, 2, false))
    }

    @Test
    fun filterUsersMatchesDartSearchUsers() {
        val users = listOf(
            user("1", "Maya Rao", "maya", "maya@x.com"),
            user("2", "Alex Doe", "alexd", "alex@y.com"),
            user("me", "Self User", "self"),
        )
        assertEquals(listOf("1", "2"), NewChatRules.filterUsers(users, "", "me").map { it.id })
        assertEquals(listOf("1", "2", "me"), NewChatRules.filterUsers(users, "", "me", includeSelf = true).map { it.id })
        assertEquals(listOf("1"), NewChatRules.filterUsers(users, "MAYA", null).map { it.id })
        assertEquals(listOf("2"), NewChatRules.filterUsers(users, "alex@y.com", null).map { it.id })
        assertEquals(emptyList<String>(), NewChatRules.filterUsers(users, "zzz", null).map { it.id })
    }

    @Test
    fun toggleMemberAddsThenRemoves() {
        val once = NewChatRules.toggleMember(emptySet(), "u1")
        assertEquals(setOf("u1"), once)
        assertEquals(emptySet<String>(), NewChatRules.toggleMember(once, "u1"))
    }

    // ── ContactInfoLogic (contact_info_screen.dart) ──

    @Test
    fun presenceLabelPriorityAndFormats() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-25T12:00:00Z")
        assertEquals("recording voice message…", ContactInfoLogic.presenceLabel(true, true, true, null, now, zone))
        assertEquals("typing…", ContactInfoLogic.presenceLabel(false, true, true, null, now, zone))
        assertEquals("Online", ContactInfoLogic.presenceLabel(false, false, true, null, now, zone))
        assertEquals("Last seen hidden", ContactInfoLogic.presenceLabel(false, false, false, null, now, zone))
        assertEquals(
            "Last seen today at 09:05",
            ContactInfoLogic.presenceLabel(false, false, false, Instant.parse("2026-10-25T09:05:00Z"), now, zone),
        )
        assertEquals(
            "Last seen 24/10/2026 at 23:59",
            ContactInfoLogic.presenceLabel(false, false, false, Instant.parse("2026-10-24T23:59:00Z"), now, zone),
        )
    }

    @Test
    fun mediaDocumentsLinksSplit() {
        val messages = listOf(
            msg("see https://a.com/x and http://b.com"),
            msg("dup https://a.com/x"),
            msg("", "image"),
            msg("", "video"),
            msg("", "document"),
            msg("", "audio"),
        )
        assertEquals(2, ContactInfoLogic.sharedMedia(messages).size)
        assertEquals(1, ContactInfoLogic.sharedDocuments(messages).size)
        assertEquals(listOf("https://a.com/x", "http://b.com"), ContactInfoLogic.extractLinks(messages))
    }

    // ── SecretPhraseSupport (local_lock_service.dart) ──

    @Test
    fun normalizeSecretPhraseTrimsCollapsesLowercases() {
        assertEquals("my vault", SecretPhraseSupport.normalizeSecretPhrase("  My   Vault "))
        assertEquals("🔒 secret", SecretPhraseSupport.normalizeSecretPhrase("🔒  Secret"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySecretPhraseThrows() {
        SecretPhraseSupport.requireValidSecretPhrase("   ")
    }


    // ── Avatar color parsing (app_avatar.dart + chaty_kit.dart variants) ──

    @Test
    fun appAvatarParsePadsSixDigitAndFallsBack() {
        assertEquals(0xFF6366F1, com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit(null))
        assertEquals(0xFF112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("112233", 0xFF000001))
        assertEquals(0xFF112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("0xFF112233", 0))
        assertEquals(0xFF112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("#112233", 0))
        assertEquals(0x80112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("0x80112233", 0))
        // "bad" parses radix-16 exactly as Dart: length != 6, so no FF pad -
        // Dart produces Color(0xBAD) (alpha 0). Quirk kept for parity.
        assertEquals(0xBAD, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("bad", 0xFFABCDEF))
        assertEquals(0xFFABCDEF, com.example.chat.ui.chats.contact_info.parseAvatarColorArgb("zzz", 0xFFABCDEF))
    }

    @Test
    fun kitParseOrsAlphaOnShortHex() {
        assertEquals(0xFF6366F1, com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit(""))
        assertEquals(0xFF112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit("0x112233"))
        assertEquals(0x80112233, com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit("0x80112233"))
        assertEquals(0xFF6366F1, com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit("nope"))
    }

    // ── QrPayloads (linked_devices_qr_screen.dart) ──

    @Test
    fun myQrPayloadShape() {
        assertEquals("chaty://contact/maya", QrPayloads.myQrPayload("maya"))
    }

    @Test
    fun parseScannedValidatesUri() {
        assertTrue(QrPayloads.parseScanned(null) is QrPayloads.ScanResult.NotChatyQr)
        assertTrue(QrPayloads.parseScanned("https://x.com") is QrPayloads.ScanResult.NotChatyQr)
        assertTrue(QrPayloads.parseScanned("chaty://other/maya") is QrPayloads.ScanResult.NotChatyQr)
        assertTrue(QrPayloads.parseScanned("chaty://contact/") is QrPayloads.ScanResult.NotChatyQr)
        assertEquals(QrPayloads.ScanResult.Contact("maya"), QrPayloads.parseScanned("chaty://contact/maya"))
        assertEquals(QrPayloads.ScanResult.Contact("maya"), QrPayloads.parseScanned("chaty://contact/@maya"))
        assertEquals(QrPayloads.ScanResult.Contact("ma ya"), QrPayloads.parseScanned("chaty://contact/ma%20ya"))
        assertTrue(QrPayloads.isSelfUsername("Maya", "maya"))
        assertFalse(QrPayloads.isSelfUsername("maya", "alex"))
    }

    @Test
    fun lastActiveLabels() {
        val now = Instant.parse("2026-10-25T12:00:00Z")
        val zone = ZoneId.of("UTC")
        assertEquals("Active now", QrPayloads.lastActiveLabel(now.minusSeconds(30), now, zone))
        assertEquals("Active 5m ago", QrPayloads.lastActiveLabel(now.minusSeconds(300), now, zone))
        assertEquals("Active 3h ago", QrPayloads.lastActiveLabel(now.minusSeconds(3 * 3600), now, zone))
        assertEquals("Active 24/10/2026", QrPayloads.lastActiveLabel(Instant.parse("2026-10-24T10:00:00Z"), now, zone))
    }
}
