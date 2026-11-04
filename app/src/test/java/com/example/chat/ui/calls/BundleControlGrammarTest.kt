package com.example.chat.ui.calls

import com.example.chat.ui.calls.bundles.CallBundleId
import org.junit.Assert.*
import org.junit.Test

class BundleControlGrammarTest {
    @Test fun eachBundleHasBoundedProductionGrammar() {
        for (bundle in CallBundleId.entries) {
            val spec = bundleControlLayout(bundle)
            assertTrue(spec.columns in 1..3)
            assertTrue(spec.spacingDp >= 12)
            assertTrue(spec.panelRadiusDp >= 0)
        }
    }
    @Test fun familyControlArrangementsAreNotPaletteOnly() {
        assertNotEquals(bundleControlLayout(CallBundleId.APPLE_IOS), bundleControlLayout(CallBundleId.SAMSUNG_ONE_UI))
        assertNotEquals(bundleControlLayout(CallBundleId.GOOGLE_PHONE_CLASSIC), bundleControlLayout(CallBundleId.GOOGLE_PHONE_LATEST))
        assertNotEquals(bundleControlLayout(CallBundleId.VIVO_IQOO), bundleControlLayout(CallBundleId.MONO_CHEVRON_DARK))
        assertTrue(bundleControlLayout(CallBundleId.NEXTCLOUD_TALK).endFirst)
    }
}
