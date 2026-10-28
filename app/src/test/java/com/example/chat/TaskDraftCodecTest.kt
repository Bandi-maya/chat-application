package com.example.chat

import com.example.chat.ui.tasks.TaskDraftCodec
import com.example.chat.ui.tasks.TaskDraftValues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Draft codec round-trips: the saveable string map must reproduce the typed
 * draft exactly, for any identifier characters (no delimiter fragility).
 */
class TaskDraftCodecTest {

    @Test
    fun multiAssigneeExactRoundTrip() {
        val draft = TaskDraftValues(
            title = "Ship v8 parity",
            description = "multi-line\nbody with, punctuation and spaces",
            assigneeIds = listOf("user-", "a b,c", "unicode-\u00e9\u0915", "ctl-\u0001-in-id"),
            priorityName = "high",
            dueAtIso = "2026-10-30T10:15:00Z",
        )
        assertEquals(draft, TaskDraftCodec.decode(TaskDraftCodec.encode(draft)))
    }

    @Test
    fun emptyAssigneeListRoundTrip() {
        val draft = TaskDraftValues(
            title = "No assignees yet",
            description = "",
            assigneeIds = emptyList(),
            priorityName = "medium",
            dueAtIso = "",
        )
        assertEquals(draft, TaskDraftCodec.decode(TaskDraftCodec.encode(draft)))
    }

    @Test
    fun singleAssigneeRoundTripPreservesOrderAndContent() {
        val draft = TaskDraftValues(
            title = "t", description = "d",
            assigneeIds = listOf("only-one"),
            priorityName = "urgent", dueAtIso = "2026-10-29T00:00:00Z",
        )
        assertEquals(draft, TaskDraftCodec.decode(TaskDraftCodec.encode(draft)))
    }

    @Test
    fun encodeUsesOnlyBundleSafeStrings() {
        val draft = TaskDraftValues(
            title = "x", description = "y",
            assigneeIds = listOf("id1", "id2"),
            priorityName = "low", dueAtIso = "z",
        )
        val encoded = TaskDraftCodec.encode(draft)
        assert(encoded.keys.all { it is String })
        assert(encoded.values.all { it is String })
    }
}
