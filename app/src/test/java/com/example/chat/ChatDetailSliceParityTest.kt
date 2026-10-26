package com.example.chat

import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.BubbleStyleRegistry
import com.example.chat.ui.chats.detail.ChatCommandParser
import com.example.chat.ui.chats.detail.EmojiRecentCache
import com.example.chat.ui.chats.detail.EmojiRecentStore
import com.example.chat.ui.chats.detail.ChatCommandType
import com.example.chat.ui.chats.detail.MessageActionRegistry
import com.example.chat.ui.chats.detail.MessageActionType
import com.example.chat.ui.chats.detail.classifyEmojiOnly
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests for the ChatDetail slice's pure-Kotlin ports, written from
 *  the Dart source's observable behavior (emoji_only.dart,
 *  message_action_registry.dart, bubble_style_registry.dart). */
class ChatDetailSliceParityTest {

    private fun msg(
        type: MessageType = MessageType.text,
        text: String = "hi",
        isStarred: Boolean = false,
        isPinned: Boolean = false,
        deletedForEveryone: Boolean = false,
    ) = ChatMessage(
        id = "m1",
        conversationId = "c1",
        senderId = "u1",
        type = type,
        text = text,
        createdAt = Instant.now(),
        deliveryState = DeliveryState.delivered,
        isStarred = isStarred,
        isPinned = isPinned,
        isDeletedForEveryone = deletedForEveryone,
    )

    // --- emoji_only.dart ---

    @Test fun singleEmojiIsEmojiOnlyWithOneGlyph() {
        val info = classifyEmojiOnly("😂")
        assertTrue(info.isEmojiOnly)
        assertEquals(1, info.graphemeCount)
    }

    @Test fun zwjFamilyCountsAsOneGlyph() {
        val info = classifyEmojiOnly("👨‍👩‍👧‍👦")
        assertTrue(info.isEmojiOnly)
        assertEquals(1, info.graphemeCount)
    }

    @Test fun twoEmojisCountAsTwoGlyphs() {
        val info = classifyEmojiOnly("😂😂")
        assertTrue(info.isEmojiOnly)
        assertEquals(2, info.graphemeCount)
    }

    @Test fun regionalIndicatorFlagCountsAsOneGlyph() {
        val info = classifyEmojiOnly("🇮🇳")
        assertTrue(info.isEmojiOnly)
        assertEquals(1, info.graphemeCount)
    }

    @Test fun whitespaceInsideEmojiOnlyMessageIsAllowed() {
        val info = classifyEmojiOnly("😂😂 ❤️")
        assertTrue(info.isEmojiOnly)
        assertEquals(3, info.graphemeCount)
    }

    @Test fun emojiPlusLettersIsNotEmojiOnly() {
        assertFalse(classifyEmojiOnly("😂 lol").isEmojiOnly)
    }

    @Test fun plainTextIsNotEmojiOnly() {
        assertFalse(classifyEmojiOnly("hello").isEmojiOnly)
    }

    @Test fun emptyAndBlankAreNotEmojiOnly() {
        assertFalse(classifyEmojiOnly("").isEmojiOnly)
        assertFalse(classifyEmojiOnly("   ").isEmojiOnly)
    }

    @Test fun keycapSequenceCountsAsOneGlyph() {
        val info = classifyEmojiOnly("1️⃣")
        assertTrue(info.isEmojiOnly)
        assertEquals(1, info.graphemeCount)
    }

    @Test fun emojiOnlyFontSizeLadder() {
        assertEquals(15.0 * 2.9, classifyEmojiOnly("😂").fontSize(15.0), 0.0001)
        assertEquals(15.0 * 2.4, classifyEmojiOnly("😂😂").fontSize(15.0), 0.0001)
        assertEquals(15.0 * 2.0, classifyEmojiOnly("😂😂😂").fontSize(15.0), 0.0001)
        assertEquals(15.0 * 1.7, classifyEmojiOnly("😂😂😂😂").fontSize(15.0), 0.0001)
        assertEquals(15.0 * 1.3, classifyEmojiOnly("😂😂😂😂😂").fontSize(15.0), 0.0001)
    }

    // --- message_action_registry.dart ---

    @Test fun ownTextMessageGetsReplyCopyForwardStarEditPinTaskInfoSelectDeletes() {
        val types = MessageActionRegistry.getAvailableActions(msg(), isMe = true).map { it.type }
        assertEquals(
            listOf(
                MessageActionType.reply,
                MessageActionType.copy,
                MessageActionType.forward,
                MessageActionType.star,
                MessageActionType.edit,
                MessageActionType.pin,
                MessageActionType.task,
                MessageActionType.info,
                MessageActionType.select,
                MessageActionType.deleteForMe,
                MessageActionType.deleteForEveryone,
            ),
            types,
        )
    }

    @Test fun otherUsersTextMessageOmitsEditInfoDeleteForEveryone() {
        val types = MessageActionRegistry.getAvailableActions(msg(), isMe = false).map { it.type }
        assertFalse(types.contains(MessageActionType.edit))
        assertFalse(types.contains(MessageActionType.info))
        assertFalse(types.contains(MessageActionType.deleteForEveryone))
        assertTrue(types.contains(MessageActionType.deleteForMe))
    }

