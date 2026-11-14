package com.example.chat.data.messaging

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One dispatch lock per (account identity, conversation). Every path that
 *  can put bytes for a conversation on the wire takes it: the REST outbox
 *  flush (ApiBackendService, per entry), MLS delivery
 *  (ConversationsRepository.deliverAndResolve, the single choke point for
 *  direct sends and queued retries), the secure-channel toggle
 *  (ConversationsRepository.setSecureChannel, held for the whole toggle,
 *  including its hold/release of queued REST sends), and upload
 *  registration (AttachmentUploadTracker.begin, briefly).
 *
 *  INVARIANTS (what the lock establishes - and what it does not):
 *   1. For one (account, conversation) key, at most one of {REST dispatch,
 *      MLS dispatch, toggle check+hold+write, upload registration} runs at a
 *      time. The toggle's in-flight check, queue hold and flag write happen
 *      inside the lock, so when the check passes, no dispatch for that
 *      conversation is in flight on THIS device/process.
 *   2. Lock order: secureChannelMutex (flag store) BEFORE a dispatch lock;
 *      dispatch paths never take the flag mutex (the upload tracker's
 *      in-lock flag re-read uses a non-suspending loaded-state read for
 *      exactly this reason). flushMutex is the outermost REST-plane lock
 *      (flush -> dispatch, per entry); NOTHING may acquire flushMutex while
 *      holding a dispatch lock (that would invert and deadlock). The queue
 *      hold/release therefore runs WITHOUT flushMutex: its exclusion of
 *      in-flight delivery comes from the caller-held dispatch lock plus the
 *      flush loop re-checking `held` under that same lock before each
 *      delivery. outboxLock is always the innermost lock.
 *   3. NOT covered: another device/process (the lock is process-local),
 *      process death mid-dispatch (the outbox/queue re-drives on restart and
 *      re-reads the flag then), an upload that never registered with
 *      AttachmentUploadTracker (its send port still fails closed on the
 *      flag), and the server side (a plaintext send already accepted by the
 *      server before the toggle stays sent).
 *   4. The Mutex serializes; it does not by itself prove atomicity of the
 *      toggle. The guarantee comes from every dispatch AND the toggle
 *      holding the same key - enumerated above, not assumed. */
class ConversationDispatchLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(
        accountKey: String,
        conversationId: String,
        block: suspend () -> T,
    ): T {
        val mutex = locks.getOrPut(keyOf(accountKey, conversationId)) { Mutex() }
        return mutex.withLock { block() }
    }

    companion object {
        /** Full account+tenant+backend identity for the dispatch-lock key.
         *  Each segment is length-prefixed, so segments containing any
         *  separator character stay unambiguous. */
        fun accountKey(userId: String?, tenantId: String?, baseUrl: String?): String {
            fun seg(s: String?): String {
                val v = s ?: ""
                return "${v.length}:$v"
            }
            return seg(userId) + "|" + seg(tenantId) + "|" + seg(baseUrl)
        }

        fun keyOf(accountKey: String, conversationId: String): String =
            "$accountKey|${conversationId.length}:$conversationId"
    }
}
