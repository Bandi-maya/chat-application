package com.example.chat.data.services

/** Wire-shape compatibility with Flutter api@cac25f1. No transport/identity authority. */
internal object CallSignalCodec {
    fun sdp(raw: Any?): String = when (raw) {
        is String -> raw
        is Map<*, *> -> when (val nested = raw["sdp"]) {
            is String -> nested
            is Map<*, *> -> nested["sdp"] as? String ?: ""
            else -> ""
        }
        else -> ""
    }
    fun candidate(signal: Map<String, Any?>): DecodedCallCandidate? {
        val raw = signal["candidate"]
        val nested = raw as? Map<*, *>
        val text = (if (nested != null) nested["candidate"] else raw) as? String
        if (text.isNullOrBlank()) return null
        val mid = (nested?.get("sdpMid") ?: signal["sdpMid"]) as? String
        val line = nested?.get("sdpMLineIndex") ?: signal["sdpMLineIndex"]
        val index = when (line) {
            is Int -> line
            is Long -> line.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            is String -> line.toIntOrNull()
            else -> null
        }?.takeIf { it >= 0 }
        return DecodedCallCandidate(text, mid, index)
    }
    fun isVideo(signal: Map<String, Any?>): Boolean =
        signal["is_video"] == true || signal["is_video"] == "true" || signal["call_type"] == "video"

    fun outgoingCandidate(callId: String, candidate: String, mid: String?, line: Int?): Map<String, Any?> = mapOf(
        "signal_type" to "candidate", "call_id" to callId,
        "candidate" to mapOf("candidate" to candidate, "sdpMid" to mid, "sdpMLineIndex" to line),
        "sdpMid" to mid, "sdpMLineIndex" to line,
    )
}

internal data class DecodedCallCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?)
