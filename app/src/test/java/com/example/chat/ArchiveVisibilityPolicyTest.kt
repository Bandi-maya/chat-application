package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.home.ArchiveVisibilityPolicy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveVisibilityPolicyTest {
    private fun chat(id: String, archived: Boolean = true) = Conversation(
        id = id, type = ConversationType.direct, title = "Private $id",
        participantIds = emptyList(), lastMessageText = "message $id",
        lastMessageTime = Instant.EPOCH, lastMessageSenderId = "sender", isArchived = archived,
    )

    @Test fun hiddenArchiveDoesNotAppearInOrdinaryArchive() {
        val rows = listOf(chat("hidden"), chat("visible"), chat("active", false))
        assertEquals(listOf("visible"), ArchiveVisibilityPolicy.visible(rows) { it == "hidden" }.map { it.id })
    }

    @Test fun hiddenOnlyArchiveHasNoRows() {
        assertTrue(ArchiveVisibilityPolicy.visible(listOf(chat("hidden"))) { true }.isEmpty())
    }

    @Test fun visibleOrderingIsUnchanged() {
        val rows = listOf(chat("z"), chat("a"), chat("m"))
        assertEquals(rows, ArchiveVisibilityPolicy.visible(rows) { false })
    }

    @Test fun actionDropsSelectionThatBecameHiddenOrUnarchivedOrRemoved() {
        val rows = listOf(chat("hidden"), chat("active", false), chat("visible"))
        assertEquals(listOf("visible"), ArchiveVisibilityPolicy.actionableIds(
            setOf("removed", "hidden", "active", "visible"), rows,
        ) { it == "hidden" })
    }

    @Test fun privacyIsRecheckedAtActionTime() {
        var hidden = false
        val rows = listOf(chat("one"))
        val selected = setOf("one")
        assertEquals(listOf("one"), ArchiveVisibilityPolicy.actionableIds(selected, rows) { hidden })
        hidden = true
        assertTrue(ArchiveVisibilityPolicy.actionableIds(selected, rows) { hidden }.isEmpty())
    }

    @Test fun bulkActionDoesNotDuplicateIds() {
        assertEquals(listOf("one"), ArchiveVisibilityPolicy.actionableIds(
            setOf("one"), listOf(chat("one"), chat("one")),
        ) { false })
    }

    @Test fun clearingSelectionMakesActionsEmpty() {
        assertTrue(ArchiveVisibilityPolicy.actionableIds(emptySet(), listOf(chat("one"))) { false }.isEmpty())
    }
}
