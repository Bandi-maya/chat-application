package com.example.chat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chat.ServiceLocator
import com.example.chat.data.services.CallSessionState
import kotlinx.coroutines.launch

/** "Decline" on the incoming-call notification. Works without opening the app: declines the
 *  ringing session if the offer already arrived, otherwise tells the caller directly. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChatyNotifications.ACTION_DECLINE_CALL) return
        val callId = intent.getStringExtra(ChatyNotifications.EXTRA_CALL_ID).orEmpty()
        val callerId = intent.getStringExtra(ChatyNotifications.EXTRA_CALLER_ID).orEmpty()
        ChatyNotifications.cancelIncomingCall(context, callId)
        ChatyNotifications.forgetCall(callId)
        if (callId.isEmpty()) return
        val pending = goAsync()
        ServiceLocator.appScope.launch {
            try {
                val calls = ServiceLocator.callSignaling
                val session = calls.currentSession
                if (session != null && session.callId == callId && session.state == CallSessionState.incoming) {
                    calls.declineCall()
                } else if (callerId.isNotEmpty() && ServiceLocator.apiBackend.isAuthenticated) {
                    // The offer has not reached this device yet: decline straight to the caller.
                    ServiceLocator.apiBackend.sendCallSignal(
                        callerId, mapOf("signal_type" to "decline", "call_id" to callId),
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("CallActionReceiver", "decline failed: $e")
            } finally {
                pending.finish()
            }
        }
    }
}
