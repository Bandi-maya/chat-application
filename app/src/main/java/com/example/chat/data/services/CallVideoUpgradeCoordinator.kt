package com.example.chat.data.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Call/account scoped orchestration. Capability is denied unless transport AND relay/peer support are verified. */
internal class CallVideoUpgradeCoordinator(
    private val localId: String,
    private val peerId: String,
    private val media: CallMediaController,
    private val scope: CoroutineScope,
    private val valid: () -> Boolean,
    private val send: (String, String, String?) -> Unit,
    private val changed: () -> Unit,
    private val videoChanged: (Boolean) -> Unit,
    private val clockMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val timeoutMillis: Long = 30000,
) {
    val state = VideoUpgradeState(localId, peerId)
    private val lock = Mutex()
    private var timer: Job? = null
    private var epoch = 0L
    private var initiator = false
    private var offerSent = false
    private var answerSent = false
    private var cleaning = false
    private val consumed = linkedSetOf<String>()
    private fun rememberConsumed(id: String) { consumed.add(id); if (consumed.size > 64) consumed.remove(consumed.first()) }
    private fun freshFlags(outgoing: Boolean) { initiator = outgoing; offerSent = false; answerSent = false }
    var notice: String? = null; private set
    private fun arm() {
        timer?.cancel()
        val e = epoch
        timer = scope.launch { delay(timeoutMillis); if (e == epoch && valid() && state.phase != VideoUpgradeState.Phase.VIDEO) cancel("Video request timed out", true) }
    }
    private fun check(e: Long, id: String) {
        check(valid() && epoch == e && state.requestId == id && state.phase == VideoUpgradeState.Phase.NEGOTIATING && clockMillis() < state.deadlineMillis) { "Video request changed" }
    }
    fun request(id: String, supported: Boolean): Boolean {
        if (cleaning || id in consumed || !valid() || !media.supportsVideoUpgrade || !state.request(id, clockMillis(), supported)) return false
        epoch++; freshFlags(true); notice = null; send("video_upgrade_request", id, null); arm(); changed(); return true
    }
    fun incoming(id: String): Boolean {
        if (cleaning || id in consumed || !valid() || !media.supportsVideoUpgrade) return false
        val losing = state.requestId
        if (!state.incoming(id, peerId, clockMillis())) return false
        if (losing != null) { rememberConsumed(losing); send("video_upgrade_cancel", losing, null) }
        epoch++; freshFlags(false); notice = null; arm(); changed(); return true
    }
    /** Local consent on receiver. Camera is not opened until the matching offer arrives. */
    fun acceptIncoming(): Boolean {
        val id = state.requestId ?: return false
        if (initiator || !valid() || !state.accept(id, localId, clockMillis())) return false
        send("video_upgrade_accept", id, null); changed(); return true
    }
    suspend fun accepted(id: String) = lock.withLock {
        if (!initiator || !valid() || !state.accept(id, peerId, clockMillis())) return@withLock
        val e = epoch
        changed()
        try {
            check(e, id)
            media.prepareVideoUpgrade(); check(e, id)
            val offer = media.createOffer(true); check(e, id)
            offerSent = true
            send("video_upgrade_offer", id, offer)
        } catch (_: Exception) { abortLocked(e, id, "Unable to start video. Audio remains connected.") }
    }
    suspend fun offer(id: String, sdp: String) = lock.withLock {
        if (initiator || answerSent || !valid() || state.phase != VideoUpgradeState.Phase.NEGOTIATING || state.requestId != id || sdp.isBlank()) return@withLock
        val e = epoch
        try {
            check(e, id)
            media.prepareVideoUpgrade(); check(e, id)
            media.setRemoteDescription(sdp, "offer"); check(e, id)
            val answer = media.createAnswer(true); check(e, id)
            answerSent = true
            send("video_upgrade_answer", id, answer)
        } catch (_: Exception) { abortLocked(e, id, "Unable to accept video. Audio remains connected.") }
    }
    suspend fun answer(id: String, sdp: String) = lock.withLock {
        if (!initiator || !offerSent || !valid() || state.phase != VideoUpgradeState.Phase.NEGOTIATING || state.requestId != id || sdp.isBlank()) return@withLock
        val e = epoch
        try {
            check(e, id)
            media.setRemoteDescription(sdp, "answer"); check(e, id)
            send("video_upgrade_commit", id, null)
            media.commitVideoUpgrade(); check(e, id)
            state.committed(id); timer?.cancel(); videoChanged(true); changed()
        } catch (_: Exception) { abortLocked(e, id, "Video setup failed. Audio remains connected.") }
    }
    suspend fun commit(id: String) = lock.withLock {
        if (initiator || !answerSent || !valid() || state.phase != VideoUpgradeState.Phase.NEGOTIATING || state.requestId != id) return@withLock
        val e = epoch
        try {
            check(e, id)
            media.commitVideoUpgrade(); check(e, id)
            state.committed(id); timer?.cancel(); videoChanged(true); changed()
        } catch (_: Exception) { abortLocked(e, id, "Video setup failed. Audio remains connected.") }
    }
    private suspend fun abortLocked(e: Long, id: String, message: String) {
        runCatching { media.abortVideoUpgrade() }
        if (e != epoch || !valid()) return
        send("video_upgrade_cancel", id, null); rememberConsumed(id); epoch++; state.reset(); timer?.cancel(); notice = message; videoChanged(false); changed()
    }
    /** Invalidate synchronously so an awaited SDP result cannot commit after cancellation. */
    fun cancel(message: String = "Video request cancelled", notifyPeer: Boolean = true, decline: Boolean = false) {
        val id = state.requestId ?: return
        if (notifyPeer && valid()) send(if (decline) "video_upgrade_decline" else "video_upgrade_cancel", id, null)
        rememberConsumed(id); epoch++; timer?.cancel(); state.reset(); notice = message; cleaning = true
        media.setVideoEnabled(false)
        val e = epoch
        scope.launch { lock.withLock { runCatching { media.abortVideoUpgrade() }; if (e == epoch) { cleaning = false; if (valid()) { videoChanged(false); changed() } } } }
        if (valid()) changed()
    }
    fun returnToAudio(notifyPeer: Boolean = true) {
        val id = state.requestId ?: return
        if (notifyPeer && valid()) send("video_return_audio", id, null)
        cancel("Audio call", false)
    }
    fun close() { epoch++; timer?.cancel(); media.setVideoEnabled(false); state.reset() }
}
