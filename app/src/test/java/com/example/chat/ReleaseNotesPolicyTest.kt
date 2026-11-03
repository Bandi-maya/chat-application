package com.example.chat

import com.example.chat.ui.updates.ReleaseHistoryEntry
import com.example.chat.ui.updates.ReleaseNotesPolicy
import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesPolicyTest {
    private val current = ReleaseHistoryEntry("1.0.0", 1, "r2", listOf("Actual change"))
    @Test fun firstInstallShows() { assertTrue(ReleaseNotesPolicy.shouldShow(null, current)) }
    @Test fun identicalReinstallStaysDismissed() { assertFalse(ReleaseNotesPolicy.shouldShow(current.identity, current.copy())) }
    @Test fun newVersionShows() { assertTrue(ReleaseNotesPolicy.shouldShow(current.identity, current.copy(versionName = "1.1.0"))) }
    @Test fun newCodeShows() { assertTrue(ReleaseNotesPolicy.shouldShow(current.identity, current.copy(versionCode = 2))) }
    @Test fun developmentNotesRevisionShows() { assertTrue(ReleaseNotesPolicy.shouldShow(current.identity, current.copy(revision = "r3"))) }
    @Test fun oldTimestampKeyDoesNotMasqueradeAsNewIdentity() {
        assertTrue(ReleaseNotesPolicy.shouldShow("1.0.0 (1) @123", current))
    }
    @Test fun historyRetainsAllObservedVersionsNewestFirst() {
        val older = current.copy(versionCode = 0, revision = "r1")
        assertEquals(listOf(current, older), ReleaseNotesPolicy.history(current, listOf(older)))
    }
    @Test fun duplicateCurrentNotInsertedAgain() {
        assertEquals(listOf(current), ReleaseNotesPolicy.history(current, listOf(current, current)))
    }
    @Test fun previousSnapshotIsNotRewritten() {
        val old = current.copy(revision = "r1", changes = listOf("Original notes"))
        assertEquals(old, ReleaseNotesPolicy.history(current, listOf(old))[1])
    }
    @Test fun noInventedHistoricalVersions() { assertEquals(listOf(current), ReleaseNotesPolicy.history(current, emptyList())) }
    @Test fun noArtificialHistoryLimit() {
        val old = (0..149).map { current.copy(versionCode = it.toLong(), revision = "old$it") }
        assertEquals(151, ReleaseNotesPolicy.history(current, old).size)
    }
    @Test fun separatorsInNamesDoNotCollide() {
        assertNotEquals(ReleaseNotesPolicy.identity("a:1", 2, "r"), ReleaseNotesPolicy.identity("a", 1, "2:r"))
    }
}
