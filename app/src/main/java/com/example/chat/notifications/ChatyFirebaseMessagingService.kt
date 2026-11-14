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
        if (message.notification != null) return
        val binding = message.data["account_binding"]?.takeIf { it.isNotBlank() } ?: return
        // An opaque binding is only a hint. The router validates it with the mapped account's token.
        when (message.data["type"]) {
            "sync" -> runBlocking {
                withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) { handler.onAccountSyncNudgeAndWait(binding) }
            }
            "incoming_call" -> message.data["call_id"]?.takeIf { it.isNotBlank() }?.let { callId ->
                runBlocking { withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) { handler.onAccountIncomingCallNudgeAndWait(binding, callId) } }
            }
        }
    }

    private companion object {
        const val PUSH_WAKE_TIMEOUT_MS = 12_000L
    }
}
