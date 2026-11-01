package com.example.chat

import com.example.chat.ui.chats.detail.PollDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PollDraftValidationTest {
    @Test
    fun trimsQuestionAndOptionsAndDropsBlankOptions() {
        val draft = PollDraft.normalized("  Lunch?  ", listOf(" Pizza ", "", " Salad "), false)
        assertNotNull(draft)
        assertEquals("Lunch?", draft?.question)
        assertEquals(listOf("Pizza", "Salad"), draft?.options)
    }

    @Test
    fun rejectsEmptyOrOversizedQuestion() {
        assertNull(PollDraft.normalized("   ", listOf("A", "B"), false))
        assertNull(PollDraft.normalized("Q".repeat(PollDraft.QUESTION_MAX_LENGTH + 1), listOf("A", "B"), false))
        assertNotNull(PollDraft.normalized("Q".repeat(PollDraft.QUESTION_MAX_LENGTH), listOf("A", "B"), false))
    }

    @Test
    fun enforcesOptionCountAndLengthLimits() {
        assertNull(PollDraft.normalized("Question", listOf("A"), false))
        assertNull(PollDraft.normalized("Question", (1..(PollDraft.MAX_OPTIONS + 1)).map { "O$it" }, false))
        assertNull(PollDraft.normalized("Question", listOf("A", "B".repeat(PollDraft.OPTION_MAX_LENGTH + 1)), false))
        assertNotNull(PollDraft.normalized("Question", (1..PollDraft.MAX_OPTIONS).map { "O$it" }, true))
    }
}
