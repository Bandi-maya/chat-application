package com.example.chat.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Framework-created FCM entry point.
 *
 *  Two events only:
 *   - onNewToken: forward to the registrar (dual-backend registration,
 *     exactly the ported Dart lifecycle).
 *   - onMessageReceived: our nudges are content-free data messages
 *     ({type: "sync"}); hand them to the push-agnostic wake handler. Any
 *     message carrying a notification block or unexpected data is ignored -
 *     we never render push-provided content.
 */
class ChatyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmRegistrarHolder.registrar?.onTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "sync" -> PushWakeHolder.handler?.onSyncNudge()
            // Content-free ring: only the call id routing handle. The handler
            // pulls the queued offer over the authenticated channel (REST
            // /chat/call/signals fallback); we never render push-provided
            // content such as caller names from the nudge.
            "incoming_call" -> message.data["call_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { PushWakeHolder.handler?.onIncomingCallNudge(it) }
        }
    }
}