    @Test fun systemMessageOmitsReplyForwardStarPinInfoButKeepsSelectAndDeleteForMe() {
        val types = MessageActionRegistry
            .getAvailableActions(msg(type = MessageType.system, text = "x"), isMe = true)
            .map { it.type }
        assertEquals(
            // deleteForEveryone has no type guard in the Dart source.
            listOf(
                MessageActionType.select,
                MessageActionType.deleteForMe,
                MessageActionType.deleteForEveryone,
            ),
            types,
        )
    }

    @Test fun deletedForEveryoneMessageOmitsReplyForwardEditAndDeleteForEveryone() {
        val types = MessageActionRegistry
            .getAvailableActions(msg(deletedForEveryone = true), isMe = true)
            .map { it.type }
        assertFalse(types.contains(MessageActionType.reply))
        assertFalse(types.contains(MessageActionType.forward))
        assertFalse(types.contains(MessageActionType.edit))
        assertFalse(types.contains(MessageActionType.deleteForEveryone))
    }

    @Test fun nonTextMessageOmitsCopyAndTask() {
        val types = MessageActionRegistry
            .getAvailableActions(msg(type = MessageType.image, text = ""), isMe = true)
            .map { it.type }
        assertFalse(types.contains(MessageActionType.copy))
        assertFalse(types.contains(MessageActionType.task))
        assertFalse(types.contains(MessageActionType.edit))
    }

    @Test fun starAndPinLabelsReflectState() {
        val starred = MessageActionRegistry.getAvailableActions(msg(isStarred = true), isMe = false)
        assertEquals("Unstar", starred.first { it.type == MessageActionType.star }.label)
        val pinned = MessageActionRegistry.getAvailableActions(msg(isPinned = true), isMe = false)
        assertEquals("Unpin", pinned.first { it.type == MessageActionType.pin }.label)
    }

    @Test fun deleteForMeIsDestructiveEditIsNotPrimary() {
        val actions = MessageActionRegistry.getAvailableActions(msg(), isMe = true)
        assertTrue(actions.first { it.type == MessageActionType.deleteForMe }.isDestructive)
        assertTrue(actions.first { it.type == MessageActionType.deleteForEveryone }.isDestructive)
        assertFalse(actions.first { it.type == MessageActionType.edit }.isPrimary)
        assertTrue(actions.first { it.type == MessageActionType.reply }.isPrimary)
    }

    // --- bubble_style_registry.dart ---

    @Test fun defaultGeometryPaddingIs12x8AndMarginIs8x2() {
        val g = BubbleStyleRegistry.getGeometry(BubbleStyleId.stock)
        assertEquals(12.0, g.contentPadding.left, 0.0)
        assertEquals(8.0, g.contentPadding.top, 0.0)
        assertEquals(8.0, g.bubbleMargin.left, 0.0)
        assertEquals(2.0, g.bubbleMargin.top, 0.0)
        assertFalse(g.isOutlined)
        assertFalse(g.hasDropShadow)
    }

    @Test fun threeDHasDropShadow() {
        val g = BubbleStyleRegistry.getGeometry(BubbleStyleId.threeD)
        assertTrue(g.hasDropShadow)
        assertEquals(3.5, g.shadowElevation, 0.0)
        assertEquals(14.0, g.contentPadding.left, 0.0)
        assertEquals(10.0, g.contentPadding.top, 0.0)
    }

    @Test fun domeStylesReserveTopPadding() {
        // Crown shoulders are at26dp (filled) or22/24dp (outlined).
        for (id in listOf(BubbleStyleId.rcPal, BubbleStyleId.rcImess)) {
            val padding = BubbleStyleRegistry.getGeometry(id).contentPadding
            assertEquals(30.0, padding.top, 0.001)
            assertEquals(9.0, padding.bottom, 0.001)
        }
        for (id in listOf(BubbleStyleId.rcImLine, BubbleStyleId.rcTranLine)) {
            val padding = BubbleStyleRegistry.getGeometry(id).contentPadding
            assertEquals(28.0, padding.top, 0.001)
            assertEquals(9.0, padding.bottom, 0.001)
        }
    }

    @Test fun flatFrameStylesDoNotReserveDomeHeadroom() {
        // These actual paths are roundrects/chamfers, not centered domes.
        //30dp headroom clipped text in the real44dp proof preview.
        for (id in listOf(
            BubbleStyleId.gabiOutline, BubbleStyleId.rcSamsBord,
            BubbleStyleId.rcLine, BubbleStyleId.aranbor,
        )) {
            val padding = BubbleStyleRegistry.getGeometry(id).contentPadding
            assertEquals(8.0, padding.top, 0.001)
            assertEquals(8.0, padding.bottom, 0.001)
            assertEquals(13.0, padding.left, 0.001)
            assertEquals(13.0, padding.right, 0.001)
        }
    }

