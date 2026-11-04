package com.example.chat.ui.calls.bundles

import com.example.chat.data.services.CallSessionState
import com.example.chat.ui.calls.CallScreenTemplate
import org.junit.Assert.*
import org.junit.Test

class CallBundlePresentationPolicyTest {
    @Test fun allBundlesSurviveEveryRealStateWithoutChangingSelection() {
        for (bundle in CallBundleId.entries) for (state in CallSessionState.entries) {
            val spec = resolveCallBundlePresentation(bundle.id, state)
            assertEquals(bundle, spec.bundleId)
            assertEquals(state == CallSessionState.incoming, spec.incoming)
            assertEquals(state == CallSessionState.connected, spec.showConnectedDuration)
        }
    }
    @Test fun unknownOrMissingBundleFallsBackWithoutLegacyTemplateInput() {
        for (raw in listOf(null, "", "corrupt", "sheet")) {
            assertEquals(CallBundleId.CHATY_DEFAULT, resolveCallBundlePresentation(raw, CallSessionState.connecting).bundleId)
        }
    }
    @Test fun outgoingStatesNeverOfferAnswerOrPrematureTimer() {
        for (state in listOf(CallSessionState.initiating, CallSessionState.ringing, CallSessionState.connecting, CallSessionState.reconnecting)) {
            val spec = resolveCallBundlePresentation("google_phone_latest", state)
            assertFalse(spec.incoming)
            assertFalse(spec.showConnectedDuration)
            assertEquals(CallScreenTemplate.sheet, spec.template)
        }
    }
    @Test fun terminalStatesNeverExposeIncomingOrRunningDuration() {
        for (state in listOf(CallSessionState.failed, CallSessionState.busy, CallSessionState.missed, CallSessionState.declined, CallSessionState.ended, CallSessionState.idle)) {
            val spec = resolveCallBundlePresentation("apple_ios", state)
            assertFalse(spec.incoming)
            assertFalse(spec.showConnectedDuration)
        }
    }
}
