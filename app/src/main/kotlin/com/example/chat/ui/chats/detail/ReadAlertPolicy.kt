package com.example.chat.ui.chats.detail

import android.content.Context
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.DeliveryState

/**
 * Persistent watched + already-alerted message IDs, scoped per account
 * so an account switch never leaks watches across tenants.
 */
interface ReadAlertStore {
    fun load(conversationId: String): Set<String>
    fun loadFired(conversationId: String): Set<String>

    /**
     * ONE atomic write of both sets. Returns true only when the state is durable.
     * Callers must not deliver alerts for a write that returned false: the next
     * message update retries the same transition, which keeps alerting at-most-once.
     */
    fun saveAll(conversationId: String, watched: Set<String>, fired: Set<String>): Boolean
}

/** SharedPreferences-backed store for ONE account (file name carries the account key). */
class PrefsReadAlertStore(context: Context, accountKey: String) : ReadAlertStore {
    private val prefs = context.getSharedPreferences("read_alerts_" + java.security.MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }, Context.MODE_PRIVATE)

    override fun load(conversationId: String): Set<String> =
        prefs.getStringSet(conversationId, emptySet())?.toSet().orEmpty()
    override fun loadFired(conversationId: String): Set<String> =
        prefs.getStringSet("$conversationId#fired", emptySet())?.toSet().orEmpty()

    /**
     * Both sets in a single editor, committed SYNCHRONOUSLY (commit, not apply):
     * when this returns true the state is on disk before any alert callback runs.
     * A crash after a true return can never re-fire; a crash after a false return
     * simply re-evaluates on the next update.
     */
    override fun saveAll(conversationId: String, watched: Set<String>, fired: Set<String>): Boolean {
        // Cap the durable at-most-once log; older entries fall off, watches for them
        // are long gone by then.
        val capped = if (fired.size > 500) fired.drop(fired.size - 500).toSet() else fired
        val editor = prefs.edit()
        if (watched.isEmpty()) editor.remove(conversationId) else editor.putStringSet(conversationId, watched)
        if (capped.isEmpty()) editor.remove("$conversationId#fired") else editor.putStringSet("$conversationId#fired", capped)
        return editor.commit()
    }

    /** Conversations with at least one persisted watch (for restart restoration). */
    fun watchedConversations(): Map<String, Set<String>> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.endsWith("#fired")) return@mapNotNull null
            val ids = (value as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            if (ids.isEmpty()) null else key to ids
        }.toMap()
}

/**
 * Notify When Message Read (Bandi, 2026-11-14): sender-side watch on own 1:1
 * messages. The watch never changes what is sent or shown to the peer. It fires
 * once when the server's is_read flips the message to read; revoke/delete (for
 * everyone OR for me) before the read cancels it; when the peer does not share
 * read receipts the server never sends is_read, so the watch simply stays
 * silent. 1:1 only until the server exposes per-member read state for groups.
 */
object ReadAlertPolicy {
    fun watch(watched: Set<String>, messageId: String): Set<String> = watched + messageId

    /** A watch is only offerable on the sender's own live, not-yet-read, direct messages. */
    fun canWatch(message: ChatMessage, currentUserId: String, isDirect: Boolean): Boolean =
        isDirect && message.senderId == currentUserId &&
            message.deliveryState !in setOf(DeliveryState.read, DeliveryState.failed) &&
            !message.isDeletedForEveryone && !message.isDeletedForMe && !message.isRevokedBySender

    /**
     * Watches that can still alert. A message ABSENT from the current list (paged
     * out, or an empty initial page) keeps its watch: absence is not terminal.
     * Only an observed read/delete/revoke drops the watch.
     */
    fun live(watched: Set<String>, messages: List<ChatMessage>): Set<String> {
        val byId = messages.associateBy { it.id }
        return watched.filter { id ->
            val m = byId[id] ?: return@filter true
            m.deliveryState != DeliveryState.read &&
                !m.isDeletedForEveryone && !m.isDeletedForMe && !m.isRevokedBySender
        }.toSet()
    }

    /**
     * Watched messages that just became read. A missing previous state means the
     * read happened while the app was not observing (e.g. after a restart), which
     * still fires exactly once. Anything in [firedIds] can never fire again, which
     * makes at-most-once durable across process death. The caller removes fired
     * IDs from the watch set and records them in [firedIds] BEFORE alerting.
     */
    fun fired(
        watched: Set<String>,
        firedIds: Set<String>,
        previousStates: Map<String, DeliveryState>,
        messages: List<ChatMessage>,
    ): List<ChatMessage> {
        val byId = messages.associateBy { it.id }
        return watched.mapNotNull { id ->
            if (id in firedIds) return@mapNotNull null
            val m = byId[id] ?: return@mapNotNull null
            val was = previousStates[id]
            if (m.deliveryState == DeliveryState.read && was != DeliveryState.read &&
                !m.isDeletedForEveryone && !m.isDeletedForMe && !m.isRevokedBySender) m else null
        }
    }
}