    @Test fun outlinedStylesMatchSourceFlags() {
        assertTrue(BubbleStyleRegistry.getGeometry(BubbleStyleId.rcImLine).isOutlined)
        assertTrue(BubbleStyleRegistry.getGeometry(BubbleStyleId.rcTranLine).isOutlined)
        assertTrue(BubbleStyleRegistry.getGeometry(BubbleStyleId.rcIosLine).isOutlined)
        assertTrue(BubbleStyleRegistry.getGeometry(BubbleStyleId.gabiMess).isOutlined)
        assertTrue(BubbleStyleRegistry.getGeometry(BubbleStyleId.transparent).isOutlined)
        assertEquals(1.2, BubbleStyleRegistry.getGeometry(BubbleStyleId.transparent).strokeWidth, 0.0)
        // Outlined styles whose tails overhang reserve wider margins.
        assertEquals(16.0, BubbleStyleRegistry.getGeometry(BubbleStyleId.rcIosLine).bubbleMargin.left, 0.0)
        assertEquals(10.0, BubbleStyleRegistry.getGeometry(BubbleStyleId.gabiMess).bubbleMargin.bottom, 0.0)
        assertFalse(BubbleStyleRegistry.getGeometry(BubbleStyleId.stock).isOutlined)
    }

    // --- ChatCommandParser (lib/ui/core/commands/chat_command_parser.dart) ---

    @Test
    fun nonSlashInputIsUnknownWithEmptyFields() {
        val parsed = ChatCommandParser.parse("hello there")
        assertEquals(ChatCommandType.unknown, parsed.type)
        assertEquals("", parsed.rawCommand)
        assertEquals("", parsed.argument)
        assertFalse(parsed.isCommand)
    }

    @Test
    fun taskCommandWithoutArgument() {
        val parsed = ChatCommandParser.parse("/task")
        assertEquals(ChatCommandType.task, parsed.type)
        assertEquals("task", parsed.rawCommand)
        assertEquals("", parsed.argument)
        assertTrue(parsed.isCommand)
    }

    @Test
    fun taskCommandKeepsTrimmedArgument() {
        val parsed = ChatCommandParser.parse("/task   buy milk  ")
        assertEquals(ChatCommandType.task, parsed.type)
        assertEquals("buy milk", parsed.argument)
    }

    @Test
    fun commandNameIsCaseInsensitive() {
        assertEquals(ChatCommandType.poll, ChatCommandParser.parse("/Poll best?").type)
        assertEquals(ChatCommandType.remind, ChatCommandParser.parse("/REMIND call").type)
    }

    @Test
    fun unknownSlashCommandKeepsRawCommandAndArgument() {
        val parsed = ChatCommandParser.parse("/dance now")
        assertEquals(ChatCommandType.unknown, parsed.type)
        assertEquals("dance", parsed.rawCommand)
        assertEquals("now", parsed.argument)
        assertFalse(parsed.isCommand)
    }

    // --- EmojiRecentCache (lib/core/emoji/services/emoji_recent_cache.dart) ---

    private fun freshCache(now: () -> Long = { 1000L }) =
        EmojiRecentCache(store = EmojiRecentStore.InMemory(), now = now).apply { initialize() }

    @Test
    fun recentsAreMostRecentFirst() {
        val cache = freshCache()
        cache.recordUsage("😀", "grinning face")
        cache.recordUsage("👍", "thumbs up")
        val items = cache.items()
        assertEquals(2, items.size)
        assertEquals("👍", items[0].unicode)
        assertEquals("😀", items[1].unicode)
    }

    @Test
    fun rerecordingMovesEmojiToFront() {
        val cache = freshCache()
        cache.recordUsage("😀", "grinning face")
        cache.recordUsage("👍", "thumbs up")
        cache.recordUsage("😀", "grinning face")
        val items = cache.items()
        assertEquals(2, items.size)
        assertEquals("😀", items[0].unicode)
    }

    @Test
    fun recentsBoundedAt32() {
        val cache = freshCache()
        for (i in 1..40) cache.recordUsage("e$i", "emoji $i")
        assertEquals(32, cache.items().size)
        assertEquals("e40", cache.items()[0].unicode)
    }

    @Test
    fun recentsRoundTripThroughStore() {
        var tick = 0L
        val store = EmojiRecentStore.InMemory()
        val first = EmojiRecentCache(store = store, now = { ++tick })
        first.initialize()
        first.recordUsage("😀", "grinning face")
        first.recordUsage("👍", "thumbs up")

        val second = EmojiRecentCache(store = store, now = { ++tick })
        second.initialize()
        val items = second.items()
        assertEquals(2, items.size)
        assertEquals("👍", items[0].unicode)
        assertEquals("thumbs up", items[0].label)
        assertEquals(2L, items[0].lastUsedTimestamp)
    }

    @Test
    fun corruptStoreStartsEmpty() {
        val store = object : EmojiRecentStore {
            override fun load(): String? = "not json"
            override fun save(raw: String) {}
        }
        val cache = EmojiRecentCache(store = store)
        cache.initialize()
        assertTrue(cache.items().isEmpty())
        cache.recordUsage("😀", "grinning face")
        assertEquals(1, cache.items().size)
    }
}
