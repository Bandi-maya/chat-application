package com.example.chat.location

import com.example.chat.ui.chats.location.*
import org.junit.Assert.*
import org.junit.Test

class LocationMapBridgePolicyTest {
    private val session = "a".repeat(32)
    private val policy = LocationMapBridgePolicy(session)
    private val base = mapOf<String, Any?>("version" to 1, "type" to "pinSelected", "session" to session, "latitude" to 12.0, "longitude" to 78.0)
    private fun validate(fields: Map<String, Any?> = base, origin: String = LocationMapBridgePolicy.LOCAL_ORIGIN, main: Boolean = true, length: Int = 140) = policy.validate(origin, main, length, fields)
    @Test fun acceptsExactPinEvent() = assertEquals(LocationMapEvent.PinSelected(LocationCoordinate(12.0, 78.0)), validate())
    @Test fun rejectsForeignOrLookalikeOrigins() {
        for (origin in listOf("null", "http://appassets.androidplatform.net", "https://appassets.androidplatform.net.evil.invalid", "https://tiles.openfreemap.org")) assertNull(validate(origin = origin))
    }
    @Test fun rejectsSubframes() = assertNull(validate(main = false))
    @Test fun rejectsWrongSession() = assertNull(validate(base + ("session" to "b".repeat(32))))
    @Test fun rejectsOversizedAndEmptyMessages() { assertNull(validate(length = 513)); assertNull(validate(length = 0)) }
    @Test fun rejectsNonNumericAndInvalidCoordinates() {
        for (value in listOf("12.0", Double.NaN, Double.POSITIVE_INFINITY, 91.0)) assertNull(validate(base + ("latitude" to value)))
    }
    @Test fun rejectsArbitraryCommandsAndExtraFields() {
        assertNull(validate(base + ("type" to "sendMessage")))
        assertNull(validate(base + ("url" to "https://example.invalid")))
    }
    @Test fun readyMustHaveExactShape() {
        val ready = mapOf("version" to 1, "type" to "ready", "session" to session)
        assertEquals(LocationMapEvent.Ready, validate(ready))
        assertNull(validate(ready + ("latitude" to 0)))
    }
    @Test fun generatedSessionsAreValidAndDistinct() {
        val a = LocationMapBridgePolicy().session; val b = LocationMapBridgePolicy().session
        assertTrue(a.matches(Regex("[a-f0-9]{32}"))); assertNotEquals(a, b)
    }
}
