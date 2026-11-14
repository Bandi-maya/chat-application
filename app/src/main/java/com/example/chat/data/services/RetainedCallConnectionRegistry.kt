package com.example.chat.data.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An independently account-bound peer. UI account selection must not redefine this connection's credentials. */
interface RetainedCallConnection {
    val accountHash: String
    val callId: String
    fun credentialsStillValid(): Boolean
    fun isConnected(): Boolean
    /** Must disable microphone, camera and remote audio before true. False may not claim a held state. */
    suspend fun holdMedia(): Boolean
    /** Explicit user action only, restoring the saved mute/camera choices. */
    suspend fun resumeMedia(): Boolean
    suspend fun acceptIncoming(): Boolean
    suspend fun end(reason: String)
}

/** Single foreground media owner, at most one independently retained held peer, never two open microphones. */
class RetainedCallConnectionRegistry {
    private val lock = Mutex()
    private val peers = linkedMapOf<String, RetainedCallConnection>()
    private var foregroundKey: String? = null
    private var heldKey: String? = null
    data class View(val foregroundCallId: String?, val foregroundAccount: String?, val heldCallId: String?, val heldAccount: String?)
    enum class Result { APPLIED, STALE, BUSY, HOLD_UNAVAILABLE, ANSWER_FAILED, RESUME_FAILED }
    private fun key(peer: RetainedCallConnection) = "${peer.accountHash}:${peer.callId}"
    private fun viewUnlocked(): View {
        val foreground = foregroundKey?.let(peers::get); val held = heldKey?.let(peers::get)
        return View(foreground?.callId, foreground?.accountHash, held?.callId, held?.accountHash)
    }
    suspend fun view(): View = lock.withLock { viewUnlocked() }
    /** Adopt only the media owner that was already established by an authenticated call workflow. */
    suspend fun registerForeground(peer: RetainedCallConnection): Result = lock.withLock {
        if (!peer.credentialsStillValid() || !peer.isConnected()) return@withLock Result.STALE
        val id = key(peer)
        if (foregroundKey != null && foregroundKey != id) return@withLock Result.BUSY
        peers[id] = peer; foregroundKey = id
        Result.APPLIED
    }
    /** New peer must be separately authenticated; failure leaves the old peer held and never auto-unmutes it. */
    suspend fun holdAndAnswer(incoming: RetainedCallConnection): Result = lock.withLock {
        val oldKey = foregroundKey ?: return@withLock Result.STALE
        val old = peers[oldKey] ?: return@withLock Result.STALE
        val incomingKey = key(incoming)
        if (incomingKey == oldKey || !old.credentialsStillValid() || !incoming.credentialsStillValid()) return@withLock Result.STALE
        if (heldKey != null || peers.size >= 2) return@withLock Result.BUSY
        if (!old.holdMedia()) return@withLock Result.HOLD_UNAVAILABLE
        // Hold completed before the new media can be acquired.
        heldKey = oldKey; foregroundKey = null
        if (!old.credentialsStillValid() || !incoming.credentialsStillValid()) return@withLock Result.STALE
        peers[incomingKey] = incoming
        val accepted = try { incoming.acceptIncoming() } catch (cancel: kotlinx.coroutines.CancellationException) {
            peers.remove(incomingKey)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { incoming.end("answer_cancelled") }
            throw cancel
        } catch (_: Exception) { false }
        if (!accepted || !incoming.credentialsStillValid()) {
            peers.remove(incomingKey)
            incoming.end("answer_failed")
            return@withLock Result.ANSWER_FAILED
        }
        foregroundKey = incomingKey
        Result.APPLIED
    }
    /** Ending the foreground does not auto-resume a held conversation's microphone/camera. */
    suspend fun endForeground(expectedCallId: String): Result = lock.withLock {
        val id = foregroundKey ?: return@withLock Result.STALE
        val current = peers[id] ?: return@withLock Result.STALE
        if (current.callId != expectedCallId) return@withLock Result.STALE
        current.end("user_end"); peers.remove(id); foregroundKey = null
        Result.APPLIED
    }
    suspend fun resumeHeldByUser(expectedCallId: String): Result = lock.withLock {
        if (foregroundKey != null) return@withLock Result.BUSY
        val id = heldKey ?: return@withLock Result.STALE
        val held = peers[id] ?: return@withLock Result.STALE
        if (held.callId != expectedCallId || !held.credentialsStillValid()) return@withLock Result.STALE
        if (!held.resumeMedia()) return@withLock Result.RESUME_FAILED
        foregroundKey = id; heldKey = null
        Result.APPLIED
    }
    /** Explicit sign-out/revocation closes only that account's peers; selecting another UI account is NOT revocation. */
    suspend fun revokeAccount(accountHash: String) = lock.withLock {
        val removed = peers.filterValues { it.accountHash == accountHash }.toList()
        removed.forEach { (id, peer) ->
            peer.end("account_revoked")
            peers.remove(id)
            if (foregroundKey == id) foregroundKey = null
            if (heldKey == id) heldKey = null
        }
    }
}
