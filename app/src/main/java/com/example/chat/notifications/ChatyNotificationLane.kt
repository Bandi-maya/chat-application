package com.example.chat.notifications

import com.example.chat.data.api.ApiAccountScope

/**
 * CHATY NOTIFICATION LANE - implementation of API CONTRACT v3 FINAL (2026-10-27, vs candidate 26,
 * contract sha256 eea22481ef93cfad6a609f9b1469820a5c75265343b9e670c351b891adb55767).
 *
 * Every NotificationManager interaction is DEFERRED onto one serial FIFO lane thread and re-checked
 * against the LIVE account scope at execution time. Tags are canonical (m:<generation>:<conversationId>,
 * c:<generation>:<callId>), so a stale account's ops can never post or cancel another account's alerts,
 * and old-process/orphan alerts are cleared on start. Cancel failure retains the tag (kind + generation)
 * and retries once per later drain; there is no timer, no auto-retry loop and no eventual-absence
 * guarantee (contract A5): if the sink keeps failing, a notification can stay visible.
 *
 * Lane-thread state ([records], [pendingCancel], [incomingTag]) is only touched on the executor thread;
 * [cleanupFlag] is the sole caller-thread field. Pure Kotlin: no Android types, JVM-testable.
 */

/** Submission seam: DEFERRED (never inline, even over a direct executor) + SERIAL FIFO.
 *  Production: ONE daemon thread created once in ChatyNotificationCoordinator.start(), process-lived,
 *  never per-transition, never shut down. Tests: manually pumped FIFO queue. */
fun interface SerialDeferredExecutor {
    fun executeSerial(command: Runnable)
}

/** Sole NotificationManager IPC surface, implemented once by the Coordinator as thin wrappers over
 *  ChatyNotificationPoster (context/style captured there). Invoked only on the lane thread, only with
 *  lane-derived tags. Pure-Kotlin types only, so the lane stays android-free. */
interface ChatyNotificationSink {
    fun postMessage(tag: String, title: String, body: String, whenEpochMs: Long, replyNonce: String?, dismissNonce: String?)
    fun cancelMessage(tag: String)
    fun postIncomingCall(tag: String, callerName: String, isVideo: Boolean, declineNonce: String?)
    fun cancelIncomingCall(tag: String)
    /** Currently-active notification tags owned by this app, filtered by the Coordinator impl to this
     *  app's m:/c:-prefixed tags on the v1 message/call channels ONLY. The foreground-service
     *  notification (CallForegroundService SERVICE_ID=7401) is lifecycle-bound and MUST NOT appear here
     *  or be cancelled by reconcile. */
    fun activeTags(): List<String>
}

