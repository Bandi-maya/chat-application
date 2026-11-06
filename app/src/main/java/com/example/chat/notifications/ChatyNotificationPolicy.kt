package com.example.chat.notifications

/** Pure, JVM-testable rules for the notification layer (no Android types). */
object ChatyNotificationPolicy {
    const val CH_MESSAGES = "chaty_messages_v1"
    const val CH_INCOMING_CALL = "chaty_incoming_call_v1"
    /** Existing ongoing-call channel (IMPORTANCE_LOW, quiet by design). */
    const val CH_ACTIVE_CALL = "chaty_active_call"
    const val ACTION_REPLY = "com.example.chat.notif.REPLY"
    const val ACTION_DISMISS = "com.example.chat.notif.DISMISS"
    const val ACTION_HANGUP = "com.example.chat.notif.CALL_HANGUP"
    const val ACTION_MUTE = "com.example.chat.notif.CALL_MUTE"
    const val ACTION_SPEAKER = "com.example.chat.notif.CALL_SPEAKER"
    const val ACTION_DECLINE = "com.example.chat.notif.CALL_DECLINE"
    const val EXTRA_NONCE = "chaty.notif.nonce"
    const val KEY_REPLY_TEXT = "chaty.notif.reply_text"
    const val MAX_REPLY_LENGTH = 4000
    const val INCOMING_CALL_ID = 7402

    fun messageNotificationId(conversationId: String): Int = 10000 + (conversationId.hashCode() and 0x3FFFFFFF) % 900000

    class MessageGate(
        val globalEnabled: Boolean, val signedIn: Boolean, val fromMe: Boolean, val conversationMuted: Boolean,
        val appForeground: Boolean, val conversationOpen: Boolean, val systemOrDeleted: Boolean,
    )

    fun shouldPostMessage(g: MessageGate): Boolean =
        g.globalEnabled && g.signedIn && !g.fromMe && !g.conversationMuted && !g.systemOrDeleted &&
            !(g.appForeground && g.conversationOpen)

    /** Protected content rule. Vault-locked chats never show sender, text or actions. */
    enum class Detail { FULL, NO_REPLY, GENERIC }
    fun detail(chatLocked: Boolean, appLockEnabled: Boolean): Detail = when {
        chatLocked -> Detail.GENERIC
        appLockEnabled -> Detail.NO_REPLY
        else -> Detail.FULL
    }

    class Plan(val title: String, val body: String, val offerReply: Boolean, val offerDismiss: Boolean)

    /** What a message notification may show. Vault-locked chats reveal nothing and offer no actions. */
    fun plan(detail: Detail, chatTitle: String?, rawText: String, bodyFormatter: (String) -> String): Plan {
        val name = chatTitle?.takeIf { it.isNotBlank() } ?: "Chaty"
        return when (detail) {
            Detail.GENERIC -> Plan("Chaty", "New message", offerReply = false, offerDismiss = false)
            Detail.NO_REPLY -> Plan(name, bodyFormatter(rawText), offerReply = false, offerDismiss = true)
            Detail.FULL -> Plan(name, bodyFormatter(rawText), offerReply = true, offerDismiss = true)
        }
    }

    /** Reply is refused while the app is locked, app lock is on, or the chat is vault-protected. */
    fun mayReply(appLockRequired: Boolean, appLockEnabled: Boolean, chatLocked: Boolean) =
        !appLockRequired && !appLockEnabled && !chatLocked

    /** Mute/Hang up/Speaker/Decline only touch the same call id the action was built for, while nonterminal. */
    fun callActionAllowed(builtCallId: String?, sessionCallId: String?, active: Boolean) =
        !builtCallId.isNullOrEmpty() && builtCallId == sessionCallId && active

    /** Text of a reply. Null when blank/oversize (action does nothing). */
    fun cleanReply(raw: CharSequence?): String? {
        val t = raw?.toString()?.trim().orEmpty()
        return if (t.isEmpty() || t.length > MAX_REPLY_LENGTH) null else t
    }
}

/**
 * Locally registered action table. A PendingIntent carries only a random opaque nonce; the exact scope object
 * (reference identity, never serialized), action, callId and conversationId live here. [clear] runs on every auth
 * transition so nothing built under account A can run after B, or after A re-signs-in (new scope object).
 */
class ChatyActionRegistry<S : Any>(private val maxEntries: Int = 64, private val nonce: () -> String = ::randomNonce) {
    data class Entry<S>(val scope: S, val action: String, val callId: String?, val conversationId: String?)

    private val lock = Any()
    private val byNonce = LinkedHashMap<String, Entry<S>>()
    private val keyToNonce = HashMap<String, String>()

    private fun key(scope: S, action: String, callId: String?, convo: String?) =
        "${System.identityHashCode(scope)}|$action|$callId|$convo"

    /** Same (scope, action, call, conversation) reuses its nonce, so rebuilt notifications do not grow the table. */
    fun register(scope: S, action: String, callId: String? = null, conversationId: String? = null): String = synchronized(lock) {
        val k = key(scope, action, callId, conversationId)
        keyToNonce[k]?.let { n -> byNonce[n]?.takeIf { it.scope === scope }?.let { return n } }
        val n = nonce()
        byNonce[n] = Entry(scope, action, callId, conversationId)
        keyToNonce[k] = n
        while (byNonce.size > maxEntries) {
            val eldest = byNonce.entries.first()
            byNonce.remove(eldest.key)
            keyToNonce.values.remove(eldest.key)
        }
        n
    }

    fun resolve(n: String?): Entry<S>? = synchronized(lock) { n?.let { byNonce[it] } }

    /** Drops entries by nonce. Nulls and unknown nonces are ignored (idempotent, P5); used when a
     *  lane op is rejected or fails admission before posting, so its PendingIntents can never fire. */
    fun dropNonce(vararg nonces: String?) = synchronized(lock) {
        for (n in nonces) {
            if (n == null) continue
            if (byNonce.remove(n) != null) keyToNonce.values.remove(n)
        }
    }

    fun clear() = synchronized(lock) { byNonce.clear(); keyToNonce.clear() }
    fun clearCall(callId: String) = synchronized(lock) {
        val gone = byNonce.filterValues { it.callId == callId }.keys.toList()
        gone.forEach { byNonce.remove(it); keyToNonce.values.remove(it) }
    }
    fun clearCalls() = synchronized(lock) {
        val gone = byNonce.filterValues { it.callId != null }.keys.toList()
        gone.forEach { byNonce.remove(it); keyToNonce.values.remove(it) }
    }
    val size: Int get() = synchronized(lock) { byNonce.size }

    companion object {
        fun randomNonce(): String {
            val b = ByteArray(16); java.security.SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}

/** User-selectable look (within what Android allows): accent colour and small-icon variant. */
data class ChatyNotificationStyle(val accentArgb: Int = 0xFF075E54.toInt(), val iconVariant: Int = ICON_CHAT) {
    companion object {
        const val ICON_CHAT = 0
        const val ICON_BELL = 1
        fun sanitized(accent: Int, icon: Int) =
            ChatyNotificationStyle(accent or 0xFF000000.toInt(), if (icon == ICON_BELL) ICON_BELL else ICON_CHAT)
    }
}
