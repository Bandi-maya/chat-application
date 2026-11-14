package com.example.chat.data.messaging

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Process-local count of attachment/voice uploads in flight, keyed by the
 *  full account identity + target conversation (uploads are
 *  conversation-agnostic at the network layer, so this is tracked where the
 *  target is known: the chat bindings).
 *
 *  Purpose: ConversationsRepository.setSecureChannel refuses to enable
 *  encrypted routing while an upload for that conversation is in flight
 *  (via the injected restSendsInFlight check).
 *
 *  Race closures (review):
 *  1. [begin] takes the conversation's dispatch lock to register. The toggle
 *     checks the count while HOLDING that same lock, so an upload either
 *     registers before the toggle's check (count > 0, the toggle refuses) or
 *     after the toggle completed - and in that second case the durable-flag
 *     re-read inside [begin] (below) refuses the upload.
 *  2. The flag consulted here is the repository's authoritative state
 *     (persisted before memory is swapped; strict loads), never a UI-state
 *     lambda: a UI lambda can be stale for a toggle that already completed.
 *
 *  Two reads, on purpose:
 *   - warmAndRead BEFORE the dispatch lock (suspending, may take the flag
 *     mutex; also loads the flag state if needed);
 *   - readLoaded INSIDE the dispatch lock (non-suspending, NEVER takes the
 *     flag mutex - the lock order is flag mutex -> dispatch lock, so taking
 *     it here would invert and deadlock the toggle).
 *  Either ordering of {this registration, the toggle's critical section} is
 *  then safe: registration first -> the toggle's count check refuses; toggle
 *  first -> readLoaded sees the new flag and throws. */
object AttachmentUploadTracker {

    /** Authoritative secure-channel flag access; wired once by ServiceLocator
     *  to ConversationsRepository.isSecureChannel (warm) and
     *  ConversationsRepository.isSecureChannelLoaded (fast). Null in tests:
     *  counts increment without flag enforcement. */
    class SecureFlagProbe(
        val warmAndRead: suspend (conversationId: String) -> Boolean,
        val readLoaded: (conversationId: String) -> Boolean,
    )

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** Shared with ServiceLocator/restSendsInFlight wiring; null = counts
     *  increment without the registration lock (tests). */
    @Volatile var dispatchLocks: ConversationDispatchLocks? = null

    @Volatile var secureFlagProbe: SecureFlagProbe? = null

    /** Marks one upload started for (accountKey, conversationId); returns the
     *  end hook. The hook is idempotent (AtomicBoolean) and MUST be called
     *  (use try/finally). Throws - nothing registered - when the
     *  conversation's durable flag already requests encrypted routing. */
    suspend fun begin(accountKey: String, conversationId: String): () -> Unit {
        val probe = secureFlagProbe
        // Fast-path refusal on the durable flag, BEFORE suspending on the
        // dispatch lock (a UI lambda may be stale; this one is not).
        if (probe != null && probe.warmAndRead(conversationId)) {
            throw IllegalStateException("This chat uses encrypted routing; media upload refused.")
        }
        val key = ConversationDispatchLocks.keyOf(accountKey, conversationId)
        val counter = counts.getOrPut(key) { AtomicInteger(0) }
        val locks = dispatchLocks
        if (locks != null) {
            locks.withLock(accountKey, conversationId) {
                counter.incrementAndGet()
                try {
                    // A toggle that completed between the warm read and this
                    // registration is caught here (see class doc, race 1).
                    if (probe != null && probe.readLoaded(conversationId)) {
                        throw IllegalStateException("Encryption was enabled while the upload was starting; nothing was uploaded.")
                    }
                } catch (t: Throwable) {
                    // Any throw after the increment (refusal OR an unreadable
                    // flag) must not leak the count: the toggle's in-flight
                    // check would otherwise refuse forever.
                    counter.decrementAndGet()
                    throw t
                }
            }
        } else {
            counter.incrementAndGet()
            try {
                if (probe != null && probe.readLoaded(conversationId)) {
                    throw IllegalStateException("This chat uses encrypted routing; media upload refused.")
                }
            } catch (t: Throwable) {
                counter.decrementAndGet()
                throw t
            }
        }
        val done = AtomicBoolean(false)
        return {
            if (done.compareAndSet(false, true)) counter.decrementAndGet()
        }
    }

    fun hasInFlight(accountKey: String, conversationId: String): Boolean =
        (counts[ConversationDispatchLocks.keyOf(accountKey, conversationId)]?.get() ?: 0) > 0
}