/** Observes one conversation's message list and fires each alert at most once, durably. */
class ReadAlertController(
    private val store: ReadAlertStore,
    private val conversationId: String,
    private val onAlert: (ChatMessage) -> Unit,
) {
    private val previousStates = mutableMapOf<String, DeliveryState>()
    var watched: Set<String> = store.load(conversationId)
        private set
    var firedIds: Set<String> = store.loadFired(conversationId)
        private set

    @Synchronized
    fun watch(messageId: String) {
        val next = ReadAlertPolicy.watch(watched, messageId)
        check(store.saveAll(conversationId, next, firedIds)) { "Could not save the read notification." }
        watched = next
    }

    @Synchronized
    fun cancel(messageId: String) {
        val next = watched - messageId
        check(store.saveAll(conversationId, next, firedIds)) { "Could not stop the read notification." }
        watched = next
    }

    /** Feed the current message list after every update. */
    @Synchronized
    fun onMessages(messages: List<ChatMessage>) {
        val fired = ReadAlertPolicy.fired(watched, firedIds, previousStates, messages)
        val nextWatched = ReadAlertPolicy.live(watched, messages) - fired.mapTo(linkedSetOf()) { it.id }
        val nextFired = firedIds + fired.mapTo(linkedSetOf()) { it.id }
        // Keep the previous memory state on write failure so the next update retries it.
        if (!store.saveAll(conversationId, nextWatched, nextFired)) return
        watched = nextWatched
        firedIds = nextFired
        messages.forEach { previousStates[it.id] = it.deliveryState }
        fired.forEach(onAlert)
    }
}

/**
 * Process-level registry so a watch keeps observing even after the chat screen
 * closes: any layer that receives message updates (chat screen today, background
 * sync when wired) routes them through here. The app shell calls [init] once,
 * then [ensureWatching] per active account so persisted watches resume observing
 * without waiting for each chat to be opened. [notifier] is the system-notification
 * hook the shell owns (channel + runtime permission); it carries the account key
 * so the shell drops callbacks from an account that is no longer active.
 */
object ReadAlertCenter {
    private var appContext: Context? = null
    private val controllers = mutableMapOf<String, ReadAlertController>()
    var notifier: ((accountKey: String, conversationId: String, message: ChatMessage) -> Unit)? = null
    /** Latest in-app listener per conversation (the open chat screen, if any). */
    private val screenListeners = mutableMapOf<String, (ChatMessage) -> Unit>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun forConversation(
        accountKey: String,
        conversationId: String,
        screenListener: ((ChatMessage) -> Unit)? = null,
    ): ReadAlertController {
        val key = "$accountKey/$conversationId"
        if (screenListener != null) screenListeners[key] = screenListener
        return controllers.getOrPut(key) { newController(accountKey, conversationId, key) }
    }

    /**
     * Recreate controllers for every persisted watch of this account (e.g. after
     * process death). Idempotent: existing controllers are kept. Background sync
     * then reaches them through [onMessages].
     */
    @Synchronized
    fun ensureWatching(accountKey: String) {
        val context = appContext ?: return
        PrefsReadAlertStore(context, accountKey).watchedConversations().keys.forEach { conversationId ->
            val key = "$accountKey/$conversationId"
            controllers.getOrPut(key) { newController(accountKey, conversationId, key) }
        }
    }

    private fun newController(accountKey: String, conversationId: String, key: String): ReadAlertController {
        val context = checkNotNull(appContext) { "ReadAlertCenter.init() was not called" }
        return ReadAlertController(PrefsReadAlertStore(context, accountKey), conversationId) { message ->
            screenListeners[key]?.invoke(message)
            notifier?.invoke(accountKey, conversationId, message)
        }
    }

    @Synchronized
    fun watchedConversationIds(accountKey: String): Set<String> {
        ensureWatching(accountKey)
        val prefix = "$accountKey/"
        return controllers.filter { (key, controller) -> key.startsWith(prefix) && controller.watched.isNotEmpty() }
            .keys.map { it.removePrefix(prefix) }.toSet()
    }

    /** The closing chat screen unregisters its listener; the controller keeps observing. */
    @Synchronized
    fun clearScreenListener(accountKey: String, conversationId: String) {
        screenListeners.remove("$accountKey/$conversationId")
    }

    /** Route for updates that arrive while the chat screen is closed (sync layer). */
    @Synchronized
    fun onMessages(accountKey: String, conversationId: String, messages: List<ChatMessage>) {
        controllers["$accountKey/$conversationId"]?.onMessages(messages)
    }
}
