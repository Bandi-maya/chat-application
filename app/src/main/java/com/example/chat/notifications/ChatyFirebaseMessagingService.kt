package com.example.chat.notifications

import com.example.chat.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

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
        ServiceLocator.ensurePushReady(applicationContext)
        FcmRegistrarHolder.registrar?.onTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ServiceLocator.ensurePushReady(applicationContext)
        val handler = PushWakeHolder.handler ?: return
        when (message.data["type"]) {
            "sync" -> runBlocking {
                withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) {
                    handler.onSyncNudgeAndWait()
                }
            }
            // Content-free ring: only the call id routing handle. The handler
            // pulls the queued offer over the authenticated channel (REST
            // /chat/call/signals fallback); we never render push-provided
            // content such as caller names from the nudge.
            "incoming_call" -> message.data["call_id"]
                ?.takeIf { it.isNotBlank() }
                ?.let { callId ->
                    runBlocking {
                        withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) {
                            handler.onIncomingCallNudgeAndWait(callId)
                        }
                    }
                }
        }
    }

    private companion object {
        const val PUSH_WAKE_TIMEOUT_MS = 12_000L
    }
}
