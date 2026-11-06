package com.example.chat.ui.calls

import com.example.chat.ui.calls.bundles.CallBundleId

/** Geometry only. All color roles come from the user's theme, never a vendor palette. */
internal data class CallBundleVisualProfile(
    val columns: Int,
    val panel: Boolean,
    val panelRadiusDp: Int,
    val controlDiameterDp: Int,
    val spacingDp: Int,
    val outlined: Boolean,
    val bannerRadiusDp: Int,
    val bannerAccentEdge: Boolean,
)

internal fun callBundleVisualProfile(bundle: CallBundleId?): CallBundleVisualProfile = when (bundle) {
    CallBundleId.APPLE_IOS, null -> CallBundleVisualProfile(2, false, 32, 60, 22, false, 32, false)
    CallBundleId.SAMSUNG_ONE_UI -> CallBundleVisualProfile(2, true, 32, 64, 24, false, 24, true)
    CallBundleId.GOOGLE_PHONE_LATEST -> CallBundleVisualProfile(2, true, 36, 60, 12, false, 28, false)
    CallBundleId.GOOGLE_PHONE_CLASSIC -> CallBundleVisualProfile(4, false, 0, 56, 16, false, 12, true)
    CallBundleId.VIVO_IQOO -> CallBundleVisualProfile(2, true, 24, 58, 18, false, 20, true)
    CallBundleId.AZURE_RING_VERTICAL -> CallBundleVisualProfile(2, false, 0, 62, 20, true, 30, true)
    CallBundleId.MONO_CHEVRON_DARK -> CallBundleVisualProfile(2, false, 0, 52, 28, true, 8, false)
    CallBundleId.NEXTCLOUD_TALK -> CallBundleVisualProfile(4, true, 16, 52, 10, false, 16, true)
    else -> CallBundleVisualProfile(4, true, 28, 56, 14, false, 28, false)
}
