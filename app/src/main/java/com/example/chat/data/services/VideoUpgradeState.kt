package com.example.chat.data.services

/** Pure consent protocol. No camera or transport side effect. One owner per call/account. */
class VideoUpgradeState(private val localId: String, private val peerId: String) {
    enum class Phase { AUDIO, OUTGOING, INCOMING, NEGOTIATING, VIDEO }
    var phase = Phase.AUDIO; private set
    var requestId: String? = null; private set
    var deadlineMillis: Long = 0; private set
    fun request(id: String, nowMillis: Long, supported: Boolean): Boolean {
        if (!supported || phase != Phase.AUDIO || (id.isBlank() || id.length > 128)) return false
        requestId = id; phase = Phase.OUTGOING; deadlineMillis = nowMillis + 30000; return true
    }
    fun incoming(id: String, senderId: String, nowMillis: Long): Boolean {
        if (senderId != peerId || id.isBlank() || id.length > 128) return false
        if (phase != Phase.AUDIO && deadlineMillis > 0 && nowMillis >= deadlineMillis) return false
        if (senderId != peerId || (id.isBlank() || id.length > 128) || phase == Phase.VIDEO || phase == Phase.NEGOTIATING) return false
        // Deterministic glare: lower account ID owns the request. Losing outgoing
        // request is cancelled before any camera/SDP allocation by its coordinator.
        if (phase == Phase.OUTGOING && localId < peerId) return false
        if (phase == Phase.INCOMING) return false
        requestId = id; phase = Phase.INCOMING; deadlineMillis = nowMillis + 30000; return true
    }
    fun accept(id: String, senderId: String, nowMillis: Long): Boolean {
        if (id != requestId || nowMillis >= deadlineMillis ||
            !((phase == Phase.OUTGOING && senderId == peerId) || (phase == Phase.INCOMING && senderId == localId))) return false
        phase = Phase.NEGOTIATING; return true
    }
    fun committed(id: String): Boolean {
        if (phase != Phase.NEGOTIATING || id != requestId) return false
        phase = Phase.VIDEO; deadlineMillis = 0; return true
    }
    fun cancel(id: String, senderId: String): Boolean {
        if (id != requestId || (senderId != localId && senderId != peerId) || phase == Phase.AUDIO || phase == Phase.VIDEO) return false
        reset(); return true
    }
    fun timeout(nowMillis: Long): Boolean {
        if (deadlineMillis == 0L || nowMillis < deadlineMillis) return false
        reset(); return true
    }
    fun returnToAudio() { reset() }
    fun reset() { phase = Phase.AUDIO; requestId = null; deadlineMillis = 0 }
}
