package com.example.chat.data.local

import java.util.concurrent.atomic.AtomicReference

/**
 * One atomic (tenant, user, seq, gen) snapshot for the cache. Every transition (tenant/user change, logout,
 * clear/delete) installs a NEW immutable snapshot with a compare-and-set, so concurrent threads can never observe
 * a half-updated pair and a stamp is never reused. Operations capture a [Snap] at the start and call [valid] before
 * they hand anything out or publish; A -> B -> A and clear + re-login of the SAME user both change the stamp.
 * Pure (no Android), JVM-tested. [onTransition] runs AFTER the new snapshot is installed and OUTSIDE any lock here.
 */
class SessionEpoch(private val identity: () -> Identity?) {
    /** ONE atomic read of who is signed in on the message plane. [authGeneration] must change on every restore,
     *  adopt and sign-out of that plane, including a re-login with the same user and token. */
    data class Identity(val tenant: String, val uid: String, val authGeneration: Long)

    class Snap(val tenant: String?, val uid: String?, val authGeneration: Long, val seq: Long, val gen: Long) {
        val stamp: String get() = "s$seq.$gen"
    }

    private companion object { const val MAX_SPINS = 64 }

    private val ref = AtomicReference(Snap(null, null, 0, 0, 0))

    /**
     * Returns a snapshot that matched a FRESH identity read made after it was installed. The identity is re-read on
     * every iteration: a thread that sampled an old identity, lost the race, and then won a CAS over a NEWER
     * snapshot would otherwise regress to the stale identity. After any install we loop and re-verify, so the
     * returned snapshot always agrees with the identity as read at that moment (a stale snapshot installed in the
     * meantime only costs an extra seq bump, which invalidates anything captured on it).
     */
    fun current(onTransition: () -> Unit = {}): Snap {
        var spins = 0
        while (true) {
            val id = identity()?.takeIf { it.tenant.isNotEmpty() && it.uid.isNotEmpty() }
            val cur = ref.get()
            if (cur.tenant == id?.tenant && cur.uid == id?.uid && (id == null || cur.authGeneration == id.authGeneration)) return cur
            if (++spins > MAX_SPINS) {
                // The identity will not hold still: fail CLOSED with a signed-out snapshot instead of spinning.
                val closed = Snap(null, null, 0, cur.seq + 1, cur.gen + 1)
                if (ref.compareAndSet(cur, closed)) { onTransition(); return closed }
                continue
            }
            if (ref.compareAndSet(cur, Snap(id?.tenant, id?.uid, id?.authGeneration ?: 0, cur.seq + 1, cur.gen + 1))) onTransition()
        }
    }

    /** Clear/delete: same user and tenant, but everything handed out earlier becomes invalid. */
    fun bump(): Snap = ref.updateAndGet { Snap(it.tenant, it.uid, it.authGeneration, it.seq, it.gen + 1) }

    /** Session end without an identity change (logout + same-user login, process start): new seq and gen. */
    fun renew(): Snap = ref.updateAndGet { Snap(it.tenant, it.uid, it.authGeneration, it.seq + 1, it.gen + 1) }

    /** Lock-free: compares with the LAST INSTALLED snapshot and never reads the identity source. Use it while holding
     *  a lock (the identity source may take its own lock, e.g. the auth scope lock: calling [valid] under a cache lock
     *  would invert the lock order). Sound for publish checks because every transition installs first, then purges. */
    /** Pure read of the last installed snapshot: one AtomicReference.get, no identity lookup, no callback, no IO. */
    fun peek(): Snap = ref.get()

    fun stillCurrent(s: Snap): Boolean = ref.get().stamp == s.stamp

    fun valid(s: Snap, onTransition: () -> Unit = {}): Boolean = current(onTransition).stamp == s.stamp
}
