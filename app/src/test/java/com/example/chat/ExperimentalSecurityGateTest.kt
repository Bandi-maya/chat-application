package com.example.chat

import org.junit.Assert.assertFalse
import org.junit.Test

/** Source-policy regression. This does not establish MLS or receipt runtime safety. */
class ExperimentalSecurityGateTest {
    @Test fun unverifiedProtocolWorkStaysDisabled() {
        assertFalse(ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING)
        assertFalse(ReleaseFeatures.SCOPED_DELIVERY_RECEIPTS)
        assertFalse(ReleaseFeatures.ENFORCE_TAMPER_GUARD)
    }
}
