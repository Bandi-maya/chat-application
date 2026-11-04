package com.example.chat.ui.calls

/** 54dp buttons with at least 8dp separation. Hangup always stays visible. */
internal fun visibleCallActionCount(availableWidthDp: Int, actionCount: Int): Int {
    if (availableWidthDp < 116) return 0 // Host stacks More and End vertically.
    val slots = ((availableWidthDp + 8) / 62).coerceAtLeast(2)
    return if (actionCount + 1 <= slots) actionCount else (slots - 2).coerceAtLeast(0)
}

/** 88dp labelled pills separated by 12dp; small windows stack rather than overlap. */
internal fun googleLatestCallControlColumns(availableWidthDp: Int): Int =
    ((availableWidthDp + 12) / 100).coerceIn(1, 3)

/** Product grammar adapted from the existing bundle renderer identities. */
internal data class BundleControlLayout(
    val columns: Int, val circle: Boolean, val panel: Boolean,
    val panelRadiusDp: Int, val controlRadiusDp: Int,
    val spacingDp: Int, val wideEnd: Boolean, val endFirst: Boolean = false,
)

internal fun bundleControlLayout(bundle: com.example.chat.ui.calls.bundles.CallBundleId): BundleControlLayout =
    when (bundle) {
        com.example.chat.ui.calls.bundles.CallBundleId.APPLE_IOS -> BundleControlLayout(3, true, false, 0, 26, 24, false)
        com.example.chat.ui.calls.bundles.CallBundleId.SAMSUNG_ONE_UI -> BundleControlLayout(3, false, true, 28, 18, 24, true)
        com.example.chat.ui.calls.bundles.CallBundleId.GOOGLE_PHONE_CLASSIC -> BundleControlLayout(3, true, false, 0, 26, 32, false)
        com.example.chat.ui.calls.bundles.CallBundleId.VIVO_IQOO -> BundleControlLayout(2, true, true, 24, 26, 20, true)
        com.example.chat.ui.calls.bundles.CallBundleId.AZURE_RING_VERTICAL -> BundleControlLayout(3, true, false, 0, 26, 20, false)
        com.example.chat.ui.calls.bundles.CallBundleId.MONO_CHEVRON_DARK -> BundleControlLayout(2, false, false, 0, 12, 28, false)
        com.example.chat.ui.calls.bundles.CallBundleId.NEXTCLOUD_TALK -> BundleControlLayout(3, false, true, 8, 8, 12, false, true)
        com.example.chat.ui.calls.bundles.CallBundleId.GOOGLE_PHONE_LATEST -> BundleControlLayout(3, false, true, 28, 40, 12, true)
        com.example.chat.ui.calls.bundles.CallBundleId.CHATY_DEFAULT -> BundleControlLayout(3, false, true, 24, 10, 12, false)
    }
