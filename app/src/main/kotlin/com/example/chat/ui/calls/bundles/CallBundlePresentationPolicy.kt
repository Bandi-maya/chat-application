package com.example.chat.ui.calls.bundles

import com.example.chat.data.services.CallSessionState
import com.example.chat.ui.calls.CallScreenTemplate

/** Presentation only: never creates or advances a signalling state. */
data class CallBundlePresentationSpec(
    val bundleId: CallBundleId,
    val template: CallScreenTemplate,
    val incoming: Boolean,
    val showConnectedDuration: Boolean,
)

/** The global bundle is authoritative; legacy independent template values are not inputs.
 * Existing production media/control rendering remains responsible for capabilities.
 */
fun resolveCallBundlePresentation(rawBundleId: String?, state: CallSessionState): CallBundlePresentationSpec {
    val bundle = CallBundleId.fromId(rawBundleId)
    val template = when (bundle) {
        CallBundleId.GOOGLE_PHONE_LATEST, CallBundleId.SAMSUNG_ONE_UI -> CallScreenTemplate.sheet
        CallBundleId.APPLE_IOS, CallBundleId.MONO_CHEVRON_DARK -> CallScreenTemplate.edge
        CallBundleId.VIVO_IQOO, CallBundleId.NEXTCLOUD_TALK -> CallScreenTemplate.card
        CallBundleId.CHATY_DEFAULT, CallBundleId.GOOGLE_PHONE_CLASSIC,
        CallBundleId.AZURE_RING_VERTICAL -> CallScreenTemplate.chatyClassic
    }
    return CallBundlePresentationSpec(bundle, template,
        incoming = state == CallSessionState.incoming,
        showConnectedDuration = state == CallSessionState.connected)
}
