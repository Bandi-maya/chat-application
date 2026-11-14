package com.example.chat.ui.automation

import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.data.services.ServerScheduledMessage
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Exact port of lib/features/automation/scheduled_message_service.dart.
 *
 *  Service managing scheduled message queues and background dispatch.
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController.
 */
class ScheduledMessageService(
    private val backend: GbFeatureBackendService,
    private val scope: CoroutineScope,
    private val session: (() -> ScheduledMessagesPort)? = null,
    /** Fail-closed guard consulted before scheduling: throws when the target
     *  conversation must not receive a server-side plaintext send (it is
     *  flagged for end-to-end encryption, or its flag cannot be read). */
    private val sendGuard: suspend (conversationId: String) -> Unit = {},
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _messages: List<ServerScheduledMessage> = emptyList()
    private var _isLoading = false
    private var _lastError: String? = null

    private var cachedPort: ScheduledMessagesPort? = null
    private var refreshEpoch = 0L
    private fun ownsVisibleState(): Boolean = cachedPort?.publish {} ?: (session == null)
    val messages: List<ServerScheduledMessage> get() = if (ownsVisibleState()) _messages else emptyList()
    val isLoading: Boolean get() = ownsVisibleState() && _isLoading
    val lastError: String? get() = if (ownsVisibleState()) _lastError else null

    /** Refreshes the list of scheduled messages from the backend. */
    suspend fun refresh(): List<ServerScheduledMessage> {
        val port = session?.invoke()
        val epoch = ++refreshEpoch
        cachedPort = port
        _messages = emptyList()
        _isLoading = true
        _lastError = null
        notifyListeners()
        try {
            val results = if (port != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { port.list() } else backend.getScheduledMessages()
            if (epoch != refreshEpoch) return messages
            check(port?.publish { _messages = results } ?: run { _messages = results; true }) { "Account changed." }
            return messages
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (epoch == refreshEpoch) port?.publish { _lastError = e.message } ?: run { if (session == null) _lastError = e.message }
            return messages
        } finally {
            if (epoch == refreshEpoch) {
                _isLoading = false
                notifyListeners()
            }
        }
    }

    /** Schedules a message for future delivery at [scheduledAt]. */
    suspend fun scheduleMessage(
        conversationId: String,
        body: String,
        scheduledAt: ZonedDateTime,
        expectedSession: ScheduledMessagesPort? = null,
    ): String {
        val port = expectedSession ?: session?.invoke() // Capture BEFORE suspended encryption/privacy checks.
        // The server dispatches scheduled sends later, without this device -
        // always plaintext. Fail closed for encrypted/unreadable-flag chats.
        require(body.isNotBlank() && body.length <= 10_000) { "Scheduled messages must contain 1 to 10,000 characters." }
        sendGuard(conversationId)
        val id = if (port != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            port.create(conversationId, body, scheduledAt.toInstant())
        } else backend.scheduleMessage(conversationId, body, scheduledAt.toInstant())
        check(port?.publish {} ?: true) { "Account changed. Reopen the original account to check its schedule." }
        scope.launch { refresh() }
        return id
    }

    /** Cancels every pending server-side scheduled message for one
     *  conversation. Used when the conversation moves to the encrypted plane:
     *  those entries would still dispatch from the server as plaintext.
     *  Returns the cancelled count; callers should surface it. Throws (and
     *  cancels nothing) when the list cannot be fetched, so the toggle can
     *  fail closed instead of leaving unknown plaintext sends on the server. */
    suspend fun cancelScheduledMessagesForConversation(conversationId: String): Int {
        val port = session?.invoke()
        val pending = try {
            if (port != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { port.list() } else backend.getScheduledMessages()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Could not list scheduled messages; none were cancelled.", e)
        }
        var cancelled = 0
        for (m in pending.filter { it.conversationId == conversationId }) {
            if (port != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { port.cancel(m.id) }
            else backend.cancelScheduledMessage(m.id)
            cancelled++
        }
        if (cancelled > 0) {
            check(port?.publish { _messages = _messages.filterNot { it.conversationId == conversationId } }
                ?: run { _messages = _messages.filterNot { it.conversationId == conversationId }; true }) { "Account changed." }
            notifyListeners()
        }
        return cancelled
    }

    /** Cancels a pending scheduled message by [id]. */
    suspend fun cancelScheduledMessage(id: String) {
        val port = session?.invoke()
        if (port != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { port.cancel(id) }
        else backend.cancelScheduledMessage(id)
        check(port?.publish { _messages = _messages.filterNot { it.id == id } }
            ?: run { _messages = _messages.filterNot { it.id == id }; true }) { "Account changed." }
        notifyListeners()
        scope.launch { refresh() }
    }
}
