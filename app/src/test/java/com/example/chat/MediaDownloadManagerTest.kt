package com.example.chat

import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.local.AttachmentStore
import com.example.chat.data.local.EditKind
import com.example.chat.data.local.EditResult
import com.example.chat.data.local.EditSession
import com.example.chat.data.local.ExportPurpose
import com.example.chat.data.local.ExportResult
import com.example.chat.data.local.LeasedFile
import com.example.chat.data.local.StorePut
import com.example.chat.data.local.StoreRead
import com.example.chat.data.media.AttachmentSourceResolver
import com.example.chat.data.media.MediaDownloadManager
import com.example.chat.data.media.MediaDownloadState as S
import com.example.chat.data.media.PartDownloader
import com.example.chat.data.media.ResumableMediaDownloader
import com.example.chat.data.media.TrustedAttachment
import java.io.File
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Manager-level behavior with a controllable downloader: one writer per part, no lost restart, purge ordering. */
class MediaDownloadManagerTest {
    private class FakeStore : AttachmentStore {
        val stored = CopyOnWriteArrayList<String>()
        override fun session(): String? = "s1"
        override fun isOpen() = true
        override fun isEpochCurrent(expected: String) = expected == "s1"
        override fun put(ref: AttachmentRef, source: InputStream, viewOnce: Boolean, restricted: Boolean, expectedSession: String): StorePut {
            if (expectedSession != "s1") return StorePut.Closed
            stored += ref.attachmentId + ":" + source.readBytes().size; return StorePut.Stored
        }
        override fun readVerified(ref: AttachmentRef, maxBytes: Int): StoreRead<ByteArray> = StoreRead.Miss
        override fun lease(ref: AttachmentRef): StoreRead<LeasedFile> = StoreRead.Miss
        override fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String, viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): ExportResult = ExportResult.Miss
        override fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): EditResult = EditResult.Miss
        override fun commitEdit(session: EditSession, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult = ExportResult.Failed
        override fun abortEdit(session: EditSession) {}
        override fun invalidate(ref: AttachmentRef) {}
    }

    /** Writes a marker file under the part path, then waits for a token. Tracks concurrent writers. */
    private class FakeDownloader : PartDownloader {
        val tokens = Channel<Unit>(Channel.UNLIMITED)
        val started = AtomicInteger(); val active = AtomicInteger(); val maxActive = AtomicInteger()
        val discardedWhileActive = AtomicInteger()
        @Volatile var ignoreCancelMs = 0L
        val markerSeenAtStart = CopyOnWriteArrayList<Boolean>()
        @Volatile var partsRoot: File? = null
        override suspend fun download(url: String, part: File, isCurrent: () -> Boolean, onProgress: (Long, Long?, Long) -> Unit): ResumableMediaDownloader.Result {
            markerSeenAtStart += (partsRoot?.let { File(it, "marker").exists() } ?: false)
            started.incrementAndGet()
            val now = active.incrementAndGet(); maxActive.updateAndGet { maxOf(it, now) }
            try {
                part.parentFile?.mkdirs(); part.writeText("data")
                onProgress(4, 8, 1)
                if (ignoreCancelMs > 0) { val ms = ignoreCancelMs; ignoreCancelMs = 0; withContext(NonCancellable) { delay(ms) } }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()   // a cancelled run must not consume a token
                tokens.receive()
                return ResumableMediaDownloader.Result(part, 8, "\"e\"")
            } finally { active.decrementAndGet() }
        }
        override fun discard(part: File) { if (active.get() > 0) discardedWhileActive.incrementAndGet(); part.delete() }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var root: File
    private lateinit var store: FakeStore
    private lateinit var fake: FakeDownloader
    private lateinit var online: MutableStateFlow<Boolean>
    private var trusted: TrustedAttachment? = TrustedAttachment("image", false, false)
    private lateinit var mgr: MediaDownloadManager

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        root = File.createTempFile("parts", "d").apply { delete(); mkdirs() }
        store = FakeStore(); fake = FakeDownloader().also { it.partsRoot = root }; online = MutableStateFlow(true)
        trusted = TrustedAttachment("image", false, false)
        val resolver = AttachmentSourceResolver(store, { true }, { trusted }, { error("unused") })
        mgr = MediaDownloadManager(scope, resolver, fake, root, online)
    }
    @After fun tearDown() { scope.cancel(); root.deleteRecursively() }

    private fun req(att: String = "a1") = MediaDownloadManager.Request(
        AttachmentRef("c1", "m-$att", att), "s1", { "s1" }, { "https://x/$att" }, 8)

    private fun <T> block(f: suspend () -> T): T = runBlocking { withTimeout(10_000) { f() } }
    private suspend fun until(what: String, cond: () -> Boolean) { repeat(400) { if (cond()) return; delay(10) }; error("timeout: $what") }

    @Test fun `start twice and a network flap never run two writers on one part`() = block {
        val r = req(); val st = mgr.stateFlow(r)
        mgr.start(r); mgr.start(r)
        until("first run") { fake.started.get() == 1 }
        online.value = false; until("paused") { st.value is S.Paused }
        online.value = true                                  // quick flap while the first run is still unwinding
        until("second run") { fake.started.get() == 2 }
        mgr.start(r)                                          // already active: no-op
        fake.tokens.send(Unit)
        mgr.awaitIdle()
        assertEquals(1, fake.maxActive.get())
        assertEquals(S.Done, st.value)
        assertEquals(listOf("a1:4"), store.stored.toList())   // stored exactly once
        assertEquals(2, fake.started.get())
    }

    @Test fun `quick reconnect while the cancelled run is still unwinding still restarts`() = block {
        val r = req(); val st = mgr.stateFlow(r)
        fake.ignoreCancelMs = 300                              // old run keeps running ~300 ms after cancel
        mgr.start(r)
        until("run") { fake.started.get() == 1 }
        online.value = false
        until("paused") { st.value is S.Paused }
        online.value = true                                    // old job is still active here
        fake.tokens.send(Unit)
        mgr.awaitIdle()
        assertEquals(S.Done, st.value)                         // previously stayed Queued forever
        assertEquals(1, fake.maxActive.get())
    }

    @Test fun `user pause stays paused across network back and resumes only on tap`() = block {
        val r = req(); val st = mgr.stateFlow(r)
        mgr.start(r); until("run") { fake.started.get() == 1 }
        mgr.pause(r); mgr.awaitIdle()
        online.value = false; online.value = true; delay(150); mgr.awaitIdle()
        assertTrue(st.value is S.Paused); assertEquals(1, fake.started.get())
        mgr.start(r); until("second run") { fake.started.get() == 2 }
        fake.tokens.send(Unit); mgr.awaitIdle()
        assertEquals(S.Done, st.value)
    }

    @Test fun `cancel cleanup waits for the writer and a later start queues behind it`() = block {
        val r = req(); val st = mgr.stateFlow(r)
        fake.ignoreCancelMs = 200
        mgr.start(r); until("run") { fake.started.get() == 1 }
        mgr.cancel(r)
        mgr.start(r)                                           // immediately again
        until("second run") { fake.started.get() == 2 }
        fake.tokens.send(Unit); mgr.awaitIdle()
        assertEquals(0, fake.discardedWhileActive.get())       // never deleted under a live writer
        assertEquals(1, fake.maxActive.get())
        assertEquals(S.Done, st.value)
    }

    @Test fun `purgeAll finishes deleting before a new download touches disk`() = block {
        File(root, "marker").writeText("old")                  // stands for the previous session's parts
        val a = req("a1"); mgr.start(a); until("a run") { fake.started.get() == 1 }
        mgr.purgeAll()
        val b = req("b1"); val stB = mgr.stateFlow(b)
        mgr.start(b)                                           // issued right after the purge
        fake.tokens.send(Unit)
        mgr.awaitIdle()
        assertFalse(File(root, "marker").exists())
        assertEquals(listOf(false), fake.markerSeenAtStart.drop(1))   // B's writer saw the purge already done
        assertEquals(S.Done, stB.value)
        assertEquals(1, fake.maxActive.get())
    }

    @Test fun `view-once or restricted media is refused and never downloaded`() = block {
        trusted = TrustedAttachment("image", true, false)
        val r = req(); mgr.start(r)
        mgr.awaitIdle()
        assertEquals(S.Failed(0, 8, S.FailReason.NOT_ALLOWED), mgr.stateFlow(r).value)
        assertEquals(0, fake.started.get())
        trusted = null
        val r2 = req("a2"); mgr.start(r2)
        assertEquals(S.Failed(0, 8, S.FailReason.NOT_ALLOWED), mgr.stateFlow(r2).value)
    }

    @Test fun `offline start waits and begins by itself when the network returns`() = block {
        online.value = false; delay(50)
        val r = req(); val st = mgr.stateFlow(r)
        mgr.start(r)
        assertEquals(S.Paused(0, 8, S.PauseReason.NO_NETWORK), st.value)
        assertEquals(0, fake.started.get())
        online.value = true
        until("run") { fake.started.get() == 1 }
        fake.tokens.send(Unit); mgr.awaitIdle()
        assertEquals(S.Done, st.value)
    }
}
