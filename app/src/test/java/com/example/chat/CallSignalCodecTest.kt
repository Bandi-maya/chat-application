package com.example.chat

import com.example.chat.data.services.CallSignalCodec
import org.junit.Assert.*
import org.junit.Test

class CallSignalCodecTest {
    @Test fun sdpSupportsBothFlutterShapes() {
        assertEquals("raw", CallSignalCodec.sdp("raw"))
        assertEquals("flat", CallSignalCodec.sdp(mapOf("sdp" to "flat")))
        assertEquals("nested", CallSignalCodec.sdp(mapOf("sdp" to mapOf("sdp" to "nested"))))
    }
    @Test fun malformedSdpIsNotMapToString() {
        assertEquals("", CallSignalCodec.sdp(mapOf("x" to 1)))
        assertEquals("", CallSignalCodec.sdp(42))
        assertEquals("", CallSignalCodec.sdp(null))
    }
    @Test fun nestedAndLegacyCandidates() {
        val legacy = CallSignalCodec.candidate(mapOf("candidate" to "ice", "sdpMid" to "0", "sdpMLineIndex" to "2"))!!
        val nested = CallSignalCodec.candidate(mapOf("candidate" to mapOf("candidate" to "ice", "sdpMid" to "0", "sdpMLineIndex" to 2)))!!
        assertEquals(legacy, nested)
    }
    @Test fun missingCandidateRejectedAndIndexNotTruncated() {
        assertNull(CallSignalCodec.candidate(mapOf("candidate" to mapOf("sdpMid" to "0"))))
        assertNull(CallSignalCodec.candidate(mapOf("candidate" to "")))
        assertNull(CallSignalCodec.candidate(mapOf("candidate" to "ice", "sdpMLineIndex" to 1.5))!!.sdpMLineIndex)
    }
    @Test fun outboundShapeAndVideoTypeMatchLatest() {
        val signal = CallSignalCodec.outgoingCandidate("id", "ice", "mid", 0)
        assertEquals("candidate", signal["signal_type"])
        assertEquals("ice", (signal["candidate"] as Map<*, *>)["candidate"])
        assertTrue(CallSignalCodec.isVideo(mapOf("call_type" to "video")))
        assertFalse(CallSignalCodec.isVideo(mapOf("call_type" to "voice")))
    }
}
