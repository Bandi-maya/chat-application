package com.example.chat

import com.example.chat.data.local.AttachmentCachePolicy
import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.local.AttachmentStore
import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditResult
import com.example.chat.data.local.EditSession
import com.example.chat.data.local.ExportPurpose
import com.example.chat.data.local.ExportResult
import com.example.chat.data.local.ExportToken
import com.example.chat.data.local.LeasedFile
import com.example.chat.data.local.StorePut
import com.example.chat.data.local.StoreRead
import com.example.chat.data.media.AttachmentSourceRequest
import com.example.chat.data.media.AttachmentSourceResolver
import com.example.chat.data.media.ResolvedSource
import com.example.chat.data.media.ResolvedSource.Reason
import com.example.chat.data.media.TrustedAttachment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AttachmentSourceResolverTest {
    private class FakeStore : AttachmentStore {
        val data = HashMap<String, ByteArray>()
        val corrupt = HashSet<String>()
        val leases = ArrayList<File>()
        var closed = false
        var seq = 1
        var putResult: StorePut? = null
        var putCalls = 0
        var invalidated = 0
        var exports = 0
        var onRead: () -> Unit = {}
        fun k(r: AttachmentRef) = AttachmentCachePolicy.entryName(r)
        override fun put(ref: AttachmentRef, source: InputStream, viewOnce: Boolean, restricted: Boolean, expectedSession: String): StorePut {
            putCalls++
            if (closed || session() != expectedSession) return StorePut.Closed      // models the real store: no write
            if (!AttachmentCachePolicy.mayPersist(viewOnce, restricted)) return StorePut.NotAllowed
            putResult?.let { return it }
            corrupt.remove(k(ref)); data[k(ref)] = source.readBytes(); return StorePut.Stored
        }
        override fun readVerified(ref: AttachmentRef, maxBytes: Int): StoreRead<ByteArray> {
            if (closed) return StoreRead.Closed
            val key = k(ref)
            if (key in corrupt) { data.remove(key); return StoreRead.Corrupt }
            val b = data[key] ?: return StoreRead.Miss
            onRead()
            return if (b.size > maxBytes) StoreRead.TooLarge else StoreRead.Hit(b)
        }
        override fun lease(ref: AttachmentRef): StoreRead<LeasedFile> {
            if (closed) return StoreRead.Closed
            val key = k(ref)
            if (key in corrupt) { data.remove(key); return StoreRead.Corrupt }
            val b = data[key] ?: return StoreRead.Miss
            val f = File.createTempFile("chaty_test_", ".tmp").apply { writeBytes(b) }
            leases += f
            onRead()
            return StoreRead.Hit(LeasedFile(f) { f.delete() })
        }
        override fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String,
                                viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): ExportResult =
            if (viewOnce || restricted) ExportResult.Denied("policy") else if (session() != expectedSession) ExportResult.Denied("session")
            else if (data[k(ref)] == null) ExportResult.Miss
            else { exports++; val f = File.createTempFile("chaty_exp_", ".$extension").apply { writeBytes(data[k(ref)]!!) }
                ExportResult.Granted(ExportToken(f, "content://x/" + f.name, purpose, { f.exists() }) { f.delete() }) }
        var begins = 0; var aborts = 0; var commits = 0
        override fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): EditResult {
            if (viewOnce || restricted) return EditResult.Denied("policy")
            if (session() != expectedSession) return EditResult.Denied("session")
            if (data[k(ref)] == null) return EditResult.Miss
            begins++
            return EditResult.Started(EditSession("e1", "content://x/e1", File.createTempFile("chaty_e_", ".part"), kind))
        }
        /** When set, commitEdit signals [commitEntered], blocks on [commitGate], then mints a counted token (models a slow native validation). */
        var commitGate: java.util.concurrent.CountDownLatch? = null
        val commitEntered = java.util.concurrent.CountDownLatch(1)
        var minted: File? = null; val mintedReleases = java.util.concurrent.atomic.AtomicInteger()
        override fun commitEdit(session: EditSession, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult {
            commits++
            val gate = commitGate ?: return ExportResult.Failed
            commitEntered.countDown(); gate.await()
            val f = File.createTempFile("chaty_out_", ".jpg"); minted = f
            return ExportResult.Granted(ExportToken(f, "content://x/" + f.name, purpose, { f.exists() }) { mintedReleases.incrementAndGet(); f.delete() })
        }
        override fun abortEdit(session: EditSession) { aborts++; session.outputFile.delete() }
        var pureChecks = 0
        override fun isEpochCurrent(expected: String): Boolean { pureChecks++; return !closed && "s$seq" == expected }
        override fun isOpen(): Boolean = !closed
        override fun session(): String? = if (closed) null else "s$seq"
        override fun invalidate(ref: AttachmentRef) { invalidated++; data.remove(k(ref)) }
    }

    private val ref = AttachmentRef("c1", "m1", "a1")
    private var trusted: TrustedAttachment? = TrustedAttachment("image", false, false)
    private val signed = mutableListOf<String>()
    private fun req(user: Boolean = true, r: AttachmentRef = ref, session: String = "s1",
                    url: suspend () -> String = { signed += "sign"; "https://h/f?sig=${signed.size}" }) =
        AttachmentSourceRequest(r, user, session, url)

    private fun resolver(store: FakeStore, online: Boolean, downloads: MutableList<String> = mutableListOf(),
                         fail: Boolean = false, mem: Int = 1024, onDownload: () -> Unit = {}) =
        AttachmentSourceResolver(store, { online }, { trusted }, { url ->
            downloads += url; onDownload(); if (fail) throw IOException("net") else ByteArrayInputStream("bytes".toByteArray())
        }, mem)

    private fun unavailable(r: ResolvedSource, reason: Reason) = assertEquals(ResolvedSource.Unavailable(reason), r)

    @Test fun userInitiatedDownloadStoresThenServesVerifiedLocalBytes() = runBlocking {
        val s = FakeStore(); val d = mutableListOf<String>()
        val r = resolver(s, true, d).resolve(req())
        assertArrayEquals("bytes".toByteArray(), (r as ResolvedSource.Bytes).bytes); assertEquals(1, d.size)
    }

    @Test fun localHitNeverSignsAUrlSoOfflineWorksAfterUrlExpiry() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "cached".toByteArray()
        assertTrue(resolver(s, false).resolve(req()) is ResolvedSource.Bytes)
        assertTrue(signed.isEmpty())
        assertTrue(resolver(s, true).resolve(req(url = { throw IOException("expired") })) is ResolvedSource.Bytes)
    }

    @Test fun sameAttachmentIdInAnotherMessageOrConversationDoesNotCollide() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "mine".toByteArray()
        val other = AttachmentRef("c2", "m1", "a1"); val other2 = AttachmentRef("c1", "m2", "a1")
        assertNotEquals(s.k(ref), s.k(other)); assertNotEquals(s.k(ref), s.k(other2))
        unavailable(resolver(s, false).resolve(req(r = other)), Reason.OFFLINE)   // a miss, not "mine"
    }

    @Test fun blankIdsAndUnknownOrDeletedMessagesAreRejectedEvenOnCacheHit() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "cached".toByteArray()
        unavailable(resolver(s, false).resolve(req(r = AttachmentRef("", "m1", "a1"))), Reason.NOT_ALLOWED)
        unavailable(resolver(s, false).resolve(req(r = AttachmentRef("c1", "m1", " "))), Reason.NOT_ALLOWED)
        trusted = null                                                              // deleted/unknown
        unavailable(resolver(s, false).resolve(req()), Reason.NOT_ALLOWED)
    }

    @Test fun notUserInitiatedMissStreamsRemoteWithoutStoring() = runBlocking {
        val s = FakeStore(); val d = mutableListOf<String>()
        assertTrue(resolver(s, true, d).resolve(req(user = false)) is ResolvedSource.Remote)
        assertEquals(0, s.putCalls); assertTrue(d.isEmpty())
    }

    @Test fun offlineMissIsUnavailable() = runBlocking { unavailable(resolver(FakeStore(), false).resolve(req()), Reason.OFFLINE) }

    @Test fun signedOutIsClosedEvenForViewOnceAndNoUrlIsSigned() = runBlocking {
        val s = FakeStore().also { it.closed = true }
        unavailable(resolver(s, true).resolve(req()), Reason.SIGNED_OUT)
        trusted = TrustedAttachment("image", true, false)
        unavailable(resolver(s, true).resolve(req()), Reason.SIGNED_OUT)
        assertTrue(signed.isEmpty())
    }

    @Test fun switchBetweenReadAndReturnDropsBytesAndReleasesLease() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "cached".toByteArray()
        s.onRead = { s.seq++ }                                  // A -> B right after the local read completes
        unavailable(resolver(s, false).resolve(req()), Reason.SIGNED_OUT)
        trusted = TrustedAttachment("video", false, false)
        s.seq = 1; s.onRead = { s.seq++ }
        unavailable(resolver(s, false).resolve(req()), Reason.SIGNED_OUT)
        assertTrue(s.leases.isNotEmpty() && s.leases.all { !it.exists() })       // lease was released
    }

    @Test fun finalGuardWipesBytesItDropped() = runBlocking {
        val s = FakeStore(); val stored = "cached".toByteArray(); s.data[s.k(ref)] = stored
        var seen: ByteArray? = null
        val r = AttachmentSourceResolver(s, { false }, { trusted }, { ByteArrayInputStream(ByteArray(0)) }, 1024)
        s.onRead = { s.seq++ }
        unavailable(r.resolve(req()), Reason.SIGNED_OUT)
        assertTrue(stored.all { it == 0.toByte() })      // FakeStore hands out the same array; the guard zeroed it
    }

    @Test fun switchDuringDownloadOrSigningServesNothing() = runBlocking {
        val d = mutableListOf<String>()
        val s1 = FakeStore()
        unavailable(resolver(s1, true, d, onDownload = { s1.seq++ }).resolve(req()), Reason.SIGNED_OUT)
        val s2 = FakeStore(); d.clear()
        unavailable(resolver(s2, true, d).resolve(req(url = { s2.seq++; "https://h/f" })), Reason.SIGNED_OUT)
        assertTrue(d.isEmpty())
        val s3 = FakeStore(); trusted = TrustedAttachment("image", true, false)
        unavailable(resolver(s3, true, d).resolve(req(url = { s3.seq++; "https://h/f" })), Reason.SIGNED_OUT)
    }

    @Test fun restrictionAppearingMidFlightDeniesAndInvalidatesLocalCopy() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "cached".toByteArray()
        s.onRead = { trusted = TrustedAttachment("image", true, false) }   // becomes view-once during the read
        unavailable(resolver(s, false).resolve(req()), Reason.NOT_ALLOWED)
        assertEquals(1, s.invalidated); assertTrue(s.data.isEmpty())
        // and mid-download
        val s2 = FakeStore(); trusted = TrustedAttachment("image", false, false)
        unavailable(resolver(s2, true, onDownload = { trusted = TrustedAttachment("image", false, true) }).resolve(req()), Reason.NOT_ALLOWED)
    }

    @Test fun storeNotAllowedIsPolicyDenialNeverRemote() = runBlocking {
        val s = FakeStore().also { it.putResult = StorePut.NotAllowed }
        unavailable(resolver(s, true).resolve(req()), Reason.NOT_ALLOWED)
    }

    @Test fun storeClosedDuringPutIsSignedOutNotRemote() = runBlocking {
        unavailable(resolver(FakeStore().also { it.putResult = StorePut.Closed }, true).resolve(req()), Reason.SIGNED_OUT)
    }

    @Test fun corruptNeverFallsBackToRemoteUnlessExplicitRedownload() = runBlocking {
        val s = FakeStore(); val d = mutableListOf<String>()
        s.data[s.k(ref)] = "x".toByteArray(); s.corrupt += s.k(ref)
        unavailable(resolver(s, true, d).resolve(req(user = false)), Reason.CORRUPT); assertTrue(d.isEmpty())
        s.data[s.k(ref)] = "x".toByteArray(); s.corrupt += s.k(ref)
        unavailable(resolver(s, false, d).resolve(req()), Reason.CORRUPT)
        s.data[s.k(ref)] = "x".toByteArray(); s.corrupt += s.k(ref)
        assertTrue(resolver(s, true, d).resolve(req()) is ResolvedSource.Bytes); assertEquals(1, d.size)
    }

    @Test fun corruptAndRedownloadCannotBeStoredIsUnavailableNotRemote() = runBlocking {
        for (res in listOf(StorePut.Error, StorePut.TooLarge)) {
            val s = FakeStore().also { it.putResult = res }
            s.data[s.k(ref)] = "x".toByteArray(); s.corrupt += s.k(ref)
            unavailable(resolver(s, true).resolve(req()), Reason.CORRUPT)
        }
    }

    @Test fun viewOnceAndRestrictedFromTrustedStateNeverTouchTheStore() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "stale".toByteArray()
        for (t in listOf(TrustedAttachment("image", true, false), TrustedAttachment("image", false, true))) {
            trusted = t
            assertTrue(resolver(s, true).resolve(req()) is ResolvedSource.Remote)
            unavailable(resolver(s, false).resolve(req()), Reason.OFFLINE)
        }
        assertEquals(0, s.putCalls)
    }

    @Test fun downloadAndSigningFailuresAreUnavailableNotPartial() = runBlocking {
        val s = FakeStore()
        unavailable(resolver(s, true, fail = true).resolve(req()), Reason.DOWNLOAD_FAILED)
        unavailable(resolver(s, true).resolve(req(url = { throw IOException("sign") })), Reason.DOWNLOAD_FAILED)
        assertTrue(s.data.isEmpty())
    }

    @Test fun unstorablePlainMissFallsBackToRemoteOnly() = runBlocking {
        assertTrue(resolver(FakeStore().also { it.putResult = StorePut.TooLarge }, true).resolve(req()) is ResolvedSource.Remote)
    }

    @Test fun videoIsLeasedAndReleaseDeletesTempFile() = runBlocking {
        trusted = TrustedAttachment("video", false, false)
        val s = FakeStore(); s.data[s.k(ref)] = "vid".toByteArray()
        val l = (resolver(s, false).resolve(req()) as ResolvedSource.Leased).lease
        assertTrue(l.file.exists()); l.release(); assertFalse(l.file.exists())
    }

    @Test fun largeImageFallsBackToLeaseWhenTooBigForMemory() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = ByteArray(2048)
        val r = resolver(s, false, mem = 1024).resolve(req())
        assertTrue(r is ResolvedSource.Leased); (r as ResolvedSource.Leased).lease.release()
    }

    @Test fun exportUsesTrustedFlagsNotCallerFlagsAndRejectsForgedIds() = runBlocking {
        val s = FakeStore(); val r = resolver(s, false)
        trusted = TrustedAttachment("image", true, false)
        assertTrue(r.exportCopy(ref, "s1", ExportPurpose.SHARE, "jpg", 1000) is ExportResult.Denied)
        trusted = null                                                            // forged conversation/message/attachment ids
        assertTrue(r.exportCopy(AttachmentRef("cX", "mX", "aX"), "s1", ExportPurpose.SHARE, "jpg", 1000) is ExportResult.Denied)
        trusted = TrustedAttachment("image", false, false)
        assertTrue(r.exportCopy(ref, "OLD", ExportPurpose.EDIT, "jpg", 1000) is ExportResult.Denied)   // stale session
    }

    @Test fun exportOnMissDownloadsVerifiesThenExportsFromStoredCopy() = runBlocking {
        val s = FakeStore(); val d = mutableListOf<String>()
        val r = resolver(s, true, d)
        assertEquals(ExportResult.Miss, r.exportCopy(ref, "s1", ExportPurpose.SHARE, "jpg", 1000))            // no url => plain Miss
        assertTrue(d.isEmpty())
        val g = r.exportCopy(ref, "s1", ExportPurpose.SHARE, "jpg", 1000, { "https://h/f?sig=1" }) as ExportResult.Granted
        assertEquals(1, d.size); assertEquals(1, s.putCalls); assertArrayEquals("bytes".toByteArray(), g.token.file.readBytes())
        g.token.release()
    }

    @Test fun exportOnMissRespectsOfflineSessionPolicyAndStoreFailures() = runBlocking {
        val url: suspend () -> String = { "https://h/f" }
        val d = mutableListOf<String>()
        assertEquals(ExportResult.Failed, resolver(FakeStore(), false, d).exportCopy(ref, "s1", ExportPurpose.OPEN, "jpg", 1000, url))
        val sw = FakeStore()
        assertTrue(resolver(sw, true, d, onDownload = { sw.seq++ }).exportCopy(ref, "s1", ExportPurpose.OPEN, "jpg", 1000, url) is ExportResult.Denied)
        trusted = TrustedAttachment("image", true, false)
        assertTrue(resolver(FakeStore(), true, d).exportCopy(ref, "s1", ExportPurpose.OPEN, "jpg", 1000, url) is ExportResult.Denied)
        assertTrue(d.size == 1)                                                  // only the switch case downloaded
        trusted = TrustedAttachment("image", false, false)
        assertEquals(ExportResult.Failed, resolver(FakeStore().also { it.putResult = StorePut.Error }, true).exportCopy(ref, "s1", ExportPurpose.OPEN, "jpg", 1000, url))
        assertEquals(ExportResult.Failed, resolver(FakeStore(), true, fail = true).exportCopy(ref, "s1", ExportPurpose.OPEN, "jpg", 1000, url))
    }

    @Test fun beginEditDeniesViewOnceForgedAndStaleSessionAndNeverStartsThem() = runBlocking {
        val s = FakeStore(); s.data[s.k(ref)] = "orig".toByteArray(); val r = resolver(s, false)
        assertTrue(r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) is EditResult.Started)
        assertTrue(r.beginEdit(ref, "OLD", EditKind.IMAGE_JPEG, 1000) is EditResult.Denied)
        trusted = null                                                              // lookup has no such message: forged ref
        assertTrue(r.beginEdit(AttachmentRef("cX", "mX", "aX"), "s1", EditKind.IMAGE_JPEG, 1000) is EditResult.Denied)
        trusted = TrustedAttachment("image", false, false)
        trusted = TrustedAttachment("image", true, false)
        assertTrue(r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) is EditResult.Denied)
        assertEquals(1, s.begins)
    }

    @Test fun beginEditOnMissDownloadsVerifiesThenStartsAndAbortsIfPolicyChangesMidFlight() = runBlocking {
        val s = FakeStore(); val d = mutableListOf<String>(); val url: suspend () -> String = { "https://h/f" }
        assertEquals(EditResult.Miss, resolver(s, true, d).beginEdit(ref, "s1", EditKind.VIDEO_MP4, 1000))
        assertTrue(resolver(s, true, d).beginEdit(ref, "s1", EditKind.VIDEO_MP4, 1000, url) is EditResult.Started)
        assertEquals(1, d.size)
        val s2 = FakeStore()
        assertTrue(resolver(s2, true, onDownload = { trusted = TrustedAttachment("image", true, false) }).beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000, url) is EditResult.Denied)
        assertEquals(0, s2.begins)
    }

    @Test fun commitRechecksSessionAndPolicyAndAbortsOwnedFilesOnly() = runBlocking {
        trusted = TrustedAttachment("image", false, false)
        val s = FakeStore(); s.data[s.k(ref)] = "orig".toByteArray(); val r = resolver(s, false)
        val sess = (r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) as EditResult.Started).session
        trusted = TrustedAttachment("image", true, false)                         // became view-once while editing
        assertTrue(r.commitEdit(sess, ref, "s1", ExportPurpose.SHARE, 1000) is ExportResult.Denied)
        assertEquals(0, s.commits); assertEquals(1, s.aborts); assertFalse(sess.outputFile.exists())
        trusted = TrustedAttachment("image", false, false)
        val sess2 = (r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) as EditResult.Started).session
        s.seq++                                                                   // account switch
        assertTrue(r.commitEdit(sess2, ref, "s1", ExportPurpose.SHARE, 1000) is ExportResult.Denied)
        assertEquals(0, s.commits)
    }

    private fun commitRace(afterGate: (FakeStore) -> Unit): Triple<FakeStore, ExportResult?, Throwable?> {
        trusted = TrustedAttachment("image", false, false)
        val s = FakeStore(); s.data[s.k(ref)] = "orig".toByteArray(); s.commitGate = java.util.concurrent.CountDownLatch(1)
        val r = resolver(s, false)
        val sess = runBlocking { (r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) as EditResult.Started).session }
        var result: ExportResult? = null; var err: Throwable? = null
        runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                try { result = r.commitEdit(sess, ref, "s1", ExportPurpose.SHARE, 1000) } catch (e: Throwable) { err = e; throw e }
            }
            s.commitEntered.await(); afterGate(s); s.commitGate!!.countDown(); job.join()
        }
        return Triple(s, result, err)
    }

    @Test fun cancelWhileStoreMintsStillReleasesTheTokenExactlyOnceAndKeepsOriginal() {
        var jobRef: kotlinx.coroutines.Job? = null
        val (s, res, err) = run {
            trusted = TrustedAttachment("image", false, false)
            val s = FakeStore(); s.data[s.k(ref)] = "orig".toByteArray(); s.commitGate = java.util.concurrent.CountDownLatch(1)
            val r = resolver(s, false)
            val sess = runBlocking { (r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000) as EditResult.Started).session }
            var result: ExportResult? = null; var err: Throwable? = null
            runBlocking {
                val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                    try { result = r.commitEdit(sess, ref, "s1", ExportPurpose.SHARE, 1000) } catch (e: Throwable) { err = e; throw e }
                }
                jobRef = job
                s.commitEntered.await(); job.cancel(); s.commitGate!!.countDown(); job.join()
            }
            Triple(s, result, err)
        }
        assertTrue(err is kotlinx.coroutines.CancellationException); assertNull(res)
        assertEquals(1, s.mintedReleases.get()); assertFalse(s.minted!!.exists())   // caller never saw the token, still released once
        assertArrayEquals("orig".toByteArray(), s.data[s.k(ref)])                  // original entry unchanged
    }

    @Test fun policyChangeDuringStoreCommitReleasesTheMintedTokenAndDenies() {
        val (s, res, _) = commitRace { trusted = TrustedAttachment("image", true, false) }
        assertTrue(res is ExportResult.Denied)
        assertEquals(1, s.mintedReleases.get()); assertFalse(s.minted!!.exists())
        assertArrayEquals("orig".toByteArray(), s.data[s.k(ref)])
    }

    @Test fun normalCommitTransfersOwnershipWithoutRelease() {
        val (s, res, _) = commitRace { }
        assertTrue(res is ExportResult.Granted)
        assertEquals(0, s.mintedReleases.get()); assertTrue(s.minted!!.exists())
        (res as ExportResult.Granted).token.release(); assertEquals(1, s.mintedReleases.get())
    }

    @Test fun accountSwitchDuringDownloadWritesNothingAndIsNeverServed() = runBlocking {
        trusted = TrustedAttachment("image", false, false)
        val s = FakeStore()
        val r = resolver(s, true, onDownload = { s.seq++ })          // account switches while the download is in flight
        val out = r.resolve(req())
        assertTrue(out is ResolvedSource.Unavailable)
        assertTrue(s.data.isEmpty())                                // old-account bytes never reached the store
    }

    @Test fun accountSwitchDuringExplicitDownloadForAnEditWritesNothing() = runBlocking {
        trusted = TrustedAttachment("image", false, false)
        val s = FakeStore()
        val r = resolver(s, true, onDownload = { s.seq++ })
        val res = r.beginEdit(ref, "s1", EditKind.IMAGE_JPEG, 1000, { "https://h/f" })
        assertFalse(res is EditResult.Started); assertTrue(s.data.isEmpty()); assertEquals(0, s.begins)
    }

    @Test fun staleExpectedSessionIsRefusedBeforeAnyUrlIsSignedOrNetworkUsed() = runBlocking {
        trusted = TrustedAttachment("image", false, false)
        val s = FakeStore(); s.seq = 2                                  // the store is already in a newer session
        val downloads = mutableListOf<String>()
        val out = resolver(s, true, downloads).resolve(req(session = "s1"))
        assertEquals(ResolvedSource.Unavailable(Reason.SIGNED_OUT), out)
        assertTrue(signed.isEmpty()); assertTrue(downloads.isEmpty()); assertTrue(s.data.isEmpty())
    }

    @Test fun pureEpochCheckFollowsTheInstalledStampWithoutTouchingTheStore() {
        val s = FakeStore(); val r = resolver(s, true)
        assertTrue(r.isEpochCurrent("s1")); assertFalse(r.isEpochCurrent("s0"))
        s.seq++; assertFalse(r.isEpochCurrent("s1")); assertTrue(r.isEpochCurrent("s2"))
        s.closed = true; assertFalse(r.isEpochCurrent("s2"))
        assertEquals(5, s.pureChecks)
    }
}
