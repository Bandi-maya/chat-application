package com.example.chat.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotScopeNamesTest {
    @Test fun `listing filters to allowed scopes of one user and dedupes`() {
        val names = listOf(
            "chaty_snapshot_u1_conversations.bin", "chaty_snapshot_u1_messages_c-1.bin",
            "chaty_snapshot_u1_other_thing.bin", "chaty_snapshot_u2_conversations.bin",
            "chaty_snapshot_u1_messages_c-1.bin.tmp", "unrelated.bin",
        )
        assertEquals(listOf("conversations", "messages_c-1"), SnapshotScopeNames.scopesFor("u1", names))
    }
}
