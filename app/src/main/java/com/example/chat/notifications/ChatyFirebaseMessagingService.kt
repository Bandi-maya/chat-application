package com.example.chat.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Android shell for the api branch's FirebaseNotificationService
 *  (lib/core/services/firebase_notification_service.dart): receives FCM
 *  token refreshes and messages. The token is forwarded to the app-level
 *  FcmTokenRegistrar, which mirrors Dart's _registerWithBackends. Message
 *  handling mirrors Dart: foreground messages are only logged (real-time
 *  WebSocket and the UI own display), and a notification tap carries the
 *  room_id / conversation_id data key for the root wiring to consume.
 *
 *  Firebase configuration (google-services.json) is not yet present in
 *  either repo branch, so the google-services plugin is intentionally not
 *  applied; the service registers in the manifest but stays inert until
 *  the config lands and FirebaseApp initializes.
 */
class ChatyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        FcmRegistrarHolder.registrar?.onTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Foreground message received: ${message.notification?.title}")
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
