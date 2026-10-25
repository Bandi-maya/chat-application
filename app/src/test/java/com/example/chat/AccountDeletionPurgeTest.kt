package com.example.chat

import com.example.chat.data.services.AccountDeletionPurge
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Fixture-only: temp dirs and fakes, no real account data. */
class AccountDeletionPurgeTest {
    private fun purge(log: MutableList<String>, failAt: String? = null, mlsFails: Boolean = false, mediaFails: Boolean = false) =
        AccountDeletionPurge(
            clearOutbox = { log += "outbox:$it"; if (failAt == "outbox") throw IllegalStateException("outbox") },
            purgeMlsIdentity = { log += "mls:$it"; if (mlsFails) throw IllegalStateException("mls") },
            purgeTemporaryFiles = { log += "media"; if (mediaFails) throw IllegalStateException("media") },
            deleteSnapshots = { log += "snap:$it"; if (failAt == "snap") throw IllegalStateException("snap") },
            deleteAllSecureStorage = { log += "secure"; if (failAt == "secure") throw IllegalStateException("secure") },
        )

    @Test
    fun runsInDartOrderSecureStorageLast() {
        val log = mutableListOf<String>()
        runBlocking { purge(log).purge("u1") }
        assertEquals(listOf("outbox:u1", "mls:u1", "media", "snap:u1", "secure"), log)
    }

    @Test
    fun mlsAndMediaFailuresAreSkipped() {
        val log = mutableListOf<String>()
        runBlocking { purge(log, mlsFails = true, mediaFails = true).purge("u1") }
        assertEquals(listOf("outbox:u1", "mls:u1", "media", "snap:u1", "secure"), log)
    }

    @Test
    fun outboxFailureAbortsBeforeAnythingElse() {
        val log = mutableListOf<String>()
        try { runBlocking { purge(log, failAt = "outbox").purge("u1") }; fail() } catch (e: IllegalStateException) { assertEquals("outbox", e.message) }
        assertEquals(listOf("outbox:u1"), log)
    }

    @Test
    fun snapshotFailureSkipsFinalSecureSweep() {
        val log = mutableListOf<String>()
        try { runBlocking { purge(log, failAt = "snap").purge("u1") }; fail() } catch (e: IllegalStateException) { assertEquals("snap", e.message) }
        assertFalse(log.contains("secure"))
    }

    @Test
    fun temporaryFilesOnlyChatyPrefixedRegularFilesNoRecursion() {
        val dir = java.nio.file.Files.createTempDirectory("purge").toFile()
        try {
            val a = File(dir, "chaty_upload_1.jpg").apply { writeText("x") }
            val b = File(dir, "other.jpg").apply { writeText("x") }
            val sub = File(dir, "chaty_dir").apply { mkdir() }
            val nested = File(sub, "chaty_inner.jpg").apply { writeText("x") }
            AccountDeletionPurge.purgeChatyTemporaryFiles(dir)
            assertFalse(a.exists())
            assertTrue(b.exists())
            assertTrue(sub.exists())
            assertTrue(nested.exists())
            AccountDeletionPurge.purgeChatyTemporaryFiles(File(dir, "missing"))
        } finally { dir.deleteRecursively() }
    }
}
