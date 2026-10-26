package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.ForwardSend
import com.example.chat.ui.chats.detail.ForwardTargets
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dart _openForwardSheet (chat_detail_screen.dart:2337-2519) parity: target
 *  limit, section construction and ordering, Home hide switches, groups always
 *  visible, and the forwarded send payload normalization. */
class ForwardTargetsTest {

    private fun conv(
        id: String,
        type: ConversationType = ConversationType.direct,
        lastMessageTime: Instant = Instant.ofEpochSecond(1000),
    ) = Conversation(
        id = id,
        type = type,
        title = "Chat $id",
        participantIds = listOf("me", id),
        lastMessageText = "",
        lastMessageTime = lastMessageTime,
        lastMessageSenderId = id,
    )

    private fun buildAll(
        conversations: List<Conversation>,
        counts: Map<String, Int> = emptyMap(),
        increaseForwardLimit: Boolean = false,
        hideFrequentlyContacted: Boolean = false,
        hideRecentChats: Boolean = false,
        hideOtherContacts: Boolean = false,
    ) = ForwardTargets.build(
        conversations = conversations,
        currentConversationId = "current",
        messageCount = { counts[it] ?: 0 },
        increaseForwardLimit = increaseForwardLimit,
        hideFrequentlyContacted = hideFrequentlyContacted,
        hideRecentChats = hideRecentChats,
        hideOtherContacts = hideOtherContacts,
    )

    @Test
    fun limitIsFiveByDefaultAnd250WhenIncreased() {
        assertEquals(5, ForwardTargets.maxChats(false))
        assertEquals(250, ForwardTargets.maxChats(true))
    }

    @Test
    fun currentConversationIsExcluded() {
        val items = buildAll(listOf(conv("current"), conv("a")))
        assertEquals(listOf("a"), items.map { it.conversation.id })
    }

    @Test
    fun baseLimitTakesFirstFiveInStoreOrderBeforeSectioning() {
        // Store order (not recency) decides the take(): c6 is most recent but 6th in store order.
        val list = (1..6).map { conv("c$it", lastMessageTime = Instant.ofEpochSecond(1000L - it)) }
        val items = buildAll(list)
        assertFalse(items.any { it.conversation.id == "c6" })
    }

    @Test
    fun extendedLimitKeepsBeyondFive() {
        val list = (1..6).map { conv("c$it", lastMessageTime = Instant.ofEpochSecond(1000L - it)) }
        val items = buildAll(list, increaseForwardLimit = true)
        assertTrue(items.any { it.conversation.id == "c6" })
    }

    @Test
    fun sectionsOrderAndFrequentByMessageCount() {
        // d1..d6 direct; d6 has most messages but oldest timestamp.
        val direct = (1..6).map {
            conv("d$it", lastMessageTime = Instant.ofEpochSecond(2000L - it))
        }
        val counts = mapOf("d6" to 100, "d1" to 50, "d2" to 40, "d3" to 30, "d4" to 20, "d5" to 10)
        val items = buildAll(direct, counts, increaseForwardLimit = true)
        val frequent = items.filter { it.section == ForwardTargets.SECTION_FREQUENT }.map { it.conversation.id }
        assertEquals(listOf("d6", "d1", "d2", "d3", "d4"), frequent)
        // d5 is not frequent; it is the most recent remaining -> Recent chats.
        val recent = items.filter { it.section == ForwardTargets.SECTION_RECENT }.map { it.conversation.id }
        assertEquals(listOf("d5"), recent)
    }

    @Test
    fun otherContactsAreNonFrequentNonRecentDirectsInRecencyOrder() {
        val direct = (1..8).map { conv("d$it", lastMessageTime = Instant.ofEpochSecond(3000L - it)) }
        val counts = mapOf("d8" to 90, "d7" to 80, "d6" to 70, "d5" to 60, "d4" to 50)
        val items = buildAll(direct, counts, increaseForwardLimit = true)
        // frequent: d8,d7,d6,d5,d4 (by count); recent pool: d1,d2,d3 (recency) -> recent
        // other contacts: none left.
        val others = items.filter { it.section == ForwardTargets.SECTION_OTHER_CONTACTS }
        assertTrue(others.isEmpty())
        val recent = items.filter { it.section == ForwardTargets.SECTION_RECENT }.map { it.conversation.id }
        assertEquals(listOf("d1", "d2", "d3"), recent)
    }

