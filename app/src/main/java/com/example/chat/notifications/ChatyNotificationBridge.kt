package com.example.chat.notifications

/**
 * Seam used by the framework-created receiver and the foreground call service. Installed by ServiceLocator.
 * Everything the receiver does goes through [dispatch], which revalidates against the live account scope.
 */
interface ChatyNotificationBridge {
    /** nonce: random opaque id from the PendingIntent. replyText only for REPLY. Must never throw. */
    suspend fun dispatch(nonce: String?, action: String, replyText: CharSequence?)
    /** Nonce for an ongoing-call action bound to the CURRENT exact account scope and nonterminal callId, or null. */
    fun registerCallAction(action: String): Pair<String, CallActionView>?
    /** The user-selected notification look, resolved the same way as message/call notifications.
     *  Default keeps fakes/fixtures compiling; the live coordinator overrides with the real resolver. */
    fun currentStyle(): ChatyNotificationStyle = ChatyNotificationStyle()
}

/** What the ongoing-call notification needs to draw its actions. */
data class CallActionView(val callId: String, val muted: Boolean, val onSpeaker: Boolean)

object ChatyNotificationBridgeHolder {
    @Volatile var bridge: ChatyNotificationBridge? = null
}
