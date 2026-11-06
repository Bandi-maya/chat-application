package com.example.chat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Not exported. Carries only an opaque nonce; all identity checks are in the bridge. Unknown/stale = no-op. */
class ChatyNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bridge = ChatyNotificationBridgeHolder.bridge ?: return
        val action = intent.action ?: return
        val nonce = intent.getStringExtra(ChatyNotificationPolicy.EXTRA_NONCE)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatyNotificationPolicy.KEY_REPLY_TEXT)
        val pending = goAsync()
        scope.launch {
            try { bridge.dispatch(nonce, action, reply) } catch (_: Exception) { } finally { pending.finish() }
        }
    }
    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