class ChatyNotificationLane(
    private val currentScope: () -> ApiAccountScope?,
    private val liveCallId: () -> String?,
    private val registry: ChatyActionRegistry<ApiAccountScope>,
    private val sink: ChatyNotificationSink,
    private val executor: SerialDeferredExecutor,
) {
    private enum class Kind { MESSAGE, CALL }

    private class Record(val kind: Kind, val generation: Long)

    // Lane-thread state.
    private val records = LinkedHashMap<String, Record>()
    private val pendingCancel = LinkedHashSet<String>()
    private var incomingTag: String? = null

    // Caller-thread state: cleanup coalescing flag. Set at the caller-thread attempt, cleared on the
    // lane thread at drain start; reset inline if executeSerial throws, BEFORE any drop handling.
    private val cleanupFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Canonical tags, internal + test-visible. ONLY these two functions mint notification tags. */
    internal fun messageTag(owner: ApiAccountScope, conversationId: String): String = "m:${owner.generation}:$conversationId"
    internal fun callTag(owner: ApiAccountScope, callId: String): String = "c:${owner.generation}:$callId"

    /** A1 admission predicate (exact): reference-identical live scope with non-blank userId/token. */
    private fun admitted(owner: ApiAccountScope): Boolean =
        owner === currentScope() && !owner.userId.isNullOrBlank() && !owner.token.isNullOrBlank()

    /** R5: every drop path runs registry.dropNonce inside runCatching; idempotent on absent nonces (P5). */
    private fun dropNonces(vararg nonces: String?) {
        runCatching { registry.dropNonce(*nonces) }
    }

    /** Deferred, never-throwing submission. On executor rejection the op's registry nonces are released. */
    private fun submit(vararg dropOnReject: String?, op: () -> Unit) {
        val accepted = runCatching { executor.executeSerial(Runnable { op() }) }.isSuccess
        if (!accepted) dropNonces(*dropOnReject)
    }

    /** A3 retain-until-success cancel: success removes tag/kind/generation/pending mark; a throw keeps
     *  the tracked record and marks it pendingCancel. Unknown/foreign tags are never touched. */
    private fun cancelTracked(tag: String) {
        val kind = records[tag]?.kind ?: kindFromPrefix(tag) ?: return
        val ok = runCatching {
            when (kind) {
                Kind.MESSAGE -> sink.cancelMessage(tag)
                Kind.CALL -> sink.cancelIncomingCall(tag)
            }
        }.isSuccess
        if (ok) {
            records.remove(tag)
            pendingCancel.remove(tag)
            if (incomingTag == tag) incomingTag = null
        } else if (records.containsKey(tag)) {
            pendingCancel += tag
        }
    }

    private fun kindFromPrefix(tag: String): Kind? = when {
        tag.startsWith("m:") -> Kind.MESSAGE
        tag.startsWith("c:") -> Kind.CALL
        else -> null
    }

    fun postMessage(owner: ApiAccountScope, conversationId: String, title: String, body: String,
        whenEpochMs: Long, replyNonce: String?, dismissNonce: String?) {
        submit(replyNonce, dismissNonce) {
            if (!admitted(owner)) { // A1: admission FIRST on every post
                dropNonces(replyNonce, dismissNonce)
                return@submit
            }
            val tag = messageTag(owner, conversationId)
            records[tag] = Record(Kind.MESSAGE, owner.generation) // P2: recorded BEFORE the sink call
            pendingCancel.remove(tag) // A4: a later admitted post with the same tag clears its pending mark
            runCatching { sink.postMessage(tag, title, body, whenEpochMs, replyNonce, dismissNonce) }
            // P2: on throw, tag + nonces REMAIN (a partial post stays cancellable); no retry.
        }
    }

    fun postIncomingCall(owner: ApiAccountScope, callId: String, callerName: String, isVideo: Boolean,
        declineNonce: String?) {
        submit(declineNonce) {
            if (!admitted(owner) || liveCallId() != callId) { // A1: full admission incl. live call, FIRST
                dropNonces(declineNonce) // no sink call, no tag recorded; stored tag untouched
                return@submit
            }
            val tag = callTag(owner, callId)
            val stored = incomingTag
            if (stored == tag) {
                // Same-tag quiet update (reachable ONLY after admission): content refresh, no cancel.
                records[tag] = Record(Kind.CALL, owner.generation)
                pendingCancel.remove(tag)
                runCatching { sink.postIncomingCall(tag, callerName, isVideo, declineNonce) }
            } else {
                // A6 replacement: old tag cancelled through A3; on throw it stays tracked + pending and
                // the new call is still posted. Old-owner cleanup can never cancel the new tag.
                if (stored != null) cancelTracked(stored)
                records[tag] = Record(Kind.CALL, owner.generation) // P2: recorded BEFORE the sink call
                pendingCancel.remove(tag)
                incomingTag = tag
                runCatching { sink.postIncomingCall(tag, callerName, isVideo, declineNonce) }
            }
        }
    }

    /** Current-conversation dismissal: the tag derives from the SUPPLIED owner (the Coordinator passes
     *  its fresh authSnapshot), so it can only cancel that owner's tag for that conversation. A stale
     *  or unknown tag is a no-op: no sink call, nothing else touched. */
    fun cancelMessageFor(owner: ApiAccountScope, conversationId: String) {
        submit {
            val tag = messageTag(owner, conversationId)
            if (records.containsKey(tag)) cancelTracked(tag)
        }
    }

    /** A3 twin for calls; clears the stored incoming tag iff it matches and the cancel succeeds. */
    fun cancelCallFor(owner: ApiAccountScope, callId: String) {
        submit {
            val tag = callTag(owner, callId)
            if (records.containsKey(tag)) cancelTracked(tag)
        }
    }

    /** Enqueue-only (auth-lock-safe), coalesced, idempotent. On the lane thread: cancel ALL stored tags
     *  when signed out / blank userId or token, else every stored tag whose generation is non-current;
     *  PLUS one retry of every pendingCancel tag regardless of generation (a failed dismissal of the
     *  CURRENT owner is retried too). Max one attempt per tag per drain; no retry loop, no timer. */
    fun cleanupNonCurrent() {
        if (!cleanupFlag.compareAndSet(false, true)) return
        val accepted = runCatching {
            executor.executeSerial(Runnable {
                cleanupFlag.set(false) // cleared on the lane thread at drain start
                val cur = currentScope()
                val blank = cur == null || cur.userId.isNullOrBlank() || cur.token.isNullOrBlank()
                val targets = LinkedHashSet<String>()
                for ((tag, rec) in records) if (blank || rec.generation != cur!!.generation) targets += tag
                targets += pendingCancel // A4: map membership does NOT exempt a pending record
                for (tag in targets) cancelTracked(tag)
            })
        }.isSuccess
        if (!accepted) cleanupFlag.set(false) // reset inline BEFORE any drop handling: never wedged
    }

    /** Enqueue-only; called ONCE from Coordinator.start(). On the lane thread: retry each pendingCancel
     *  tag once (A4), then cancel every sink.activeTags() entry not in the lane's tag map (the lane map
     *  is empty after process death: clears old-process alerts with dead nonces and prevents m:1:x-style
     *  generation collisions across processes). Drain union (pending + orphan), AT MOST ONE attempt per
     *  tag per drain: a tag already attempted as pending is skipped in the orphan pass even if the sink
     *  still reports it. Orphan cancels use the same retained variant: on throw the orphan is recorded
     *  with its exact tag/kind/generation (parsed from the tag) and marked pendingCancel, so the next
     *  drain retries it. Foreign (non-m:/c:-prefixed) tags are never touched. activeTags() itself is
     *  runCatching-wrapped like every sink call: on failure the orphan pass is skipped (pending retries
     *  already ran), the op does not throw, and the queue survives. */
    fun reconcileOnStart() {
        submit {
            val attempted = HashSet<String>()
            for (tag in pendingCancel.toList()) { attempted += tag; cancelTracked(tag) }
            val active = runCatching { sink.activeTags() }.getOrElse { emptyList() }
            for (tag in active) {
                if (tag in attempted) continue
                if (records.containsKey(tag)) continue
                val kind = kindFromPrefix(tag) ?: continue
                attempted += tag
                val ok = runCatching {
                    when (kind) {
                        Kind.MESSAGE -> sink.cancelMessage(tag)
                        Kind.CALL -> sink.cancelIncomingCall(tag)
                    }
                }.isSuccess
                if (!ok) {
                    records[tag] = Record(kind, generationFromTag(tag) ?: -1L)
                    pendingCancel += tag
                }
            }
        }
    }

    /** m:<generation>:<rest> / c:<generation>:<rest>; null when the middle segment is not a number. */
    private fun generationFromTag(tag: String): Long? =
        tag.substringAfter(':', "").substringBefore(':', "").toLongOrNull()
}
