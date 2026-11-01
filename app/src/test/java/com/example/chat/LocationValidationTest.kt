package com.example.chat

import com.example.chat.ui.chats.detail.validateLocationFix
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationValidationTest {
    @Test
    fun acceptsOnlyFiniteInRangeLocationFixes() {
        validateLocationFix(0.0, 0.0, 0.0)
        validateLocationFix(90.0, 180.0, 5.0)
        validateLocationFix(-90.0, -180.0, 100.0)
    }

    @Test
    fun rejectsInvalidCoordinatesAndAccuracy() {
        val invalid = listOf(
            Triple(Double.NaN, 0.0, 1.0),
            Triple(0.0, Double.POSITIVE_INFINITY, 1.0),
            Triple(91.0, 0.0, 1.0),
            Triple(0.0, -181.0, 1.0),
            Triple(0.0, 0.0, -1.0),
            Triple(0.0, 0.0, Double.NaN),
        )
        invalid.forEach { (lat, lon, accuracy) ->
            var rejected = false
            try {
                validateLocationFix(lat, lon, accuracy)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("invalid location fix must be rejected: ($lat, $lon, $accuracy)", rejected)
        }
    }
}