    @Test
    fun otherContactsFillBeyondRecentFive() {
        val direct = (1..12).map { conv("d$it", lastMessageTime = Instant.ofEpochSecond(4000L - it)) }
        val items = buildAll(direct, emptyMap(), increaseForwardLimit = true)
        // no messages anywhere -> all counts 0 -> frequent takes first 5 in sorted-stable order;
        // recent takes next 5 by recency; remaining land in Other contacts.
        val sections = items.groupBy({ it.section }, { it.conversation.id })
        assertEquals(5, sections[ForwardTargets.SECTION_FREQUENT]!!.size)
        assertEquals(5, sections[ForwardTargets.SECTION_RECENT]!!.size)
        assertEquals(2, sections[ForwardTargets.SECTION_OTHER_CONTACTS]!!.size)
    }

    @Test
    fun groupsStayVisibleWhenContactSectionsHidden() {
        // 12 directs fill the frequent+recent pools; the old group is outside the recent
        // top-5, so it survives in Groups even with every contact section hidden.
        val direct = (1..12).map { conv("d$it", lastMessageTime = Instant.ofEpochSecond(4000L - it)) }
        val list = direct + conv("g1", ConversationType.group, Instant.ofEpochSecond(10))
        val items = buildAll(
            list,
            increaseForwardLimit = true,
            hideFrequentlyContacted = true,
            hideRecentChats = true,
            hideOtherContacts = true,
        )
        assertEquals(listOf(ForwardTargets.SECTION_GROUPS), items.map { it.section }.distinct())
        assertEquals(listOf("g1"), items.map { it.conversation.id })
    }

    @Test
    fun groupsInsideRecentFiveFollowTheRecentSectionHide() {
        // Dart quirk, ported exactly: a group inside the recent top-5 belongs to the
        // "Recent chats" pool, so hiding that section hides the group too.
        val list = listOf(
            conv("d1"),
            conv("g1", ConversationType.group, Instant.ofEpochSecond(999)),
        )
        val items = buildAll(
            list,
            hideFrequentlyContacted = true,
            hideRecentChats = true,
            hideOtherContacts = true,
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun hiddenFrequentSectionDropsThoseChatsEntirely() {
        // Dart quirk, ported exactly: the frequent/recent pools are computed BEFORE the
        // hide switches apply, so hiding "Frequently contacted" removes those chats from
        // the sheet instead of letting them resurface under "Recent chats".
        val list = listOf(conv("d1", lastMessageTime = Instant.ofEpochSecond(10)))
        val items = buildAll(list, hideFrequentlyContacted = true)
        assertTrue(items.isEmpty())
    }

    @Test
    fun emptyTargetsYieldEmptyList() {
        assertTrue(buildAll(listOf(conv("current"))).isEmpty())
    }

    @Test
    fun systemMessageForwardsAsEmptyText() {
        val message = ChatMessage(
            id = "m1",
            conversationId = "c1",
            senderId = "u1",
            type = MessageType.system,
            text = "Alice joined",
            createdAt = Instant.ofEpochSecond(5),
        )
        val payload = ForwardSend.payload(message, labelAllowed = true)
        assertEquals("", payload.text)
        assertEquals(MessageType.text, payload.type)
        assertNull(payload.attachment)
        assertEquals(mapOf("forwarded" to true), payload.extraMetadata)
    }

    @Test
    fun mediaForwardsWithAttachmentByReferenceAndForwardedLabel() {
        val attachment = MessageAttachment(id = "a1", type = "audio", name = "x.m4a", size = "123", url = "https://files/x.m4a")
        val message = ChatMessage(
            id = "m2",
            conversationId = "c1",
            senderId = "u1",
            type = MessageType.audio,
            text = "",
            attachment = attachment,
            createdAt = Instant.ofEpochSecond(6),
        )
        val payload = ForwardSend.payload(message, labelAllowed = true)
        assertEquals(MessageType.audio, payload.type)
        assertTrue(payload.attachment === attachment)
        assertEquals(mapOf("forwarded" to true), payload.extraMetadata)
    }

    @Test
    fun labelAllowedHonorsBothSwitches() {
        assertTrue(ForwardSend.labelAllowed(disableForwardedLabel = false, yoDisableFwd = false))
        assertFalse(ForwardSend.labelAllowed(disableForwardedLabel = true, yoDisableFwd = false))
        assertFalse(ForwardSend.labelAllowed(disableForwardedLabel = false, yoDisableFwd = true))
        assertFalse(ForwardSend.labelAllowed(disableForwardedLabel = true, yoDisableFwd = true))
    }
}
