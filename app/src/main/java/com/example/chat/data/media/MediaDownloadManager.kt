package com.example.chat.data.media

import com.example.chat.data.local.AttachmentRef
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns tap-to-download for media sent by other people. One entry per account-session + attachment.
 *  - view-once / restricted / unknown attachments are refused here and stay on the existing Remote-only path;
 *  - bytes go to an account-scoped private .part file (noBackupFilesDir), then through
 *    [AttachmentSourceResolver.ingestPart] (same session + policy guards, encrypted store) and the part is deleted;
 *  - network loss pauses (part kept) and resumes by itself; a user pause stays paused;
 *  - a changed session or a new restriction mid-flight discards the part and stops.
 *
 * Concurrency model (one rule): every mutation of entries/jobs happens under [lock], and every piece of work that
 * touches a part file (download, cancel cleanup, purge) is a job that first JOINS the previous job of that entry
 * (and the latest purge). So there is never more than one writer per part file, a restart requested while the old
 * run is still cancelling simply queues behind it (it never gets lost), and a purge finishes before any later
 * download touches disk. A run only proceeds if it is still the latest claim and the entry is still Queued.
 */
class MediaDownloadManager(
    private val scope: CoroutineScope,
    private val resolver: AttachmentSourceResolver,
    private val downloader: PartDownloader,
    private val partsRoot: File,
    private val online: StateFlow<Boolean>,
) {
    class Request(
        val ref: AttachmentRef,
        val session: String,
        val currentSession: () -> String?,
        val authorizedUrl: suspend () -> String,
        val totalBytes: Long?,
    )

    private class Entry(val req: Request, val state: MutableStateFlow<MediaDownloadState>) {
        var job: Job? = null       // guarded by lock: the latest job for this entry (may still be cancelling)
        var claimGen = 0           // guarded by lock: bumped for every queued unit of work, older units drop out
        var cancelGen = 0          // guarded by lock: bumped whenever WE cancel, so a run can tell its own failure apart
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()           // guarded by lock
    private var purgeJob: Job? = null                         // guarded by lock

    init {
        scope.launch {
            online.collect { up ->
                synchronized(lock) {
                    entries.values.toList().forEach { e ->
                        if (!up) { dispatch(e, MediaDownloadEvent.NetworkLost); cancelRun(e) }
                        else {
                            val before = e.state.value
                            dispatch(e, MediaDownloadEvent.NetworkBack)
                            // Only a network pause resumes. The old run may still be cancelling: the new one queues behind it.
                            if (before is MediaDownloadState.Paused && e.state.value is MediaDownloadState.Queued) enqueueRun(e)
                        }
                    }
                }
            }
        }
    }

    private fun key(session: String, ref: AttachmentRef) = "$session|${ref.conversationId}|${ref.messageId}|${ref.attachmentId}"

    private fun entryFor(req: Request): Entry = entries.getOrPut(key(req.session, req.ref)) {
        Entry(req, MutableStateFlow(MediaDownloadState.Idle(req.totalBytes)))
    }

    fun stateFlow(req: Request): StateFlow<MediaDownloadState> = synchronized(lock) { entryFor(req).state }

    /** Tap, retry, resume or auto-download. No-op while already active or done. */
    fun start(req: Request) {
        val trusted = resolver.trusted(req.ref)
        synchronized(lock) {
            val e = entryFor(req)
            if (trusted == null || trusted.viewOnce || trusted.restricted) {
                e.state.value = MediaDownloadState.Failed(0, req.totalBytes, MediaDownloadState.FailReason.NOT_ALLOWED); return
            }
            if (e.state.value.isActive || e.state.value == MediaDownloadState.Done) return
            dispatch(e, MediaDownloadEvent.Start)
            if (!online.value) { dispatch(e, MediaDownloadEvent.NetworkLost); return }
            enqueueRun(e)
        }
    }

    fun pause(req: Request) = synchronized(lock) {
        entries[key(req.session, req.ref)]?.let { dispatch(it, MediaDownloadEvent.UserPause); cancelRun(it) }
        Unit
    }

    fun cancel(req: Request) = synchronized(lock) {
        val e = entries[key(req.session, req.ref)] ?: return@synchronized
        dispatch(e, MediaDownloadEvent.UserCancel)
        cancelRun(e)
        // The cleanup is queued behind the cancelling run so it cannot delete under a still-writing job,
        // and a later start() queues behind the cleanup.
        enqueue(e) { downloader.discard(partFile(e.req)) }
    }

    /** Logout / account switch: stop everything, then delete every partial file BEFORE any later download touches disk. */
    fun purgeAll() = synchronized(lock) {
        val snapshot = entries.values.toList()
        entries.clear()
        snapshot.forEach { cancelRun(it) }
        val jobs = snapshot.mapNotNull { it.job }
        val previousPurge = purgeJob
        purgeJob = scope.launch(Dispatchers.IO) {
            previousPurge?.join()
            jobs.forEach { it.join() }
            runCatching { partsRoot.deleteRecursively() }
        }
        Unit
    }

    /** Test seam: suspends until every queued run, cleanup and purge has finished. */
    internal suspend fun awaitIdle() {
        while (true) {
            val pending = synchronized(lock) { entries.values.mapNotNull { it.job } + listOfNotNull(purgeJob) }
            pending.forEach { it.join() }
            val busy = synchronized(lock) { (entries.values.mapNotNull { it.job } + listOfNotNull(purgeJob)).any { it.isActive } }
            if (!busy) return
        }
    }

    private fun dispatch(e: Entry, ev: MediaDownloadEvent) {
        while (true) {
            val cur = e.state.value
            if (e.state.compareAndSet(cur, MediaDownloadReducer.reduce(cur, ev))) return
        }
    }

    // Callers hold [lock].
    private fun cancelRun(e: Entry) { e.cancelGen++; e.job?.cancel() }

    // Callers hold [lock]. Queues [block] behind the entry's previous job and the latest purge.
    private fun enqueue(e: Entry, block: suspend () -> Unit) {
        val previous = e.job
        val purge = purgeJob
        e.claimGen++
        e.job = scope.launch(Dispatchers.IO) {
            previous?.join()
            purge?.join()
            block()
        }
    }

    // Callers hold [lock].
    private fun enqueueRun(e: Entry) {
        val claim = e.claimGen + 1       // the value enqueue() will assign
        enqueue(e) {
            val mine = synchronized(lock) { if (e.claimGen == claim && e.state.value is MediaDownloadState.Queued) e.cancelGen else null }
            if (mine != null) run(e, mine)
        }
    }

    private fun partFile(r: Request): File {
        fun h(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        return File(File(partsRoot, h(r.session)), h("${r.ref.conversationId}|${r.ref.messageId}|${r.ref.attachmentId}") + ".part")
    }

    private suspend fun run(e: Entry, cancelGenAtStart: Int) {
        val req = e.req
        val part = partFile(req)
        fun policyOk() = req.currentSession() == req.session &&
            resolver.trusted(req.ref)?.let { !it.viewOnce && !it.restricted } == true
        try {
            if (!policyOk()) throw ResumableMediaDownloader.DownloadFailure(MediaDownloadState.FailReason.NOT_ALLOWED)
            val url = req.authorizedUrl()
            if (!policyOk()) throw ResumableMediaDownloader.DownloadFailure(MediaDownloadState.FailReason.NOT_ALLOWED)
            val res = downloader.download(url, part, isCurrent = { policyOk() }) { done, total, bps ->
                dispatch(e, MediaDownloadEvent.Progress(done, total ?: req.totalBytes, bps))
            }
            val stored = resolver.ingestPart(req.ref, req.session, res.file)
            downloader.discard(part)
            when {
                stored == null -> dispatch(e, MediaDownloadEvent.Finished)
                stored.startsWith("denied") -> dispatch(e, MediaDownloadEvent.Error(MediaDownloadState.FailReason.NOT_ALLOWED))
                else -> dispatch(e, MediaDownloadEvent.Error(MediaDownloadState.FailReason.STORAGE))
            }
        } catch (c: CancellationException) {
            // If WE cancelled (pause, cancel, network loss, purge) cancelGen moved and the state is already right.
            // Otherwise the downloader stopped itself (session or policy changed mid-flight): drop the part and go idle.
            val ours = synchronized(lock) { e.cancelGen != cancelGenAtStart }
            if (!ours && e.state.value.isActive) { downloader.discard(part); dispatch(e, MediaDownloadEvent.UserCancel) }
            throw c
        } catch (f: ResumableMediaDownloader.DownloadFailure) {
            if (f.reason == MediaDownloadState.FailReason.NOT_ALLOWED || f.reason == MediaDownloadState.FailReason.TOO_LARGE) downloader.discard(part)
            dispatch(e, MediaDownloadEvent.Error(f.reason))
        } catch (_: IOException) {
            dispatch(e, if (online.value) MediaDownloadEvent.Error(MediaDownloadState.FailReason.NETWORK) else MediaDownloadEvent.NetworkLost)
        } catch (_: Exception) {
            dispatch(e, MediaDownloadEvent.Error(MediaDownloadState.FailReason.NETWORK))
        }
    }
}
