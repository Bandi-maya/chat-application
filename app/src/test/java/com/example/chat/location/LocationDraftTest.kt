package com.example.chat.location

import com.example.chat.ui.chats.location.*
import org.junit.Assert.*
import org.junit.Test

class LocationDraftTest {
    private fun manual(lat: Double = 12.0) = LocationDraft(LocationCoordinate(lat, 78.0), LocationSource.manualPin)

    @Test fun invalidCoordinatesAreRejected() {
        for (p in listOf(Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY, 90.1 to 0.0, 0.0 to -180.1)) {
            assertTrue(runCatching { LocationCoordinate(p.first, p.second) }.isFailure)
        }
        LocationCoordinate(-90.0, -180.0)
        LocationCoordinate(90.0, 180.0)
    }
    @Test fun manualPinDoesNotClaimGpsAccuracy() {
        assertFalse(manual().messageMetadata().containsKey("accuracy_meters"))
        assertTrue(runCatching { LocationDraft(LocationCoordinate(0.0, 0.0), LocationSource.manualPin, 1.0) }.isFailure)
    }
    @Test fun currentFixRequiresValidAccuracyAndPrecision() {
        assertTrue(runCatching { LocationDraft(LocationCoordinate(0.0, 0.0), LocationSource.currentFix) }.isFailure)
        for (a in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(runCatching { LocationDraft(LocationCoordinate(0.0, 0.0), LocationSource.currentFix, a, LocationPrecision.approximate) }.isFailure)
        }
        val fix = LocationDraft(LocationCoordinate(0.0, 0.0), LocationSource.currentFix, 800.0, LocationPrecision.approximate)
        assertEquals("approximate", fix.messageMetadata()["location_precision"])
    }
    @Test fun pickerCannotSendUntilExplicitConfirmation() {
        val gate = LocationSendGate()
        assertNull(gate.consumeConfirmed())
        gate.select(manual())
        assertEquals(manual(), gate.preview())
        assertNull(gate.consumeConfirmed())
        gate.confirmSend()
        assertEquals(manual(), gate.consumeConfirmed())
        assertNull(gate.consumeConfirmed())
    }
    @Test fun changingPinInvalidatesConfirmation() {
        val gate = LocationSendGate()
        gate.select(manual()); gate.confirmSend(); gate.select(manual(13.0))
        assertNull(gate.consumeConfirmed())
    }
    @Test fun cancelClearsDraftAndConfirmation() {
        val gate = LocationSendGate()
        gate.select(manual()); gate.confirmSend(); gate.cancel()
        assertNull(gate.preview()); assertNull(gate.consumeConfirmed())
        assertTrue(runCatching { gate.confirmSend() }.isFailure)
    }
    private fun access(fine: Boolean = false, coarse: Boolean = false, asked: Boolean = false, rationale: Boolean = false, enabled: Boolean = true) =
        ForegroundLocationAccess(fine, coarse, asked, rationale, enabled)
    @Test fun firstLocateRequestsForegroundOnly() = assertEquals(LocateAction.requestForeground, nextLocateAction(access()))
    @Test fun deniedWithRationaleExplains() = assertEquals(LocateAction.explainRationale, nextLocateAction(access(asked = true, rationale = true)))
    @Test fun previouslyDeniedWithoutRationaleOffersSettings() = assertEquals(LocateAction.offerAppSettings, nextLocateAction(access(asked = true)))
    @Test fun approximateGrantIsAccepted() = assertEquals(LocateAction.useApproximateFix, nextLocateAction(access(coarse = true)))
    @Test fun preciseGrantIsAccepted() = assertEquals(LocateAction.usePreciseFix, nextLocateAction(access(fine = true, coarse = true)))
    @Test fun disabledServicesOfferSettingsWithoutDiscardingGrant() = assertEquals(LocateAction.offerLocationSettings, nextLocateAction(access(coarse = true, enabled = false)))
}
