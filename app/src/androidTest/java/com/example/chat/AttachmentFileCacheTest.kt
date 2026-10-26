package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.data.local.AttachmentCachePolicy
import com.example.chat.data.local.AttachmentFileCache
import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditResult
import com.example.chat.data.local.EditSession
import com.example.chat.data.local.ExportPurpose
import com.example.chat.data.local.ExportResult
import com.example.chat.data.local.LeasedFile
import com.example.chat.data.local.StorePut
import com.example.chat.data.local.StoreRead
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic bytes only, test tenant/users, app-private dirs; needs a device/emulator (not run yet). */
@RunWith(AndroidJUnit4::class)
class AttachmentFileCacheTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    @Volatile private var user: String? = "test-user-a"
    @Volatile private var tenant = "test-tenant"
    @Volatile private var policyAllows = true
    /** FAKE validator for pure store tests: NOT production trust (production uses AndroidEditedOutputValidator). */
    @Volatile private var validatorOk = true
    private fun cache(max: Long = 1_000_000L, entry: Long = 500_000L) =
        AttachmentFileCache(ctx, { user?.let { com.example.chat.data.local.SessionEpoch.Identity(tenant, it, 0L) } }, { max }, entry, exportPolicy = { policyAllows },
            editedOutputValidator = { _, _ -> validatorOk })
    private fun bytes(c: AttachmentFileCache, a: String, @Suppress("UNUSED_PARAMETER") u: String = ""): ByteArray? =
        (c.readVerified(R(a), 100_000) as? StoreRead.Hit)?.value
    private fun leaseOf(c: AttachmentFileCache, a: String, u: String): LeasedFile? =
        (c.lease(R(a)) as? StoreRead.Hit)?.value
    /** put() under the CURRENT session stamp (what a caller that authorized just now would pass). */
    private fun AttachmentFileCache.putNow(ref: AttachmentRef, src: InputStream, vo: Boolean = false, r: Boolean = false) =
        put(ref, src, vo, r, session() ?: "none")
    private fun R(id: String) = AttachmentRef("conv", "msg", id)
    private fun stream(b: ByteArray): InputStream = ByteArrayInputStream(b)
    private val secret = ("PLAINTEXT-MARKER-" + "x".repeat(5000)).toByteArray()

    @Before @After fun clean() {
        File(ctx.filesDir, "chaty_media").deleteRecursively(); File(ctx.noBackupFilesDir, "chaty_media_tmp").deleteRecursively(); File(ctx.noBackupFilesDir, "chaty_exports").deleteRecursively()
    }

    @Test fun roundTripVerifiedAndNoPlaintextOnDisk() {
        val c = cache()
        assertEquals(StorePut.Stored, c.putNow(R("a1"), stream(secret), false, false))
        assertArrayEquals(secret, bytes(c, "a1", "u"))
        File(ctx.filesDir, "chaty_media").walkTopDown().filter { it.isFile }.toList().also { assertTrue(it.isNotEmpty()) }
            .forEach { assertFalse(String(it.readBytes(), Charsets.ISO_8859_1).contains("PLAINTEXT-MARKER")) }
    }

    @Test fun viewOnceAndRestrictedNeverStored() {
        val c = cache()
        assertNotEquals(StorePut.Stored, c.putNow(R("v"), stream(secret), true, false)); assertNotEquals(StorePut.Stored, c.putNow(R("r"), stream(secret), false, true))
        assertNull(bytes(c, "v", "u"))
        assertFalse(File(ctx.filesDir, "chaty_media").walkTopDown().any { it.isFile })
    }

    @Test fun accountsIsolatedAndLoggedOutClosed() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        user = "test-user-b"; assertNull(bytes(c, "a1", "u"))
        user = null; assertNull(bytes(c, "a1", "u")); assertNotEquals(StorePut.Stored, c.putNow(R("a2"), stream(secret), false, false))
        assertNull(leaseOf(c, "a1", "u"))
        user = "test-user-a"; assertNotNull(bytes(c, "a1", "u"))
    }

    @Test fun logoutDuringWriteDiscardsTheEntry() {
        val c = cache()
        val switching = object : InputStream() {
            var n = 0
            override fun read(): Int { if (n++ == 50) user = null; return if (n > 20_000) -1 else 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { b[off] = 1; return if (read() < 0) -1 else 1 }
        }
        assertNotEquals(StorePut.Stored, c.putNow(R("a1"), switching, false, false))
        user = "test-user-a"; assertNull(bytes(c, "a1", "u"))
        assertEquals(0L, c.totalBytes())
    }

    @Test fun resultsDistinguishMissCorruptAndClosed() {
        val c = cache()
        assertTrue(c.readVerified(R("none"), 100) is StoreRead.Miss)
        c.putNow(R("a1"), stream(secret), false, false)
        assertTrue(c.readVerified(R("a1"), 100) is StoreRead.TooLarge)
        val f = File(ctx.filesDir, "chaty_media").walkTopDown().first { it.isFile && it.name.endsWith(".bin") }
        f.writeBytes(f.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        assertTrue(c.readVerified(R("a1"), 100_000) is StoreRead.Corrupt)
        user = null; assertTrue(c.readVerified(R("a1"), 100_000) is StoreRead.Closed)
    }

    @Test fun deleteAccountRemovesEveryTenantNamespace() {
        val c = cache()
        c.putNow(R("a1"), stream(secret), false, false)
        tenant = "other-tenant"; c.putNow(R("a1"), stream(secret), false, false)
        user = null; c.deleteAccount("test-user-a"); user = "test-user-a"
        assertNull(bytes(c, "a1", "u")); tenant = "test-tenant"; assertNull(bytes(c, "a1", "u"))
    }

    @Test fun loweredLimitAppliesOnTrim() {
        var limit = 100_000L
        val c = AttachmentFileCache(ctx, { user?.let { com.example.chat.data.local.SessionEpoch.Identity(tenant, it, 0L) } }, { limit }, 50_000L)
        val chunk = ByteArray(8_000) { 2 }
        c.putNow(R("a"), stream(chunk), false, false); Thread.sleep(1100); c.putNow(R("b"), stream(chunk), false, false)
        limit = 9_000L; c.trimToLimit()
        assertNull(bytes(c, "a", "u")); assertNotNull(bytes(c, "b", "u"))
    }

    @Test fun deleteAccountWorksWhileLoggedOut() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        user = null; c.deleteAccount("test-user-a"); user = "test-user-a"
        assertNull(bytes(c, "a1", "u"))
    }

    @Test fun oversizeAndInterruptedWritesLeaveNothing() {
        assertNotEquals(StorePut.Stored, cache(entry = 1_000L).putNow(R("big"), stream(secret), false, false))
        val cut = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 100) 7 else throw java.io.IOException("cut")
        }
        val c = cache(); assertNotEquals(StorePut.Stored, c.putNow(R("p"), cut, false, false))
        assertNull(bytes(c, "p", "u")); assertEquals(0L, c.totalBytes())
    }

    @Test fun tamperedOrTruncatedEntryReadsAsMissAndIsDeleted() {
        val c = cache(); c.putNow(R("a1"), stream(ByteArray(20_000) { it.toByte() }), false, false)
        val f = File(ctx.filesDir, "chaty_media").walkTopDown().first { it.isFile && it.name.endsWith(".bin") }
        f.writeBytes(f.readBytes().copyOf(f.length().toInt() - 4100))      // drop the last 4KB segment
        assertNull(bytes(c, "a1", "u"))
        assertFalse(f.exists())
        c.putNow(R("a2"), stream(secret), false, false)
        val g = File(ctx.filesDir, "chaty_media").walkTopDown().first { it.isFile && it.name.endsWith(".bin") }
        g.writeBytes(g.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })
        assertNull(leaseOf(c, "a2", "u")); assertFalse(g.exists())
    }

    @Test fun leaseIsVerifiedDecryptedAndTempsPurged() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val l = leaseOf(c, "a1", "u")!!
        assertArrayEquals(secret, l.file.readBytes())
        c.purgeTransientTemps(); assertFalse(l.file.exists())
        val l2 = leaseOf(c, "a1", "u")!!; l2.release(); assertFalse(l2.file.exists())
        val l3 = leaseOf(c, "a1", "u")!!; user = "test-user-b"; c.purgeTransientTemps(); assertFalse(l3.file.exists())
    }

    @Test fun capEvictsOldestFirstAndConcurrentWriterPartialSurvivesAnotherPut() {
        val c = cache(max = 12_000L, entry = 8_000L); val chunk = ByteArray(5_000) { 1 }
        c.putNow(R("old"), stream(chunk), false, false); Thread.sleep(1100)
        c.putNow(R("mid"), stream(chunk), false, false); Thread.sleep(1100)
        c.putNow(R("new"), stream(chunk), false, false)
        assertNull(bytes(c, "old", "u")); assertNotNull(bytes(c, "new", "u"))
        // a slow writer must not be purged by a concurrent put to another entry
        val gate = java.util.concurrent.CountDownLatch(1); var slowOk = false
        val slow = Thread {
            slowOk = StorePut.Stored == c.putNow(R("slow"), object : InputStream() {
                var n = 0
                override fun read(): Int = -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (n == 0) { n++; gate.await(); b[off] = 5; return 1 }; return -1
                }
            }, false, false)
        }.also { it.start() }
        Thread.sleep(300); c.putNow(R("other"), stream(chunk), false, false); gate.countDown(); slow.join(5000)
        assertTrue(slowOk); assertNotNull(bytes(c, "slow", "u"))
    }

    @Test fun clearAndEntryNameHasNoUrlOrId() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        assertFalse(AttachmentCachePolicy.entryName(R("a1")).contains("secret"))
        c.clearCurrentAccount(); assertEquals(0L, c.totalBytes())
        assertNull(bytes(c, "a1", "https://secret.example/x"))
    }

    @Test fun directAtoBSwitchRevokesOutstandingLeaseWithoutNullInBetween() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val lease = leaseOf(c, "a1", "u")!!; assertTrue(lease.file.exists())
        user = "test-user-b"                       // no null in between
        assertFalse(c.isOpen().not())              // B is open
        assertFalse(lease.file.exists())           // A's decrypted temp was revoked on first call under B
        assertNull(bytes(c, "a1", "u"))
    }

    @Test fun isOpenFalseWhenSignedOut() { val c = cache(); user = null; assertFalse(c.isOpen()) }

    @Test fun exportCopyGrantsOwnedFileThenReleaseExpiryLogoutAndSwitchRemoveIt() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val sess = c.session()!!
        val g1 = c.exportCopy(R("a1"), sess, ExportPurpose.SHARE, "JPG", false, false, 60_000) as ExportResult.Granted
        assertTrue(g1.token.isValid()); assertTrue(g1.token.file.exists()); assertTrue(g1.token.file.name.endsWith(".jpg"))
        assertTrue(g1.token.file.path.contains("chaty_exports")); assertFalse(g1.token.file.path.startsWith(ctx.cacheDir.path)); assertFalse(g1.token.file.path.startsWith(ctx.filesDir.path)); assertTrue(g1.token.uri.startsWith("content://" + ctx.packageName + ".exportprovider/"))
        g1.token.release(); assertFalse(g1.token.file.exists()); assertFalse(g1.token.isValid())
        // expiry: lifetime 0 => never valid
        val g2 = c.exportCopy(R("a1"), sess, ExportPurpose.OPEN, "jpg", false, false, 0) as ExportResult.Granted
        assertFalse(g2.token.isValid()); g2.token.release()
        // logout/switch: file deleted, token invalid
        val g3 = c.exportCopy(R("a1"), sess, ExportPurpose.EDIT, "jpg", false, false, 60_000) as ExportResult.Granted
        user = "test-user-b"; c.session(); assertFalse(g3.token.isValid()); assertFalse(g3.token.file.exists())
    }

    @Test fun exportDeniedForPolicyStaleSessionAndSignedOut() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false); val sess = c.session()!!
        assertTrue(c.exportCopy(R("a1"), sess, ExportPurpose.SHARE, "jpg", true, false, 1000) is ExportResult.Denied)
        assertTrue(c.exportCopy(R("a1"), sess, ExportPurpose.SHARE, "jpg", false, true, 1000) is ExportResult.Denied)
        assertTrue(c.exportCopy(R("a1"), "stale", ExportPurpose.SHARE, "jpg", false, false, 1000) is ExportResult.Denied)
        c.purgeTransientTemps()                                          // logout of same account bumps the session stamp
        assertNotEquals(sess, c.session())
        assertTrue(c.exportCopy(R("a1"), sess, ExportPurpose.SHARE, "jpg", false, false, 1000) is ExportResult.Denied)
        user = null; assertNull(c.session())
    }

    @Test fun clearAndDeleteAccountAlsoRemoveExportFiles() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val g = c.exportCopy(R("a1"), c.session()!!, ExportPurpose.SHARE, "jpg", false, false, 60_000) as ExportResult.Granted
        c.clearCurrentAccount(); assertFalse(g.token.file.exists())
    }

    @Test fun expiredExportFileIsDeletedByTimerAndSweepEvenWithoutRelease() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val g = c.exportCopy(R("a1"), c.session()!!, ExportPurpose.SHARE, "jpg", false, false, 200) as ExportResult.Granted
        assertTrue(g.token.file.exists())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(600)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse(g.token.file.exists()); assertFalse(g.token.isValid())
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    private fun begin(c: AttachmentFileCache, kind: EditKind = EditKind.IMAGE_JPEG) =
        c.beginEdit(R("a1"), c.session()!!, kind, false, false, 60_000)

    @Test fun editCommitProducesNewTokenForOutputAndOriginalEntryIsUntouched() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val s = (begin(c) as EditResult.Started).session
        assertTrue(s.outputFile.name.endsWith(".part")); assertTrue(s.inputUri.startsWith("content://" + ctx.packageName + ".exportprovider/x/"))
        s.outputFile.writeBytes(jpeg)
        val g = c.commitEdit(s, ExportPurpose.SHARE, 60_000) as ExportResult.Granted
        assertArrayEquals(jpeg, g.token.file.readBytes()); assertFalse(s.outputFile.exists())
        assertNotEquals(s.outputFile.path, g.token.file.path)
        assertArrayEquals(secret, bytes(c, "a1", "u"))                       // original preserved
        g.token.release(); assertFalse(g.token.file.exists())
    }

    @Test fun editCommitRejectsBadMagicOversizeEmptySymlinkAndForeignSession() {
        val c = cache(entry = 500_000L); c.putNow(R("a1"), stream(secret), false, false)
        var s = (begin(c) as EditResult.Started).session
        s.outputFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))                       // not a JPEG
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied); assertFalse(s.outputFile.exists())
        s = (begin(c) as EditResult.Started).session                                       // empty output
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied)
        s = (begin(c) as EditResult.Started).session                                       // symlink to another file
        val target = File(ctx.cacheDir, "chaty_link_target.jpg").apply { writeBytes(jpeg) }
        s.outputFile.delete(); java.nio.file.Files.createSymbolicLink(s.outputFile.toPath(), target.toPath())
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied); target.delete()
        s = (begin(c) as EditResult.Started).session                                       // a session another cache minted
        val other = cache(); assertTrue(other.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied)
        c.abortEdit(s)
    }

    @Test fun editCommitDeniedAfterSwitchPolicyChangeOrExpiryAndCleansOwnedFiles() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        var s = (begin(c) as EditResult.Started).session; s.outputFile.writeBytes(jpeg)
        policyAllows = false                                                                // became view-once
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied); assertFalse(s.outputFile.exists())
        policyAllows = true
        s = (begin(c) as EditResult.Started).session; s.outputFile.writeBytes(jpeg)
        user = "test-user-b"
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied); assertFalse(s.outputFile.exists())
    }

    @Test fun abortRemovesOnlyOwnInputAndOutputAndRegistryRevokesOnDeleteHook() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val s = (begin(c) as EditResult.Started).session; c.abortEdit(s); assertFalse(s.outputFile.exists())
        val g = c.exportCopy(R("a1"), c.session()!!, ExportPurpose.SHARE, "jpg", false, false, 60_000) as ExportResult.Granted
        val id = g.token.uri.substringAfterLast('/')
        assertNotNull(c.exportRegistry.open(id))
        c.onAttachmentGone(R("a1"))                                                          // message deleted
        assertNull(c.exportRegistry.open(id)); assertFalse(g.token.file.exists()); assertNull(bytes(c, "a1", "u"))
    }

    @Test fun registryOpenFailsAfterPolicyChangeAndLogoutEvenWithFileOnDisk() {
        val c = cache(); c.putNow(R("a1"), stream(secret), false, false)
        val g = c.exportCopy(R("a1"), c.session()!!, ExportPurpose.SHARE, "jpg", false, false, 60_000) as ExportResult.Granted
        val id = g.token.uri.substringAfterLast('/')
        policyAllows = false; assertNull(c.exportRegistry.open(id)); assertFalse(g.token.file.exists())
    }

    @Test fun commitDeniedWhenContentValidatorRejectsOrIsUnwired() {
        val c = cache(); c.putNow(R("a1"), stream(secret))
        fun begin() = (c.beginEdit(R("a1"), c.session()!!, EditKind.IMAGE_JPEG, false, false, 60_000) as EditResult.Started).session
        fun writeJpegMagic(s: EditSession) = s.outputFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9))
        validatorOk = false
        val s = begin(); writeJpegMagic(s)                       // magic is right, content is not
        assertTrue(c.commitEdit(s, ExportPurpose.SHARE, 1000) is ExportResult.Denied); assertFalse(s.outputFile.exists())
        validatorOk = true
        val unwired = AttachmentFileCache(ctx, { user?.let { com.example.chat.data.local.SessionEpoch.Identity(tenant, it, 0L) } }, { 1_000_000L }, 500_000L, exportPolicy = { true })
        val s2 = (unwired.beginEdit(R("a1"), unwired.session()!!, EditKind.IMAGE_JPEG, false, false, 60_000) as? EditResult.Started)?.session
        if (s2 != null) { writeJpegMagic(s2); assertTrue(unwired.commitEdit(s2, ExportPurpose.SHARE, 1000) is ExportResult.Denied) }
    }

    @Test fun putWithAStaleSessionWritesNothingEvenAfterAnAccountSwitchBackToTheSameUser() {
        val c = cache(); val stale = c.session()!!
        user = "test-user-b"; c.session(); user = "test-user-a"                      // A -> B -> A: same uid, new stamp
        assertEquals(StorePut.Closed, c.put(R("a1"), stream(secret), false, false, stale))
        assertNull(bytes(c, "a1"))                                                   // nothing was written
        assertEquals(StorePut.Stored, c.putNow(R("a1"), stream(secret)))
    }
}
