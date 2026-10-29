package com.example.chat.notifications

import android.util.Log
import com.example.chat.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point. backend_python sends data-only, high-priority pushes so this runs even when the
 * app is in the background or was closed (not force-stopped):
 *
 *  - `chat_message`  the user had no live WebSocket: show the message notification, tell the
 *                    server it reached this phone (sender's second grey tick) and resync.
 *  - `incoming_call` ring with a full-screen call notification and reconnect the WebSocket so the
 *                    queued offer arrives (Answer then opens the call screen).
 *  - `call_ended`    the caller hung up / the call was answered elsewhere: stop ringing and
 *                    show "Missed call" if it was still ringing here.
 *
 * Requires app/google-services.json (the google-services plugin is applied when it exists).
 */
class ChatyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        FcmRegistrarHolder.registrar?.onTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"].orEmpty()
        Log.d(TAG, "push received: $type")
        try {
            when (type) {
                "incoming_call" -> onIncomingCall(data)
                "call_ended", "call_cancelled", "missed_call" -> onCallEnded(data)
                "chat_message", "new_message" -> onChatMessage(data, message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "push handling failed: $e")
        }
    }

    private fun onChatMessage(data: Map<String, String>, message: RemoteMessage) {
        val roomId = data["room_id"].orEmpty()
        val backend = ServiceLocator.apiBackend
        if (!backend.isAuthenticated) return
        // Mark delivered and pull the new message in (also wakes the socket).
        backend.onPushMessage(roomId)
        // Visible chat screen already shows it.
        if (ChatyNotifications.appInForeground && backend.activeConversationIdOrNull() == roomId) return
        if (backend.isConversationMuted(roomId)) return
        if (!ServiceLocator.preferencesController.notification.enableGlobalNotifications) return
        ChatyNotifications.showMessage(
            context = applicationContext,
            roomId = roomId,
            messageId = data["message_id"].orEmpty(),
            senderName = data["sender_name"] ?: message.notification?.title ?: "New message",
            text = data["body"] ?: message.notification?.body ?: "New message",
            roomTitle = data["room_name"],
            isGroup = data["is_group"] == "true",
        )
    }

    private fun onIncomingCall(data: Map<String, String>) {
        val callId = data["call_id"].orEmpty()
        if (callId.isEmpty()) return
        val backend = ServiceLocator.apiBackend
        if (!backend.isAuthenticated) return
        // Make sure the call engine listens, then reconnect: the server replays the queued offer
        // over the socket as soon as it connects.
        ServiceLocator.startCallNotifications()
        backend.onPushMessage(null)
        val session = ServiceLocator.callSignaling.currentSession
        if (session != null && session.callId == callId && session.state.isTerminal) return
        if (ChatyNotifications.appInForeground) return // the in-app call screen rings
        ChatyNotifications.showIncomingCall(
            applicationContext,
            callId = callId,
            callerId = data["caller_id"].orEmpty(),
            callerName = data["caller_name"].orEmpty(),
            isVideo = data["is_video"].equals("true", ignoreCase = true),
        )
    }

    private fun onCallEnded(data: Map<String, String>) {
        val callId = data["call_id"].orEmpty()
        if (callId.isEmpty()) return
        val wasRinging = ChatyNotifications.currentlyRingingCallId == callId
        val session = ServiceLocator.callSignaling.currentSession
        val answeredHere = session?.callId == callId && session.state.let { !it.isTerminal && it != com.example.chat.data.services.CallSessionState.incoming }
        if (answeredHere) return
        if (wasRinging) {
            ChatyNotifications.showMissedCall(
                applicationContext, callId, data["caller_name"],
                data["is_video"]?.equals("true", ignoreCase = true),
            )
        } else {
            ChatyNotifications.cancelIncomingCall(applicationContext, callId)
        }
    }

    companion object {
        private const val TAG = "ChatyFcmService"
    }
}

/** Wiring seam between the Firebase callback service and the app-owned
 *  FcmTokenRegistrar (the service is constructed by the framework with no
 *  arguments, so the app root installs the registrar here). */
object FcmRegistrarHolder {
    @Volatile
    var registrar: com.example.chat.data.api.FcmTokenRegistrar? = null
}
