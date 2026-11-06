package com.example.chat.ui

import com.example.chat.data.api.ApiAccountScope
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opaque gallery lease issued at the ORIGINAL paperclip open. Single use; scope is internal. */
class GalleryLease internal constructor(internal val scope: ApiAccountScope) {
    internal val valid = AtomicBoolean(true)
    val isValid: Boolean get() = valid.get()
}

/** One running attach flow. Identity (reference) is what queued cleanup is matched against. */
class AttachFlow internal constructor(internal val scope: ApiAccountScope, internal val onCancel: (() -> Unit)? = null) {
    internal val cancelled = AtomicBoolean(false)
    /** The immutable scope this session started under. Pass as expectedScope / ApiScopeElement for upload and send. */
    val expectedScope: ApiAccountScope get() = scope
    val isCancelled: Boolean get() = cancelled.get()
}

/** Pure (no Android/Compose). `post` MUST enqueue and never run inline (Android: Handler(mainLooper).post). */
class GalleryLeaseRegistry(private val post: (() -> Unit) -> Unit) {
    private val leases = CopyOnWriteArrayList<GalleryLease>()
    private val active = AtomicReference<AttachFlow?>(null)
    private val sessions = CopyOnWriteArrayList<AttachFlow>()
    private val epoch = AtomicInteger(0)
    private val published = AtomicInteger(0)

    /** Re-check after registration closes the add-vs-transition race. */
    fun issue(scope: ApiAccountScope, currentScope: () -> ApiAccountScope): GalleryLease =
        GalleryLease(scope).also { leases.add(it); if (currentScope() !== scope) it.valid.set(false) }

    /** Dismiss/close: the lease can never be used again, even by an already queued callback. */
    fun release(l: GalleryLease) { l.valid.set(false); leases.remove(l) }

    /** Start a flow. Null when one is already running (never overwrite) or the lease is dead. A lease is consumed. */
    fun begin(scope: ApiAccountScope, lease: GalleryLease? = null, currentScope: () -> ApiAccountScope): AttachFlow? {
        if (lease != null && !lease.valid.compareAndSet(true, false)) return null
        leases.remove(lease)
        val f = AttachFlow(scope)
        if (!active.compareAndSet(null, f)) return null
        // A transition between capture and registration is invisible to onTransition: re-check AFTER publishing the flow.
        if (currentScope() !== scope) f.cancelled.set(true)
        return f
    }

    /** Non-exclusive capture session (voice record, camera video): open BEFORE hardware/permission, at the original start.
     *  Atomically cancelled on any transition; `onCancel` is posted to the main queue (never under the lock), only if the
     *  session is still open, so the owner can discard/stop. Finish with `isCancelled` check, then upload/send with
     *  ApiScopeElement(expectedScope) / expectedScope, then closeSession. */
    fun openSession(scope: ApiAccountScope, currentScope: () -> ApiAccountScope, onCancel: (() -> Unit)? = null): AttachFlow {
        val f = AttachFlow(scope, onCancel); sessions.add(f)
        if (currentScope() !== scope) { f.cancelled.set(true); post { if (sessions.contains(f)) onCancel?.invoke() } }
        return f
    }
    fun closeSession(f: AttachFlow) { sessions.remove(f) }

    /** Conditional on identity: ending an old flow never clears a newer one. */
    fun end(f: AttachFlow) { active.compareAndSet(f, null) }

    /** Under the transition lock: atomics + one enqueue. Queued work is bound to the captured flow identity and a monotonic epoch. */
    fun onTransition(next: ApiAccountScope, publishEpoch: (Int) -> Unit, cancelWaiters: (AttachFlow) -> Unit) {
        for (l in leases) if (l.scope !== next) l.valid.set(false)
        val f = active.get()?.takeIf { it.scope !== next }
        f?.cancelled?.set(true)
        val stale = sessions.filter { it.scope !== next }
        for (x in stale) x.cancelled.set(true)
        val n = epoch.incrementAndGet()
        post {
            for (x in stale) if (sessions.contains(x)) x.onCancel?.invoke()   // conditional on identity: closed sessions are skipped
            var p = published.get()
            while (n > p && !published.compareAndSet(p, n)) p = published.get()
            if (n > p) publishEpoch(n)
            if (f != null && active.get() === f) cancelWaiters(f)   // a later flow B is never touched by A's delayed event
        }
    }
}

/** Coroutine-context ownership: only code running inside withContext(AttachFlowElement(flow)) may use that flow's scope.
 *  Unrelated paths (camera, voice, dock) have no element, so they can never borrow another flow's scope. */
class AttachFlowElement(val flow: AttachFlow) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<AttachFlowElement>
}

/** Correlates activity-result callbacks with the request that is still waiting. Android delivers exactly one callback per launch,
 *  even after the flow that launched it was cancelled by a transition. `orphan()` records that a launched picker is abandoned,
 *  so its late result is dropped by `accept()` instead of fulfilling a LATER flow's waiter. */
class ResultGate {
    private val orphans = java.util.concurrent.atomic.AtomicInteger(0)
    fun orphan() { orphans.incrementAndGet() }
    /** True while an abandoned launch of THIS launcher has not yet called back: a new launch would be ambiguous, so refuse it. */
    fun hasOrphans(): Boolean = orphans.get() > 0
    fun accept(): Boolean {
        while (true) {
            val n = orphans.get()
            if (n <= 0) return true
            if (orphans.compareAndSet(n, n - 1)) return false
        }
    }
}
